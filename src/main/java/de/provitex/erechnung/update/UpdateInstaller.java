// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.update;

import com.fasterxml.jackson.databind.JsonNode;
import de.provitex.erechnung.launcher.Swap;
import de.provitex.erechnung.util.AtomicFiles;
import de.provitex.erechnung.util.Hashes;
import de.provitex.erechnung.util.Json;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Prüft das heruntergeladene Paket (SHA-256, Manifest, Inhalt) und legt es unter daten/update/neu/app bereit. */
public final class UpdateInstaller {
    private static final long MAX_UNPACKED = 800L * 1024 * 1024;
    private static final int MAX_ENTRIES = 5000;

    private UpdateInstaller() {
    }

    public static Path updateDir(Path home) {
        return home.resolve("daten").resolve("update");
    }

    /** Bereitet vor; getauscht wird beim nächsten Start (Swap). Bei jedem Fehler bleibt die installierte Version unberührt. */
    public static void stage(Path home, Path zip, String expectedSha256, String expectedVersion) throws IOException {
        String actual = Hashes.sha256Hex(Files.readAllBytes(zip));
        if (!actual.equalsIgnoreCase(expectedSha256)) {
            throw new IOException("Die Prüfsumme des Pakets stimmt nicht mit dem Release überein. Das Paket wird verworfen.");
        }
        Path upd = updateDir(home);
        Path neu = upd.resolve("neu");
        Swap.deleteTree(neu);
        Files.createDirectories(neu);
        try {
            unpack(zip, neu, expectedVersion);
            Files.deleteIfExists(upd.resolve("rollback.flag"));
            AtomicFiles.write(upd.resolve("pending.json"), Json.mapper().writeValueAsBytes(Map.of(
                "version", expectedVersion, "sha256", actual.toLowerCase(), "vorbereitet", Instant.now().toString())));
        } catch (IOException | RuntimeException e) {
            Swap.deleteTree(neu);
            throw e instanceof IOException io ? io : new IOException(e.getMessage(), e);
        }
    }

    static void unpack(Path zip, Path target, String expectedVersion) throws IOException {
        long total = 0;
        int count = 0;
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            ZipEntry manifest = zf.getEntry("manifest.json");
            if (manifest == null) {
                throw new IOException("Das Paket enthält kein manifest.json.");
            }
            try (InputStream in = zf.getInputStream(manifest)) {
                JsonNode m = Json.mapper().readTree(in);
                if (!expectedVersion.equals(m.path("version").asText())) {
                    throw new IOException("Die Version im Paket stimmt nicht mit dem Release " + expectedVersion + " überein.");
                }
            }
            Path root = target.toAbsolutePath().normalize();
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.getName().equals("manifest.json")) {
                    continue;
                }
                if (++count > MAX_ENTRIES) {
                    throw new IOException("Das Paket enthält zu viele Dateien.");
                }
                if (!e.getName().startsWith("app/")) {
                    throw new IOException("Das Paket enthält unerwartete Inhalte: " + e.getName());
                }
                Path out = root.resolve(e.getName()).normalize();
                if (!out.startsWith(root)) {
                    throw new IOException("Das Paket enthält einen unzulässigen Pfad: " + e.getName());
                }
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                try (InputStream in = zf.getInputStream(e); var os = Files.newOutputStream(out)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        total += n;
                        if (total > MAX_UNPACKED) {
                            throw new IOException("Das Paket ist entpackt größer als erlaubt.");
                        }
                        os.write(buf, 0, n);
                    }
                }
            }
        }
        if (!Files.isRegularFile(target.resolve("app/lib/e-rechnung.jar"))) {
            throw new IOException("Das Paket enthält kein Programm (app/lib/e-rechnung.jar).");
        }
    }

    /** Wurde schon ein Update vorbereitet, aber noch nicht eingespielt? */
    public static String pendingVersion(Path home) {
        Path p = updateDir(home).resolve("pending.json");
        if (!Files.exists(p) || !Files.isDirectory(updateDir(home).resolve("neu"))) {
            return null;
        }
        try {
            return Json.mapper().readTree(p.toFile()).path("version").asText(null);
        } catch (IOException e) {
            return null;
        }
    }

    /** Merkt vor, dass beim nächsten Start die vorherige Version wiederhergestellt wird. */
    public static void requestRollback(Path home) throws IOException {
        Files.createDirectories(updateDir(home));
        Files.writeString(updateDir(home).resolve("rollback.flag"), Instant.now().toString());
    }

    public static boolean rollbackPossible(Path home) {
        return Files.isDirectory(home.resolve("app.alt"));
    }
}
