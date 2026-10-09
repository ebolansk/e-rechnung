// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung;

import de.provitex.erechnung.audit.AuditTrail;
import de.provitex.erechnung.util.AtomicFiles;
import de.provitex.erechnung.util.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Nutzungs- und Haftungshinweis. Er wird beim ersten Start und nach jeder inhaltlichen Änderung (VERSION erhöhen)
 * bestätigt; die Bestätigung liegt in daten/hinweis.json und im Protokoll.
 */
public final class Disclaimer {
    public static final int VERSION = 1;

    public static final String TEXT = """
        Das E-Rechnung-Tool ist eine Machbarkeitsstudie (Proof of Concept, Version 0.x) und kein fertiges oder abgenommenes Produkt. Es unterstützt beim Erstellen von E-Rechnungen (XRechnung, ZUGFeRD). Setzen Sie es nur ein, wenn Sie jedes Ergebnis selbst prüfen; für den produktiven, steuerlich relevanten Einsatz ist es nicht freigegeben.

        • Die Angaben werden aus Ihrer PDF-Rechnung nur vorgeschlagen. Für Richtigkeit und Vollständigkeit der Rechnungsangaben, die steuerliche Behandlung und die Einhaltung der Aufbewahrungspflichten sind Sie selbst verantwortlich.
        • Das Prüfprotokoll bestätigt nur, dass die erzeugte Datei formal dem Format entspricht. Es sagt nichts darüber aus, ob die Rechnung inhaltlich oder steuerlich richtig ist. Das Tool ersetzt keine Steuer- oder Rechtsberatung.
        • Das Archiv macht Änderungen nachweisbar, verhindert sie aber nicht. Die Datensicherung liegt bei Ihnen.
        • Bei Nutzung der KI-Hilfe wird der Text der Rechnung an den von Ihnen eingestellten Anbieter gesendet, erst nach Ihrer Bestätigung.
        • Die Software steht unter der Apache License 2.0 und wird ohne Gewährleistung und, soweit gesetzlich zulässig, ohne Haftung bereitgestellt.""";

    private Disclaimer() {
    }

    /** Wahr, wenn die aktuelle Fassung des Hinweises noch nicht bestätigt wurde. */
    public static boolean needed(Path file) {
        try {
            if (Files.exists(file)) {
                return Json.mapper().readTree(file.toFile()).path("version").asInt(0) < VERSION;
            }
        } catch (IOException e) {
            // unlesbar: wie nicht bestätigt behandeln
        }
        return true;
    }

    public static void accept(Path file, AuditTrail audit, Clock clock, String user) throws IOException {
        AtomicFiles.write(file, Json.mapper().writeValueAsBytes(Map.of(
            "version", VERSION, "bestaetigtAm", Instant.now(clock).toString(), "benutzer", user)));
        audit.append("hinweis-bestaetigt", Map.of("version", String.valueOf(VERSION)));
    }
}
