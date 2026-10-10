// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ai;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.ebolansk.erechnung.extract.ExtractionResult.PrintedTotals;
import io.github.ebolansk.erechnung.model.InvoiceCalculator;
import io.github.ebolansk.erechnung.model.LineItem;
import io.github.ebolansk.erechnung.util.GermanFormats;
import io.github.ebolansk.erechnung.util.Json;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Fragt eine KI nach den Rechnungsdaten und prüft die Antwort auf Plausibilität. Die Werte bleiben ein Vorschlag. */
public final class AiExtractor {
    static final String SYSTEM = """
        Du liest den Text einer deutschen Ausgangsrechnung und gibst die Daten als JSON zurück. \
        Antworte ausschließlich mit einem JSON-Objekt, ohne Erklärung und ohne Markdown. \
        Erfinde nichts: Was im Text nicht steht, bleibt null. Datumsangaben im Format JJJJ-MM-TT, \
        Beträge als Zahl mit Punkt als Dezimaltrenner, Rabatte als negative Menge. \
        Der Käufer ist der Rechnungsempfänger, nicht der Aussteller. Schema: \
        {"number":"","issueDate":"","deliveryDate":"","deliveryEnd":"","dueDate":"","paymentTerms":"",\
        "buyerReference":"","currency":"EUR","iban":"",\
        "buyer":{"name":"","street":"","zip":"","city":"","country":"DE","vatId":"","email":""},\
        "items":[{"name":"","quantity":1,"unit":"C62","unitPrice":0,"vatPercent":19,"vatCategory":"S"}],\
        "totals":{"net":0,"tax":0,"gross":0}}""";

    private static final DateTimeFormatter DE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Pattern VAT_ID = Pattern.compile("^[A-Z]{2}[A-Z0-9]{2,12}$");
    private static final Set<String> CATEGORIES = Set.of("S", "Z", "E", "AE", "K", "G", "O");

    private final AiClient client;

    public AiExtractor(AiClient client) {
        this.client = client;
    }

    public AiSuggestion extract(String invoiceText, PrintedTotals printed) throws IOException {
        String answer = client.complete(SYSTEM, "Rechnungstext:\n\n" + invoiceText);
        return parse(answer, printed);
    }

    /** Wie {@link #extract(String, PrintedTotals)}, lässt sich aber abbrechen. */
    public AiSuggestion extract(String invoiceText, PrintedTotals printed, AiCancel cancel) throws IOException {
        String answer = client.complete(SYSTEM, "Rechnungstext:\n\n" + invoiceText, cancel);
        return parse(answer, printed);
    }

    static final String SELLER_SYSTEM = """
        Du liest den Text einer deutschen Ausgangsrechnung und gibst die Stammdaten des RECHNUNGSAUSSTELLERS als JSON zurück. \
        Der Aussteller ist der Absender (Briefkopf, Absenderzeile, Fußzeile), nicht der Rechnungsempfänger. \
        Antworte ausschließlich mit einem JSON-Objekt, ohne Erklärung und ohne Markdown. \
        Erfinde nichts: Was im Text nicht steht, bleibt leer (null). Schema: \
        {"name":"","street":"","zip":"","city":"","country":"DE","vatId":"","taxNumber":"","email":"","phone":"",\
        "contactName":"","iban":"","bic":""}""";

    /** Liest die Stammdaten des Ausstellers aus dem Rechnungstext; die Werte bleiben ein Vorschlag. */
    public SellerSuggestion extractSeller(String invoiceText, AiCancel cancel) throws IOException {
        String answer = client.complete(SELLER_SYSTEM, "Rechnungstext:\n\n" + invoiceText, cancel);
        return parseSeller(answer);
    }

