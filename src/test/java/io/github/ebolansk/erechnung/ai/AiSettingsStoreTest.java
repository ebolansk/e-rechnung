// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiSettingsStoreTest {
    /** Test-Ersatz für DPAPI: macht aus dem Klartext einen erkennbar anderen Wert. */
    static final SecretProtector FAKE = new SecretProtector() {
        @Override
        public boolean available() {
            return true;
        }

        @Override
        public String protect(String plain) {
            return "ENC-" + new StringBuilder(Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8))).reverse();
        }

        @Override
        public String unprotect(String value) throws IOException {
            if (!value.startsWith("ENC-")) {
                throw new IOException("nicht entschlüsselbar");
            }
            return new String(Base64.getDecoder().decode(new StringBuilder(value.substring(4)).reverse().toString()), StandardCharsets.UTF_8);
        }
    };

    @Test
    void savedKeyIsOnlyOnDiskEncryptedAndComesBackInANewStore(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("ki.json");
        new AiSettingsStore(f, FAKE).save(new AiSettings(AiProvider.CLAUDE, "", "m", "sk-ant-geheim", true));
        String json = Files.readString(f);
        assertThat(json).doesNotContain("sk-ant-geheim").contains("apiKeyProtected").doesNotContain("\"apiKey\"");
        var loaded = new AiSettingsStore(f, FAKE).load();
        assertThat(loaded.apiKey()).isEqualTo("sk-ant-geheim");
        assertThat(loaded.saveKey()).isTrue();
    }

    @Test
    void legacyPlaintextKeyIsEncryptedOnFirstLoad(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("ki.json");
        Files.writeString(f, "{\"provider\":\"CLAUDE\",\"url\":\"\",\"model\":\"m\",\"apiKey\":\"alt-klartext\",\"saveKey\":true}");
        var loaded = new AiSettingsStore(f, FAKE).load();
        assertThat(loaded.apiKey()).isEqualTo("alt-klartext");
        assertThat(Files.readString(f)).doesNotContain("alt-klartext").contains("apiKeyProtected");
        assertThat(new AiSettingsStore(f, FAKE).load().apiKey()).isEqualTo("alt-klartext");
    }

    @Test
    void withoutEncryptionTheKeyStaysInMemoryOnly(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("ki.json");
        var store = new AiSettingsStore(f, SecretProtector.UNAVAILABLE);
        assertThat(store.canEncrypt()).isFalse();
        store.save(new AiSettings(AiProvider.CLAUDE, "", "m", "nur-im-speicher", true));
        assertThat(Files.readString(f)).doesNotContain("nur-im-speicher").doesNotContain("apiKeyProtected");
        assertThat(store.load().apiKey()).isEqualTo("nur-im-speicher");
        assertThat(new AiSettingsStore(f, SecretProtector.UNAVAILABLE).load().apiKey()).isEmpty();
    }

    @Test
    void keyFromAnotherAccountCannotBeDecryptedAndIsIgnored(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("ki.json");
        Files.writeString(f, "{\"provider\":\"CLAUDE\",\"url\":\"\",\"model\":\"m\",\"saveKey\":true,\"apiKeyProtected\":\"fremd\"}");
        var loaded = new AiSettingsStore(f, FAKE).load();
        assertThat(loaded.apiKey()).isEmpty();
        assertThat(loaded.provider()).isEqualTo(AiProvider.CLAUDE);
    }

    @Test
    void unreadableEncryptedKeyIsReportedAndTheBlobSurvivesASaveWithoutNewKey(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("ki.json");
        new AiSettingsStore(f, FAKE).save(new AiSettings(AiProvider.CLAUDE, "", "m", "sk-ant-geheim", true));
        String blob = Files.readString(f);

        // Die Entschlüsselung scheitert (zum Beispiel blockiert ein Virenscanner powershell.exe).
        SecretProtector broken = new SecretProtector() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String protect(String plain) throws IOException {
                return FAKE.protect(plain);
            }

            @Override
            public String unprotect(String value) throws IOException {
                throw new IOException("powershell blockiert");
            }
        };
        var store = new AiSettingsStore(f, broken);
        var loaded = store.load();
        assertThat(loaded.apiKey()).isEmpty();
        assertThat(store.loadWarning()).contains("nicht entschlüsselt").contains("powershell blockiert");
        assertThat(loaded.saveKey()).as("das Häkchen bleibt gesetzt").isTrue();

        // Speichern ohne neuen Key (zum Beispiel nur das Modell geändert) darf den verschlüsselten Key nicht löschen.
        store.save(new AiSettings(loaded.provider(), loaded.url(), "anderes-modell", "", true));
        assertThat(Files.readString(f)).contains("anderes-modell");
        assertThat(new AiSettingsStore(f, FAKE).load().apiKey()).isEqualTo("sk-ant-geheim");
        assertThat(blob).contains("apiKeyProtected");

        // Ein neu eingegebener Key ersetzt den alten.
        store.save(new AiSettings(loaded.provider(), loaded.url(), "m", "sk-ant-neu", true));
        assertThat(new AiSettingsStore(f, FAKE).load().apiKey()).isEqualTo("sk-ant-neu");
        assertThat(new AiSettingsStore(f, FAKE).loadWarning()).isEmpty();
    }

    @Test
    void failedMigrationOfALegacyPlaintextKeyIsReported(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("ki.json");
        Files.writeString(f, "{\"provider\":\"CLAUDE\",\"url\":\"\",\"model\":\"m\",\"apiKey\":\"alt-klartext\",\"saveKey\":true}");
        SecretProtector cannotProtect = new SecretProtector() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String protect(String plain) throws IOException {
                throw new IOException("DPAPI nicht erreichbar");
            }

            @Override
            public String unprotect(String value) throws IOException {
                throw new IOException("nicht erreichbar");
            }
        };
        var store = new AiSettingsStore(f, cannotProtect);
        assertThat(store.load().apiKey()).isEqualTo("alt-klartext");
        assertThat(store.loadWarning()).contains("unverschlüsselt");
    }
}
