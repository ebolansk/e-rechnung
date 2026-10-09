// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.extract;

import de.provitex.erechnung.extract.ExtractionResult.PrintedTotals;
import de.provitex.erechnung.extract.PdfText.Cell;
import de.provitex.erechnung.mandant.Mandant;
import de.provitex.erechnung.model.DocumentType;
import de.provitex.erechnung.model.InvoiceData;
import de.provitex.erechnung.model.LineItem;
import de.provitex.erechnung.model.Party;
import de.provitex.erechnung.util.GermanFormats;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Regelbasierte Vorschläge. Alles ist ein Vorschlag, die Prüfmaske bestätigt. */
public final class RuleBasedExtractor {
    private static final String DATE = "(\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}|\\d{4}-\\d{2}-\\d{2}|\\d{1,2}\\.\\s*\\p{L}+\\s+\\d{4})";
    private static final String AMOUNT_CORE = "-?\\d{1,3}(?:\\.\\d{3})*,\\d{2,4}|-?\\d+,\\d{2,4}";
    private static final Pattern AMOUNT = Pattern.compile("(" + AMOUNT_CORE + ")");
    private static final Pattern NUMBER = Pattern.compile(
        "(?im)(?:Rechnung|Gutschrift)s?\\s*-?\\s*(?:nummer|nr\\.?)\\s*[:.]?\\s*([A-Za-z0-9][A-Za-z0-9\\-/._]*)");
    private static final Pattern ISSUE = Pattern.compile("(?i)(?:Rechnungsdatum|Gutschriftsdatum|Datum)\\s*[:.]?\\s*" + DATE);
    private static final Pattern DELIVERY = Pattern.compile("(?i)(?:Leistungsdatum|Lieferdatum|Leistungszeitpunkt)\\s*[:.]?\\s*" + DATE);
    private static final Pattern PERIOD = Pattern.compile("(?i)Leistungszeitraum\\s*[:.]?\\s*" + DATE + "\\s*(?:-|–|bis)\\s*" + DATE);
    private static final Pattern DUE = Pattern.compile("(?i)(?:Fällig(?:keit(?:sdatum)?)?|Zahlbar bis|Zahlungsziel|netto bis)\\s*(?:am|bis)?\\s*[:.]?\\s*" + DATE
        + "|(?:Zahlbar|überweisen|zahlen|begleichen)[^\\n]*?\\bbis\\s+(?:zum\\s+)?" + DATE);
    private static final Pattern TERMS = Pattern.compile("(?im)^\\s*(?:Zahlungsbedingungen?\\s*[:.]?\\s*)?(Zahlbar[^\\n]+|Zahlungsziel[^\\n]*Tage[^\\n]*)$");
    private static final Pattern BUYER_REF = Pattern.compile(
        "(?i)(?:Leitweg-?\\s?ID|Käuferreferenz|Kundenreferenz|Ihre Referenz|Bestellnummer)\\s*[:.]?\\s*([A-Za-z0-9\\-/]+)");
    private static final Pattern VAT_ID = Pattern.compile("\\b(DE)\\s?(\\d{3})\\s?(\\d{3})\\s?(\\d{3})\\b");
    private static final Pattern TAX_NO = Pattern.compile("(?i)(?:Steuernummer|St\\.?-?\\s?Nr\\.?)\\s*[:.]?\\s*(\\d{2,3}/\\d{3,5}/\\d{3,5}|\\d{2,3}/\\d{3,}/?\\d*)");
    private static final Pattern IBAN = Pattern.compile("\\b([A-Z]{2}\\d{2}(?:\\s?[A-Z0-9]{4}){3,7}(?:\\s?[A-Z0-9]{1,4})?)\\b");
    private static final Pattern BIC = Pattern.compile("(?i)BIC\\s*[:.]?\\s*([A-Z]{6}[A-Z0-9]{2}(?:[A-Z0-9]{3})?)\\b");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    private static final Pattern PHONE = Pattern.compile("(?i)(?:Tel(?:efon)?\\.?|Fon)\\s*[:.]?\\s*(\\+?[\\d /()-]{6,})");
    private static final Pattern ZIP_CITY = Pattern.compile("^(\\d{5})\\s+(\\p{L}[\\p{L} .-]*)$");
    private static final Pattern SENDER_LINE = Pattern.compile("^(.+?)\\s*[·|•]\\s*(.+?)\\s*[·|•]\\s*(\\d{5})\\s+(.+)$");
    private static final Pattern VAT_PERCENT = Pattern.compile(
        "(?i)(?:MwSt|USt|Umsatzsteuer)\\.?\\s*(?:von)?\\s*(\\d{1,2}(?:,\\d{1,2})?)\\s*%|(\\d{1,2}(?:,\\d{1,2})?)\\s*%\\s*(?:MwSt|USt|Umsatzsteuer)");
    private static final String CUR = "(?:€|EUR)?";
    private static final String TAIL = "\\s+" + CUR + "\\s*(?<price>" + AMOUNT_CORE + ")\\s*" + CUR + "\\s+"
        + "(?:(?<vat>\\d{1,2}(?:,\\d{1,2})?)\\s*%\\s+)?" + CUR + "\\s*(?<total>" + AMOUNT_CORE + ")\\s*" + CUR + "\\s*$";
    private static final Pattern ROW = Pattern.compile(
        "^\\s*(?:\\d{1,3}\\s+)?(?<desc>\\S.*?)\\s+(?<qty>\\d+(?:[.,]\\d+)?)\\s*(?<unit>[A-Za-zÄÖÜäöüß.]{1,10})?" + TAIL);
    /** Menge und Einheit stehen vor der Bezeichnung. */
    private static final Pattern ROW_QTY_FIRST = Pattern.compile(
        "^\\s*(?<qty>\\d+(?:[.,]\\d+)?)\\s*(?<unit>[A-Za-zÄÖÜäöüß.]{1,10})\\s+(?<desc>\\S.*?)" + TAIL);
    private static final Map<String, String> UNITS = Map.ofEntries(
        Map.entry("std", "HUR"), Map.entry("std.", "HUR"), Map.entry("h", "HUR"), Map.entry("stunden", "HUR"),
        Map.entry("stk", "C62"), Map.entry("stk.", "C62"), Map.entry("stück", "C62"), Map.entry("psch", "C62"),
        Map.entry("pauschal", "C62"), Map.entry("tag", "DAY"), Map.entry("tage", "DAY"), Map.entry("kg", "KGM"),
        Map.entry("m", "MTR"), Map.entry("monat", "MON"), Map.entry("monate", "MON"));

