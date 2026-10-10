// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ai;

import java.io.IOException;

/** Verschlüsselt ein Geheimnis (den API-Key) so, dass es nur im selben Benutzerkonto wieder lesbar ist. */
public interface SecretProtector {
    /** Ist die Verschlüsselung auf diesem System verfügbar? Sonst bleibt der Key nur im Arbeitsspeicher. */
    boolean available();

    String protect(String plain) throws IOException;

    String unprotect(String protectedValue) throws IOException;

    /** Windows: DPAPI (Benutzerkonto). Sonst nicht verfügbar. */
    static SecretProtector platform() {
        return WindowsDpapi.isWindows() ? new WindowsDpapi() : UNAVAILABLE;
    }

    SecretProtector UNAVAILABLE = new SecretProtector() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public String protect(String plain) throws IOException {
            throw new IOException("Die Verschlüsselung ist auf diesem System nicht verfügbar.");
        }

        @Override
        public String unprotect(String protectedValue) throws IOException {
            throw new IOException("Die Verschlüsselung ist auf diesem System nicht verfügbar.");
        }
    };
}
