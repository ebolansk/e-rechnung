// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.ebolansk.erechnung.util.Hashes;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class UpdateInstallerTest {
    public static Path zip(Path file, String version, Map<String, String> entries) throws IOException {
        try (OutputStream os = Files.newOutputStream(file); ZipOutputStream z = new ZipOutputStream(os)) {
            if (version != null) {
                z.putNextEntry(new ZipEntry("manifest.json"));
                z.write(("{\"version\":\"" + version + "\"}").getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
            for (var e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return file;
    }

    public static Map<String, String> good() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("app/lib/e-rechnung.jar", "JAR");
        m.put("app/lib/dep.jar", "DEP");
        return m;
    }

    private static String sha(Path p) throws IOException {
        return Hashes.sha256Hex(Files.readAllBytes(p));
    }

    private static void stage(Path home, Path zip, String sha, String version) throws IOException {
        UpdateInstaller.stage(home, zip, sha, version, TestSigning.sign(Files.readAllBytes(zip)), TestSigning.PAIR.getPublic());
    }

    @Test
    void stagesValidPackageAndRecordsPending(@TempDir Path home) throws Exception {
        Path z = zip(home.resolve("u.zip"), "0.2.0", good());
        stage(home, z, sha(z), "0.2.0");
        assertThat(home.resolve("daten/update/neu/app/lib/e-rechnung.jar")).hasContent("JAR");
        assertThat(UpdateInstaller.pendingVersion(home)).isEqualTo("0.2.0");
        assertThat(home.resolve("daten/update/neu/version.txt")).hasContent("0.2.0");
    }

    @Test
    void unsignedOrWronglySignedPackageIsRejectedAndNothingStaged(@TempDir Path home) throws Exception {
        Path z = zip(home.resolve("u.zip"), "0.2.0", good());
        var key = TestSigning.PAIR.getPublic();
        assertThatThrownBy(() -> UpdateInstaller.stage(home, z, sha(z), "0.2.0", null, key)).hasMessageContaining("nicht signiert");
        assertThatThrownBy(() -> UpdateInstaller.stage(home, z, sha(z), "0.2.0", "  ", key)).hasMessageContaining("nicht signiert");

        var other = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String foreign = UpdateSignature.sign(Files.readAllBytes(z), other.getPrivate());
        assertThatThrownBy(() -> UpdateInstaller.stage(home, z, sha(z), "0.2.0", foreign, key)).hasMessageContaining("Signatur");
        assertThatThrownBy(() -> UpdateInstaller.stage(home, z, sha(z), "0.2.0", "kein-base64!", key)).hasMessageContaining("Signatur");

        String valid = TestSigning.sign(Files.readAllBytes(z));
        Path changed = zip(home.resolve("v.zip"), "0.2.0", Map.of("app/lib/e-rechnung.jar", "ANDERS"));
        assertThatThrownBy(() -> UpdateInstaller.stage(home, changed, sha(changed), "0.2.0", valid, key)).hasMessageContaining("Signatur");
        assertThat(home.resolve("daten/update/neu")).doesNotExist();
        assertThat(UpdateInstaller.pendingVersion(home)).isNull();
    }

    @Test
    void wrongChecksumIsRejectedAndNothingStaged(@TempDir Path home) throws Exception {
        Path z = zip(home.resolve("u.zip"), "0.2.0", good());
        assertThatThrownBy(() -> stage(home, z, "0".repeat(64), "0.2.0")).hasMessageContaining("Prüfsumme");
        assertThat(home.resolve("daten/update/neu")).doesNotExist();
        assertThat(UpdateInstaller.pendingVersion(home)).isNull();
    }

    @Test
    void zipSlipAndForeignContentAreRejected(@TempDir Path home) throws Exception {
        Map<String, String> slip = good();
        slip.put("app/../../evil.txt", "x");
        Path z1 = zip(home.resolve("a.zip"), "0.2.0", slip);
        assertThatThrownBy(() -> stage(home, z1, sha(z1), "0.2.0")).isInstanceOf(IOException.class);
        assertThat(home.resolve("evil.txt")).doesNotExist();
        assertThat(home.getParent().resolve("evil.txt")).doesNotExist();

        Map<String, String> foreign = good();
        foreign.put("daten/mandanten.json", "[]");
        Path z2 = zip(home.resolve("b.zip"), "0.2.0", foreign);
        assertThatThrownBy(() -> stage(home, z2, sha(z2), "0.2.0")).hasMessageContaining("unerwartete");
        assertThat(home.resolve("daten/update/neu")).doesNotExist();
    }

    @Test
    void manifestAndProgramAreRequired(@TempDir Path home) throws Exception {
        Path noManifest = zip(home.resolve("a.zip"), null, good());
        assertThatThrownBy(() -> stage(home, noManifest, sha(noManifest), "0.2.0")).hasMessageContaining("manifest");
        Path wrongVersion = zip(home.resolve("b.zip"), "0.3.0", good());
        assertThatThrownBy(() -> stage(home, wrongVersion, sha(wrongVersion), "0.2.0")).hasMessageContaining("Version");
        Path noJar = zip(home.resolve("c.zip"), "0.2.0", Map.of("app/lib/dep.jar", "x"));
        assertThatThrownBy(() -> stage(home, noJar, sha(noJar), "0.2.0")).hasMessageContaining("kein Programm");
        assertThat(UpdateInstaller.pendingVersion(home)).isNull();
    }
}