    private RuleBasedExtractor() {
    }

    public static ExtractionResult extract(String rawText, Mandant mandant) {
        return extract(rawText, List.of(), mandant);
    }

    public static ExtractionResult extract(String rawText, List<Cell> pageCells, Mandant mandant) {
        List<Cell> cells = pageCells.isEmpty() ? cellsFromText(rawText) : pageCells;
        String text = withLabelValuePairs(rawText, cells);
        List<String> notes = new ArrayList<>();
        String number = first(NUMBER, text);
        if (number == null) {
            notes.add("Rechnungsnummer nicht gefunden.");
        }
        LocalDate issue = date(first(ISSUE, text));
        if (issue == null) {
            notes.add("Rechnungsdatum nicht gefunden.");
        }
        LocalDate delivery = date(first(DELIVERY, text));
        LocalDate deliveryEnd = null;
        Matcher per = PERIOD.matcher(text);
        if (per.find()) {
            delivery = date(per.group(1));
            deliveryEnd = date(per.group(2));
        }
        if (delivery == null) {
            notes.add("Leistungsdatum nicht gefunden.");
        }
        LocalDate due = date(first(DUE, text));
        String terms = first(TERMS, text);
        String ref = first(BUYER_REF, text);
        if (ref == null) {
            notes.add("Käuferreferenz (BT-10) nicht gefunden.");
        }
        DocumentType type = isCreditNote(text) ? DocumentType.CREDIT_NOTE : DocumentType.INVOICE;
        Party seller = mandant != null ? mandant.toParty() : Party.empty();
        Party buyer = buyer(text, cells, seller, notes);
        BigDecimal defaultVat = defaultVat(text);
        List<LineItem> items = items(text, defaultVat);
        if (items.isEmpty()) {
            notes.add("Positionen nicht erkannt.");
        }
        InvoiceData draft = new InvoiceData(type, number == null ? "" : number, issue, delivery, deliveryEnd, due,
            "EUR", ref == null ? "" : ref, terms == null ? "" : terms.trim(), seller, buyer,
            mandant != null ? GermanFormats.normalizeIban(mandant.iban()) : "", mandant != null ? nz(mandant.bic()) : "", items);
        return new ExtractionResult(draft, printedTotals(text), notes);
    }

