// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.service;

import de.provitex.erechnung.ai.AiClient;
import de.provitex.erechnung.ai.AiExtractor;
import de.provitex.erechnung.ai.AiSettings;
import de.provitex.erechnung.ai.AiSettingsStore;
import de.provitex.erechnung.ai.AiSuggestion;
import de.provitex.erechnung.ai.HttpAiClient;
import de.provitex.erechnung.audit.AuditTrail;
import de.provitex.erechnung.extract.ExtractionResult.PrintedTotals;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * KI als bestätigungspflichtiger Helfer. Die Bestätigung holt die Oberfläche ein, bevor sie {@link #ask} aufruft;
 * der Aufruf selbst wird im Protokoll festgehalten (Quelle, Modell, Umfang, nicht der Inhalt und nie der Key).
 */
public final class AiAssistService {
    private final AiSettingsStore store;
    private final AuditTrail audit;
    private final Function<AiSettings, AiClient> clients;

    public AiAssistService(AiSettingsStore store, AuditTrail audit) {
        this(store, audit, HttpAiClient::new);
    }

    public AiAssistService(AiSettingsStore store, AuditTrail audit, Function<AiSettings, AiClient> clients) {
        this.store = store;
        this.audit = audit;
        this.clients = clients;
    }

    public AiSettings settings() {
        return store.load();
    }

    public boolean available() {
        return store.load().usable();
    }

    /** Sendet den Text dieser einen Rechnung an die konfigurierte KI. Nur nach ausdrücklicher Bestätigung aufrufen. */
    public AiSuggestion ask(String invoiceText, String fileName, PrintedTotals printed) throws IOException {
        AiSettings s = store.load();
        if (!s.usable()) {
            throw new IOException("Die KI ist nicht konfiguriert (Konfiguration → KI).");
        }
        Map<String, String> d = new LinkedHashMap<>();
        d.put("datei", fileName);
        d.put("anbieter", s.provider().label());
        d.put("quelle", s.endpoint());
        d.put("modell", s.model());
        d.put("zeichen", String.valueOf(invoiceText.length()));
        d.put("bestaetigt", "ja");
        try {
            AiSuggestion r = new AiExtractor(clients.apply(s)).extract(invoiceText, printed);
            d.put("ergebnis", "Vorschlag erhalten");
            audit.append("ki-genutzt", d);
            return r;
        } catch (IOException | RuntimeException e) {
            d.put("ergebnis", "Fehler: " + e.getMessage());
            try {
                audit.append("ki-genutzt", d);
            } catch (IOException ignored) {
                // Der ursprüngliche Fehler ist wichtiger als der Protokollfehler.
            }
            throw e instanceof IOException io ? io : new IOException(e.getMessage(), e);
        }
    }

    /** Hält fest, welche KI-Werte der Nutzer übernommen hat. */
    public void recordApplied(String fileName, List<String> fieldKeys) throws IOException {
        audit.append("ki-uebernommen", Map.of("datei", fileName, "felder", String.join(",", fieldKeys)));
    }
}
