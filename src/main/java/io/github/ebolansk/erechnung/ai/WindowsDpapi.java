// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Windows-DPAPI (Data Protection API) über PowerShell, ohne zusätzliche Bibliothek. Der Schutzbereich ist das Windows-Benutzerkonto
 * (CurrentUser): Der verschlüsselte Wert lässt sich nur unter demselben Konto auf demselben Rechner entschlüsseln. Das Geheimnis
 * geht ausschließlich über die Standardeingabe (nie in der Kommandozeile) und als Base64, damit keine Zeichensatzprobleme entstehen.
 */
final class WindowsDpapi implements SecretProtector {
    private static final String PREFIX = "Add-Type -AssemblyName System.Security; $in=[Console]::In.ReadToEnd().Trim(); "
        + "$bytes=[Convert]::FromBase64String($in); $scope=[Security.Cryptography.DataProtectionScope]::CurrentUser; ";
    private static final String PROTECT = PREFIX
        + "[Console]::Out.Write([Convert]::ToBase64String([Security.Cryptography.ProtectedData]::Protect($bytes,$null,$scope)))";
    private static final String UNPROTECT = PREFIX
        + "[Console]::Out.Write([Convert]::ToBase64String([Security.Cryptography.ProtectedData]::Unprotect($bytes,$null,$scope)))";

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    @Override
    public boolean available() {
        return isWindows();
    }

    @Override
    public String protect(String plain) throws IOException {
        return run(PROTECT, Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public String unprotect(String protectedValue) throws IOException {
        String b64 = run(UNPROTECT, protectedValue);
        return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
    }

    private static String run(String script, String stdin) throws IOException {
        Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script).start();
        try {
            p.getOutputStream().write(stdin.getBytes(StandardCharsets.US_ASCII));
            p.getOutputStream().close();
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("PowerShell hat nicht rechtzeitig geantwortet.");
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).trim();
            if (p.exitValue() != 0 || out.isEmpty()) {
                String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                throw new IOException("Die Windows-Verschlüsselung (DPAPI) ist fehlgeschlagen" + (err.isEmpty() ? "." : ": " + err));
            }
            return out;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new IOException("Die Verschlüsselung wurde unterbrochen.", e);
        }
    }
}