    public static MandantDraft draftMandant(String text) {
        return draftMandant(text, List.of());
    }

    public static MandantDraft draftMandant(String text, List<Cell> pageCells) {
        List<Cell> cells = pageCells.isEmpty() ? cellsFromText(text) : pageCells;
        String vat = null;
        Matcher v = VAT_ID.matcher(text);
        if (v.find()) {
            vat = v.group(1) + v.group(2) + v.group(3) + v.group(4);
        }
        String iban = null;
        Matcher i = IBAN.matcher(text);
        if (i.find()) {
            iban = GermanFormats.normalizeIban(i.group(1));
        }
        String name = "";
        String street = "";
        String zip = "";
        String city = "";
        for (String line : text.split("\\R")) {
            Matcher s = SENDER_LINE.matcher(line.trim());
            if (s.matches()) {
                name = s.group(1).trim();
                street = s.group(2).trim();
                zip = s.group(3);
                city = s.group(4).trim();
                break;
            }
        }
        if (name.isEmpty()) {
            // Kein Absenderzeilen-Muster: Briefkopf-Block, dessen Name auch an anderer Stelle (Fußzeile) steht.
            for (int k = 0; k < cells.size() && name.isEmpty(); k++) {
                Matcher m = ZIP_CITY.matcher(cells.get(k).text().trim());
                if (!m.matches()) {
                    continue;
                }
                List<Cell> above = blockAbove(cells, k, 2);
                if (above.size() == 2 && above.get(1).text().matches(".*\\d+\\s?[A-Za-z]?\\s*$")
                    && occurrences(text, above.get(0).text().trim()) >= 2) {
                    name = above.get(0).text().trim();
                    street = above.get(1).text().trim();
                    zip = m.group(1);
                    city = m.group(2).trim();
                }
            }
        }
        String phone = first(PHONE, text);
        return new MandantDraft(name, street, zip, city, vat == null ? "" : vat, nz(first(TAX_NO, text)),
            iban == null ? "" : iban, nz(first(BIC, text)), nz(firstEmail(text)), phone == null ? "" : phone.trim());
    }

    private static boolean isCreditNote(String text) {
        for (String line : text.split("\\R")) {
            String l = line.trim().toLowerCase(Locale.GERMAN);
            if (l.equals("gutschrift") || l.equals("storno") || l.startsWith("gutschrift ")) {
                return true;
            }
        }
        return false;
    }

    private static Party buyer(String text, List<Cell> cells, Party seller, List<String> notes) {
        for (int i = 0; i < cells.size(); i++) {
            Matcher m = ZIP_CITY.matcher(cells.get(i).text().trim());
            if (!m.matches()) {
                continue;
            }
            List<Cell> above = blockAbove(cells, i, 3);
            if (above.size() < 2) {
                continue;
            }
            String street = above.get(above.size() - 1).text().trim();
            if (street.contains("·") || isSellerAddress(seller, street, m.group(1), m.group(2).trim())) {
                continue;
            }
            String name = above.get(above.size() - 2).text().trim();
            if (above.size() >= 3 && name.matches(
                "(?i)(z\\.?\\s?Hd\\.?.*|Abteilung.*|Fachbereich.*|Dezernat.*|Referat.*|Sachgebiet.*|Amt für.*|Sekretariat|Poststelle|Rechnungseingang|Verwaltung|Einkauf|Buchhaltung|Herr.*|Frau.*)")) {
                name = above.get(above.size() - 3).text().trim();
            }
            String email = null;
            for (String l : text.split("\\R")) {
                Matcher em = EMAIL.matcher(l);
                while (em.find()) {
                    if (!em.group().equalsIgnoreCase(seller.email())) {
                        email = em.group();
                        break;
                    }
                }
                if (email != null) {
                    break;
                }
            }
            return new Party(name, street, m.group(1), m.group(2).trim(), "DE", "", "", email == null ? "" : email, "", "");
        }
        notes.add("Käuferanschrift nicht erkannt.");
        return Party.empty();
    }

