// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.validate;

import de.provitex.erechnung.Version;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ProtocolHtml {
    public record Input(String fileName, String format, String mandant, String invoiceNumber,
                        ValidationReport report, Instant createdAt) {
    }

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.GERMANY)
        .withZone(ZoneId.of("Europe/Berlin"));

    private ProtocolHtml() {
    }

    public static String render(Input in) {
        ValidationReport r = in.report();
        boolean ok = r.passed();
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<!DOCTYPE html>\n<html lang=\"de\"><head><meta charset=\"UTF-8\">")
            .append("<title>Prüf-Protokoll ").append(esc(in.invoiceNumber())).append("</title>")
            .append("<style>body{font-family:Segoe UI,Arial,sans-serif;margin:2rem;color:#1a1a1a}")
            .append("table{border-collapse:collapse;width:100%;margin:1rem 0}")
            .append("td,th{border:1px solid #bbb;padding:.4rem .6rem;text-align:left;vertical-align:top}")
            .append("th{background:#f0f0f0}.ok{color:#0a6b2a}.bad{color:#b00020}")
            .append(".badge{font-weight:bold;font-size:1.3rem}.hint{background:#fff7e0;border:1px solid #e0c060;padding:.7rem;margin-top:1rem}")
            .append("</style></head><body>");
        sb.append("<h1>Prüf-Protokoll</h1>");
        sb.append("<p class=\"badge ").append(ok ? "ok" : "bad").append("\">")
            .append(ok ? "BESTANDEN" : "NICHT BESTANDEN").append("</p>");
        sb.append("<table>");
        row(sb, "Mandant", in.mandant());
        row(sb, "Rechnungsnummer", in.invoiceNumber());
        row(sb, "Ausgabeformat", in.format());
        row(sb, "Geprüfte Datei", in.fileName());
        row(sb, "Geprüftes Profil", r.profile());
        row(sb, "Prüfzeitpunkt", TS.format(in.createdAt()));
        row(sb, "Regelbasis", Version.RULES);
        row(sb, "Validator-Version", r.validatorVersion());
        row(sb, "Tool-Version", Version.TOOL);
        row(sb, "XML (Schema und Regeln)", r.xmlValid() ? "gültig" : "ungültig");
        row(sb, "PDF/A", r.pdfValid() == null ? "nicht zutreffend" : (r.pdfValid() ? "gültig" : "ungültig"));
        sb.append("</table>");
        sb.append("<h2>Befunde</h2>");
        if (r.findings().isEmpty()) {
            sb.append("<p>Keine Befunde.</p>");
        } else {
            sb.append("<table><tr><th>Schwere</th><th>Regel</th><th>Fundstelle</th><th>Meldung</th></tr>");
            for (Finding f : r.findings()) {
                sb.append("<tr><td>").append(esc(f.severity())).append("</td><td>").append(esc(f.ruleId()))
                    .append("</td><td>").append(esc(f.location())).append("</td><td>").append(esc(f.message()))
                    .append("</td></tr>");
            }
            sb.append("</table>");
        }
        sb.append("<div class=\"hint\">Diese Prüfung bestätigt die formale Konformität des Ausgabeformats ")
            .append("(Schema und Regelwerk). Sie bestätigt <b>nicht</b> die inhaltliche Richtigkeit der Rechnungsangaben. ")
            .append("Die inhaltliche Verantwortung liegt beim Rechnungsaussteller.</div>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private static void row(StringBuilder sb, String label, String value) {
        sb.append("<tr><th>").append(esc(label)).append("</th><td>").append(esc(value)).append("</td></tr>");
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