    static SellerSuggestion parseSeller(String answer) throws IOException {
        JsonNode root;
        try {
            root = Json.mapper().readTree(stripFences(answer));
        } catch (IOException e) {
            throw new IOException("Die Antwort der KI war kein lesbares JSON.", e);
        }
        if (root == null || !root.isObject()) {
            throw new IOException("Die Antwort der KI war kein JSON-Objekt.");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        Map<String, String> warnings = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        put(fields, "name", text(root, "name"));
        put(fields, "street", text(root, "street"));
        String zip = text(root, "zip");
        put(fields, "zip", zip);
        put(fields, "city", text(root, "city"));
        String country = text(root, "country").toUpperCase();
        put(fields, "country", country);
        if (!country.isEmpty() && !country.matches("[A-Z]{2}")) {
            warnings.put("country", "Ländercode ist nicht zweistellig (ISO).");
        }
        if ((country.isEmpty() || country.equals("DE")) && !zip.isEmpty() && !zip.matches("\\d{5}")) {
            warnings.put("zip", "Eine deutsche PLZ hat fünf Ziffern.");
        }
        String vat = text(root, "vatId").replaceAll("\\s+", "").toUpperCase();
        put(fields, "vatId", vat);
        if (!vat.isEmpty() && !VAT_ID.matcher(vat).matches()) {
            warnings.put("vatId", "Format der USt-IdNr. ist unplausibel.");
        }
        put(fields, "taxNumber", text(root, "taxNumber"));
        String mail = text(root, "email");
        put(fields, "email", mail);
        if (!mail.isEmpty() && !mail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            warnings.put("email", "E-Mail-Adresse ist unplausibel.");
        }
        put(fields, "phone", text(root, "phone"));
        put(fields, "contactName", text(root, "contactName"));
        String iban = text(root, "iban").replaceAll("\\s+", "").toUpperCase();
        if (!iban.isEmpty()) {
            if (validIban(iban)) {
                fields.put("iban", iban);
            } else {
                notes.add("Die von der KI gelesene IBAN hat eine falsche Prüfsumme und wird nicht vorgeschlagen.");
            }
        }
        String bic = text(root, "bic").replaceAll("\\s+", "").toUpperCase();
        put(fields, "bic", bic);
        if (!bic.isEmpty() && !bic.matches("^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$")) {
            warnings.put("bic", "Format der BIC ist unplausibel.");
        }
        return new SellerSuggestion(fields, warnings, notes);
    }

    static AiSuggestion parse(String answer, PrintedTotals printed) throws IOException {
        JsonNode root;
        try {
            root = Json.mapper().readTree(stripFences(answer));
        } catch (IOException e) {
            throw new IOException("Die Antwort der KI war kein lesbares JSON.", e);
        }
        if (root == null || !root.isObject()) {
            throw new IOException("Die Antwort der KI war kein JSON-Objekt.");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        Map<String, String> warnings = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();

        put(fields, "number", text(root, "number"));
        date(fields, warnings, "issue", root, "issueDate");
        date(fields, warnings, "delivery", root, "deliveryDate");
        date(fields, warnings, "deliveryEnd", root, "deliveryEnd");
        date(fields, warnings, "due", root, "dueDate");
        put(fields, "terms", text(root, "paymentTerms"));
        put(fields, "buyerRef", text(root, "buyerReference"));
        String cur = text(root, "currency").toUpperCase();
        put(fields, "currency", cur);
        if (!cur.isEmpty() && !cur.matches("[A-Z]{3}")) {
            warnings.put("currency", "Währungscode hat nicht drei Buchstaben.");
        }
        JsonNode b = root.path("buyer");
        put(fields, "bName", text(b, "name"));
        put(fields, "bStreet", text(b, "street"));
        put(fields, "bZip", text(b, "zip"));
        put(fields, "bCity", text(b, "city"));
        String country = text(b, "country").toUpperCase();
        put(fields, "bCountry", country);
        if (!country.isEmpty() && !country.matches("[A-Z]{2}")) {
            warnings.put("bCountry", "Ländercode ist nicht zweistellig (ISO).");
        }
        String vat = text(b, "vatId").replaceAll("\\s+", "").toUpperCase();
        put(fields, "bVat", vat);
        if (!vat.isEmpty() && !VAT_ID.matcher(vat).matches()) {
            warnings.put("bVat", "Format der USt-IdNr. ist unplausibel.");
        }
        String mail = text(b, "email");
        put(fields, "bEmail", mail);
        if (!mail.isEmpty() && !mail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            warnings.put("bEmail", "E-Mail-Adresse ist unplausibel.");
        }
        if (fields.containsKey("issue") && fields.containsKey("due") && warnings.get("issue") == null && warnings.get("due") == null
            && GermanFormats.parseDate(fields.get("due")).isBefore(GermanFormats.parseDate(fields.get("issue")))) {
            warnings.put("due", "Fälligkeit liegt vor dem Rechnungsdatum.");
        }

        List<LineItem> items = items(root.path("items"), warnings);
        BigDecimal net = amount(root.path("totals").path("net"));
        BigDecimal tax = amount(root.path("totals").path("tax"));
        BigDecimal gross = amount(root.path("totals").path("gross"));
        itemSums(items, net, gross, printed, warnings);

        String iban = text(root, "iban").replaceAll("\\s+", "").toUpperCase();
        if (!iban.isEmpty() && !validIban(iban)) {
            notes.add("Die von der KI gelesene IBAN hat eine falsche Prüfsumme und wird nicht verwendet.");
            iban = "";
        }
        return new AiSuggestion(fields, items, net, tax, gross, iban, warnings, notes);
    }

    private static void itemSums(List<LineItem> items, BigDecimal aiNet, BigDecimal aiGross, PrintedTotals printed,
                                 Map<String, String> warnings) {
        if (items.isEmpty()) {
            return;
        }
        var t = InvoiceCalculator.compute(items);
        List<String> w = new ArrayList<>(warnings.containsKey("items") ? List.of(warnings.get("items")) : List.of());
        BigDecimal tol = new BigDecimal("0.02");
        if (aiNet != null && t.netTotal().subtract(aiNet).abs().compareTo(tol) > 0) {
            w.add("Die Summe der Positionen (netto " + GermanFormats.formatAmount(t.netTotal())
                + " €) weicht von der Netto-Summe ab, die die KI selbst gelesen hat.");
        }
        if (printed != null && printed.net() != null && t.netTotal().subtract(printed.net()).abs().compareTo(tol) > 0) {
            w.add("Die Summe der Positionen (netto " + GermanFormats.formatAmount(t.netTotal())
                + " €) weicht von der im PDF gelesenen Netto-Summe ab.");
        } else if (printed != null && printed.gross() != null && t.grossTotal().subtract(printed.gross()).abs().compareTo(tol) > 0) {
            w.add("Die Summe der Positionen (brutto " + GermanFormats.formatAmount(t.grossTotal())
                + " €) weicht von der im PDF gelesenen Brutto-Summe ab.");
        }
        if (!w.isEmpty()) {
            warnings.put("items", String.join(" ", w));
        }
    }

    private static List<LineItem> items(JsonNode arr, Map<String, String> warnings) {
        List<LineItem> out = new ArrayList<>();
        List<String> w = new ArrayList<>();
        int n = 0;
        for (JsonNode i : arr) {
            n++;
            BigDecimal qty = amount(i.path("quantity"));
            BigDecimal price = amount(i.path("unitPrice"));
            BigDecimal vat = amount(i.path("vatPercent"));
            String name = text(i, "name");
            if (qty == null || price == null || vat == null || name.isEmpty()) {
                w.add("Position " + n + " ist unvollständig und wurde ausgelassen.");
                continue;
            }
            if (vat.signum() < 0 || vat.compareTo(BigDecimal.valueOf(100)) > 0) {
                w.add("Position " + n + ": Steuersatz außerhalb von 0 bis 100 %.");
            }
            String cat = text(i, "vatCategory").toUpperCase();
            if (cat.isEmpty()) {
                cat = vat.signum() == 0 ? "Z" : "S";
            } else if (!CATEGORIES.contains(cat)) {
                w.add("Position " + n + ": unbekannte Steuerkategorie " + cat + ".");
                cat = "S";
            }
            String unit = text(i, "unit");
            out.add(new LineItem(name, "", unit.isEmpty() ? "C62" : unit, qty, price, vat, cat, ""));
        }
        if (!w.isEmpty()) {
            warnings.put("items", String.join(" ", w));
        }
        return out;
    }

    private static void date(Map<String, String> fields, Map<String, String> warnings, String key, JsonNode node, String name) {
        String raw = text(node, name);
        if (raw.isEmpty()) {
            return;
        }
        try {
            LocalDate d = raw.matches("\\d{4}-\\d{2}-\\d{2}") ? LocalDate.parse(raw) : GermanFormats.parseDate(raw);
            fields.put(key, DE.format(d));
        } catch (RuntimeException e) {
            fields.put(key, raw);
            warnings.put(key, "Datum nicht lesbar.");
        }
    }

    private static BigDecimal amount(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        try {
            if (n.isNumber()) {
                return n.decimalValue();
            }
            String s = n.asText("").trim();
            if (s.isEmpty()) {
                return null;
            }
            return s.matches("-?\\d+(\\.\\d+)?") ? new BigDecimal(s) : GermanFormats.parseAmount(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String text(JsonNode n, String name) {
        JsonNode v = n.path(name);
        return v.isMissingNode() || v.isNull() ? "" : v.asText("").trim();
    }

    private static void put(Map<String, String> fields, String key, String value) {
        if (!value.isEmpty()) {
            fields.put(key, value);
        }
    }

    static String stripFences(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            t = nl > 0 ? t.substring(nl + 1) : t.substring(3);
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        int a = t.indexOf('{');
        int z = t.lastIndexOf('}');
        return a >= 0 && z > a ? t.substring(a, z + 1) : t;
    }

    /** IBAN-Prüfsumme nach ISO 7064 (Modulo 97). */
    public static boolean validIban(String iban) {
        return GermanFormats.isValidIban(iban);
    }
}