    private static boolean isSellerAddress(Party seller, String street, String zip, String city) {
        return !seller.street().isBlank() && seller.street().equalsIgnoreCase(street)
            && seller.zip().equals(zip) && seller.city().equalsIgnoreCase(city);
    }

    /** Bis zu {@code max} Zellen direkt über der Zelle {@code index} in derselben Spalte (links- oder rechtsbündig), obere zuerst. */
    private static List<Cell> blockAbove(List<Cell> cells, int index, int max) {
        Cell zip = cells.get(index);
        List<Cell> found = new ArrayList<>();
        float lastY = zip.y();
        for (int j = index - 1; j >= 0 && found.size() < max; j--) {
            Cell c = cells.get(j);
            if (c.page() != zip.page() || lastY - c.y() > 30f) {
                break;
            }
            boolean sameColumn = Math.abs(c.x() - zip.x()) <= 6f || Math.abs(c.xEnd() - zip.xEnd()) <= 6f;
            if (sameColumn && c.y() < lastY - 1f) {
                found.add(0, c);
                lastY = c.y();
            }
        }
        return found;
    }

    private static int occurrences(String text, String needle) {
        if (needle.isBlank()) {
            return 0;
        }
        int n = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            n++;
        }
        return n;
    }

    /** Ersatz-Zellen, wenn keine Positionen vorliegen: jede Zeile eine linksbündige Zelle. */
    private static List<Cell> cellsFromText(String text) {
        List<Cell> cells = new ArrayList<>();
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                cells.add(new Cell(1, 0f, Float.NaN, i * 12f, lines[i].trim()));
            }
        }
        return cells;
    }

    private static final Pattern LABEL = Pattern.compile("(?i)(Rechnungs-?\\s?(?:nummer|nr\\.?)|Rechnungsdatum|Datum|Leistungsdatum|"
        + "Lieferdatum|Leistungszeitraum|Fälligkeit(?:sdatum)?|Zahlungsziel|Leitweg-?\\s?ID|Ihre Referenz|Käuferreferenz|"
        + "Kundenreferenz|Bestellnummer)\\s*:?");

    /**
     * Rechnungsdaten, die als Beschriftungszeile über einer Wertezeile stehen, werden als „Beschriftung: Wert“ vor den Text
     * gesetzt; die Beschriftungszeile entfällt, damit die Muster nicht die Nachbarbeschriftung als Wert lesen.
     */
    private static String withLabelValuePairs(String text, List<Cell> cells) {
        StringBuilder pairs = new StringBuilder();
        String result = text;
        for (Cell label : cells) {
            Matcher lm = LABEL.matcher(label.text().trim());
            if (!lm.matches()) {
                continue;
            }
            Cell value = null;
            for (Cell c : cells) {
                float dy = c.y() - label.y();
                boolean below = c.page() == label.page() && dy > 3f && dy <= 30f;
                boolean sameColumn = Math.abs(c.x() - label.x()) <= 12f || Math.abs(c.xEnd() - label.xEnd()) <= 12f;
                if (below && sameColumn && !LABEL.matcher(c.text().trim()).matches() && (value == null || c.y() < value.y())) {
                    value = c;
                }
            }
            if (value == null) {
                continue;
            }
            pairs.append(label.text().trim().replaceAll(":$", "")).append(": ").append(value.text().trim()).append('\n');
            StringBuilder row = new StringBuilder();
            for (Cell c : cells) {
                if (c.page() == label.page() && Math.abs(c.y() - label.y()) < 2.5f) {
                    row.append(row.isEmpty() ? "" : " ").append(c.text().trim());
                }
            }
            String rowText = row.toString();
            StringBuilder kept = new StringBuilder();
            boolean removed = false;
            for (String line : result.split("\\R", -1)) {
                if (!removed && line.trim().replaceAll("\\s+", " ").equals(rowText)) {
                    removed = true;
                    continue;
                }
                kept.append(line).append('\n');
            }
            result = kept.toString();
        }
        return pairs + result;
    }

    private static BigDecimal defaultVat(String text) {
        Matcher m = VAT_PERCENT.matcher(text);
        if (m.find()) {
            String g = m.group(1) != null ? m.group(1) : m.group(2);
            return new BigDecimal(g.replace(',', '.'));
        }
        return new BigDecimal("19");
    }

    private static List<LineItem> items(String text, BigDecimal defaultVat) {
        List<LineItem> items = new ArrayList<>();
        for (String line : text.split("\\R")) {
            Matcher m = ROW.matcher(line);
            if (!m.matches()) {
                m = ROW_QTY_FIRST.matcher(line);
                if (!m.matches() || !UNITS.containsKey(m.group("unit").toLowerCase(Locale.GERMAN))) {
                    continue;
                }
            }
            String desc = m.group("desc").trim();
            if (desc.matches("(?i).*(Zwischensumme|Gesamt|Summe|MwSt|USt|Umsatzsteuer|Nettobetrag).*")) {
                continue;
            }
            BigDecimal vat = m.group("vat") != null ? new BigDecimal(m.group("vat").replace(',', '.')) : defaultVat;
            String unitRaw = m.group("unit") == null ? "" : m.group("unit").toLowerCase(Locale.GERMAN);
            if (unitRaw.equals("eur")) {
                unitRaw = "";
            }
            BigDecimal qty = GermanFormats.parseAmount(m.group("qty"));
            BigDecimal price = GermanFormats.parseAmount(m.group("price"));
            if (price.signum() < 0) {
                // EN 16931 (BR-27) erlaubt keinen negativen Nettopreis; Rabattzeilen laufen über eine negative Menge.
                price = price.negate();
                qty = qty.negate();
            }
            items.add(new LineItem(desc, "", UNITS.getOrDefault(unitRaw, "C62"), qty, price,
                vat, vat.signum() == 0 ? "Z" : "S", ""));
        }
        return items;
    }

    private static final Pattern TAX_WORD = Pattern.compile("(?i)\\b(?:mwst|ust|umsatzsteuer)\\b");

    private static PrintedTotals printedTotals(String text) {
        BigDecimal net = null;
        BigDecimal gross = null;
        List<BigDecimal> taxLines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String l = line.toLowerCase(Locale.GERMAN);
            BigDecimal last = lastAmount(line);
            if (last == null) {
                continue;
            }
            if (l.matches(".*(gesamtbetrag|gesamtsumme|rechnungsbetrag|gutschriftsbetrag|gutschriftbetrag|bruttobetrag|endbetrag|endsumme|summe brutto|zu zahlen|brutto).*")) {
                gross = last;
            } else if (l.matches(".*(zwischensumme|nettobetrag|summe netto|gesamt netto|netto).*") && net == null) {
                net = last;
            } else if (TAX_WORD.matcher(line).find()) {
                taxLines.add(last);
            }
        }
        return new PrintedTotals(net, taxTotal(taxLines), gross);
    }

    /** Mehrere Steuerzeilen (je Satz eine) werden addiert; steht zusätzlich eine Gesamtzeile da, zählt nur sie. */
    private static BigDecimal taxTotal(List<BigDecimal> lines) {
        if (lines.isEmpty()) {
            return null;
        }
        BigDecimal sum = lines.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (lines.size() == 1) {
            return sum;
        }
        for (BigDecimal candidate : lines) {
            if (sum.subtract(candidate).compareTo(candidate) == 0) {
                return candidate;
            }
        }
        return sum;
    }

    private static BigDecimal lastAmount(String line) {
        Matcher m = AMOUNT.matcher(line);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        return last == null ? null : GermanFormats.parseAmount(last);
    }

    private static String first(Pattern p, String text) {
        Matcher m = p.matcher(text);
        if (!m.find()) {
            return null;
        }
        for (int g = 1; g <= m.groupCount(); g++) {
            if (m.group(g) != null) {
                return m.group(g);
            }
        }
        return null;
    }

    private static String firstEmail(String text) {
        Matcher m = EMAIL.matcher(text);
        return m.find() ? m.group() : null;
    }

    private static LocalDate date(String s) {
        if (s == null) {
            return null;
        }
        try {
            return GermanFormats.parseDate(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
