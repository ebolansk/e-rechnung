// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.ebolansk.erechnung.util.AtomicFiles;
import io.github.ebolansk.erechnung.util.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Liest und schreibt daten/ki.json. Ein gespeicherter Key liegt nur verschlüsselt auf der Platte (Windows-DPAPI, Benutzerkonto);
 * wo das nicht geht oder ohne Häkchen bleibt er nur im Arbeitsspeicher. Ältere Dateien mit Klartext-Key werden beim Laden
 * automatisch verschlüsselt, sofern die Verschlüsselung verfügbar ist.
 */
public final class AiSettingsStore {
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Disk(AiProvider provider, String url, String model, boolean saveKey, String apiKeyProtected, String apiKey) {
    }

    private final Path file;
    private final SecretProtector protector;
    private String sessionKey = "";
    private String cachedBlob = "";
    private String cachedKey = "";
    private String loadWarning = "";
    /** Der gespeicherte Blob, den die Entschlüsselung nicht lesen konnte; er wird beim Speichern nicht gelöscht. */
    private String unreadableBlob = "";

    public AiSettingsStore(Path file) {
        this(file, SecretProtector.platform());
    }

    public AiSettingsStore(Path file, SecretProtector protector) {
        this.file = file;
        this.protector = protector;
    }

    /** Kann der Key auf diesem System verschlüsselt gespeichert werden? */
    public boolean canEncrypt() {
        return protector.available();
    }

    /** Hinweis zum letzten load(): Key nicht entschlüsselbar oder noch unverschlüsselt; leer, wenn alles in Ordnung ist. */
    public synchronized String loadWarning() {
        return loadWarning;
    }

    public synchronized AiSettings load() {
        loadWarning = "";
        unreadableBlob = "";
        Disk d = readDisk();
        if (d == null) {
            return withSession(AiSettings.off());
        }
        String key = "";
        boolean migrate = false;
        if (d.apiKeyProtected() != null && !d.apiKeyProtected().isBlank() && protector.available()) {
            if (d.apiKeyProtected().equals(cachedBlob)) {
                key = cachedKey;
            } else {
                try {
                    key = protector.unprotect(d.apiKeyProtected());
                    cachedBlob = d.apiKeyProtected();
                    cachedKey = key;
                } catch (IOException | RuntimeException e) {
                    key = "";
                    unreadableBlob = d.apiKeyProtected();
                    loadWarning = "Der gespeicherte API-Key konnte nicht entschlüsselt werden (" + e.getMessage()
                        + "). Bitte den Key neu eingeben; bis dahin bleibt der alte Wert unverändert gespeichert.";
                }
            }
        } else if (d.apiKey() != null && !d.apiKey().isBlank()) {
            key = d.apiKey();
            migrate = protector.available() && d.saveKey();
        }
        AiSettings s = new AiSettings(d.provider(), d.url(), d.model(), key, d.saveKey() && (!key.isBlank() || !unreadableBlob.isEmpty()));
        if (migrate) {
            try {
                save(s);
            } catch (IOException e) {
                // bleibt vorerst unverschlüsselt liegen; der nächste Start versucht es erneut
                loadWarning = "Der API-Key konnte nicht verschlüsselt werden und liegt noch unverschlüsselt in daten/ki.json ("
                    + e.getMessage() + ").";
            }
        }
        return withSession(s);
    }

    public synchronized void save(AiSettings s) throws IOException {
        sessionKey = s.apiKey();
        String blob = null;
        boolean persist = s.saveKey() && !s.apiKey().isBlank() && protector.available();
        if (persist) {
            blob = protector.protect(s.apiKey());
            cachedBlob = blob;
            cachedKey = s.apiKey();
        }
        if (!persist && s.apiKey().isBlank() && s.saveKey() && !unreadableBlob.isEmpty()) {
            // Kein neuer Key eingegeben: den nicht lesbaren, aber vielleicht nur vorübergehend blockierten Blob nicht verwerfen.
            blob = unreadableBlob;
            persist = true;
        }
        Disk d = new Disk(s.provider(), s.url(), s.model(), persist, blob, null);
        AtomicFiles.write(file, Json.mapper().writeValueAsBytes(d));
    }

    private AiSettings withSession(AiSettings s) {
        if (s.apiKey().isBlank() && !sessionKey.isBlank()) {
            return new AiSettings(s.provider(), s.url(), s.model(), sessionKey, s.saveKey());
        }
        return s;
    }

    private Disk readDisk() {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return Json.mapper().readValue(file.toFile(), Disk.class);
        } catch (IOException e) {
            return null;
        }
    }
}
