// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.service;

import io.github.ebolansk.erechnung.SampleInvoices.Doc;
import io.github.ebolansk.erechnung.SampleInvoices.Pg;
import io.github.ebolansk.erechnung.model.DocumentType;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Zehn weitere erfundene Rechnungslayouts; jedes weicht in mindestens einem Punkt von den anderen ab. */
final class SampleVariants {
    enum Meta { INLINE, ABOVE, GAP }

    enum DateFmt { DOT, ISO, SHORT, LONG }

    enum Cur { SYMBOL, EUR_SUFFIX, EUR_PREFIX }

    enum Head { RIGHT_BLOCK, LEFT_BIG }

    record It(String name, String detail, String qty, String unit, String price, int vat) {
    }

    /** Alle Eigenschaften eines Layouts samt Soll-Werten (aus den Positionen berechnet). */
    static final class V {
        String label;
        String sellerName;
        String street;
        String zip;
        String city;
        String vatId = "";
        String taxNo = "";
        String iban;
        String bic;
        /** Optional (nur für die Beispielrechnungen): Ansprechpartner, Telefon und E-Mail des Ausstellers im Briefkopf. */
        String contact = "";
        String phone = "";
        String email = "";
        boolean senderLine = true;
        Head head = Head.RIGHT_BLOCK;
        String buyerName;
        String addressee = "";
        String buyerStreet;
        String buyerZip;
        String buyerCity;
        boolean buyerRight;
        DocumentType type = DocumentType.INVOICE;
        String numberLabel = "Rechnungsnummer";
        String number;
        LocalDate issue;
        LocalDate delivery;
        LocalDate periodEnd;
        LocalDate due;
        String dueStyle = "bis";
        String refLabel = "";
        String ref = "";
        Meta meta = Meta.INLINE;
        DateFmt dateFmt = DateFmt.DOT;
        Cur cur = Cur.SYMBOL;
        boolean thousands = true;
        boolean pos = true;
        boolean vatCol = true;
        boolean qtyFirst;
        boolean footerLine;
        String[] totals = {"Nettobetrag", "MwSt. %s %%", "Gesamtbetrag"};
        List<It> items = new ArrayList<>();
        int rowsPerPage = 100;

        BigDecimal lineNet(It i) {
            return new BigDecimal(i.qty).multiply(new BigDecimal(i.price)).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal net() {
            return items.stream().map(this::lineNet).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        Map<Integer, BigDecimal> taxByRate() {
            Map<Integer, BigDecimal> base = new LinkedHashMap<>();
            for (It i : items) {
                base.merge(i.vat, lineNet(i), BigDecimal::add);
            }
            Map<Integer, BigDecimal> tax = new LinkedHashMap<>();
            base.forEach((rate, b) -> tax.put(rate, b.multiply(BigDecimal.valueOf(rate)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)));
            return tax;
        }

        BigDecimal tax() {
            return taxByRate().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal gross() {
            return net().add(tax());
        }
    }

    private static final String[] MONTHS = {"Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August",
        "September", "Oktober", "November", "Dezember"};

    private SampleVariants() {
    }

    static List<V> all() {
        List<V> list = new ArrayList<>();

        V a = base("01-iso-praefix-ohne-spalten", "Kranich Gartenbau GmbH", "Birkenallee 3", "50667", "Köln",
            "DE811000111", "", "DE02120300000000202051", "BYLADEM1001");
        a.number = "KG-2027-310";
        a.issue = LocalDate.of(2027, 3, 1);
        a.delivery = LocalDate.of(2027, 2, 26);
        a.dateFmt = DateFmt.ISO;
        a.cur = Cur.EUR_PREFIX;
        a.pos = false;
        a.vatCol = false;
        a.numberLabel = "Rechnungs-Nr.";
        a.refLabel = "Bestellnummer";
        a.ref = "PO-88213";
        a.due = LocalDate.of(2027, 3, 15);
        a.totals = new String[] {"Summe netto", "zzgl. %s %% USt.", "Zu zahlender Betrag"};
        buyer(a, "Hofgut Rosenthal KG", "", "Rosenweg 14", "50674", "Köln");
        a.items.add(new It("Rollrasen Verlegung", "", "120", "m", "6.50", 19));
        a.items.add(new It("Pflanzarbeiten", "", "8", "Std", "58.00", 19));
        a.items.add(new It("Entsorgung Grünschnitt", "", "1", "psch", "95.00", 19));
        list.add(a);

        V b = base("02-kurzdatum-brutto-ohne-tausender", "Lenz & Partner Steuerberatung PartG mbB", "Marienstraße 22", "80331", "München",
            "DE811000222", "", "DE89370400440532013000", "COBADEFFXXX");
        b.number = "2027/0457";
        b.issue = LocalDate.of(2027, 4, 12);
        b.delivery = LocalDate.of(2027, 4, 12);
        b.dateFmt = DateFmt.SHORT;
        b.thousands = false;
        b.refLabel = "Leitweg-ID";
        b.ref = "09162000-4711-08";
        b.due = LocalDate.of(2027, 4, 26);
        b.dueStyle = "faellig";
        b.totals = new String[] {"Netto", "MwSt. %s %%", "Brutto"};
        buyer(b, "Stadt Beispielburg", "Amt für Finanzen", "Rathausplatz 1", "80335", "München");
        b.items.add(new It("Jahresabschluss 2026", "", "1", "psch", "2400.00", 19));
        b.items.add(new It("Steuererklärung Gesellschafter", "", "3", "Stk", "380.00", 19));
        list.add(b);

        V c = base("03-steuernummer-lueckenloses-gap-meta", "Elektro Sander e.K.", "Hauptstraße 118", "70563", "Stuttgart",
            "", "86/123/45678", "DE75512108001245126199", "SSKMDEMMXXX");
        c.number = "20270033";
        c.issue = LocalDate.of(2027, 5, 5);
        c.delivery = LocalDate.of(2027, 5, 4);
        c.meta = Meta.GAP;
        c.refLabel = "Bestellnummer";
        c.ref = "4711-ES";
        c.dueStyle = "ziel-text";
        c.footerLine = true;
        buyer(c, "Schulz Haustechnik GmbH", "Herrn Paul Schulz", "Gewerbering 9", "70565", "Stuttgart");
        c.items.add(new It("Zählerschrank erneuern", "", "1", "Stk", "1180.00", 19));
        c.items.add(new It("Arbeitszeit Elektromeister", "", "6.5", "Std", "74.00", 19));
        list.add(c);

        V d = base("04-adresse-rechts-langes-datum", "Schwarzwald Feinkost GmbH & Co. KG", "Straße des 17. Juni 5a", "78054", "Villingen-Schwenningen",
            "DE811000444", "", "DE12500105170648489890", "INGDDEFFXXX");
        d.number = "FK-0099";
        d.issue = LocalDate.of(2027, 3, 5);
        d.delivery = LocalDate.of(2027, 3, 4);
        d.dateFmt = DateFmt.LONG;
        d.buyerRight = true;
        d.refLabel = "Ihre Referenz";
        d.ref = "BK-2027-5";
        d.due = LocalDate.of(2027, 3, 19);
        buyer(d, "Gasthaus Zum Adler", "", "Dorfstraße 2", "78048", "Villingen-Schwenningen");
        d.items.add(new It("Schwarzwälder Schinken", "", "12", "kg", "34.90", 7));
        d.items.add(new It("Bergkäse 12 Monate gereift", "", "8", "kg", "21.50", 7));
        d.items.add(new It("Kirschwasser 0,7 l", "", "6", "Stk", "28.00", 19));
        list.add(d);

        V e = base("05-gutschrift", "Nordlicht Werbetechnik GmbH", "Lindenweg 12", "70173", "Stuttgart",
            "DE811234567", "", "DE89370400440532013000", "COBADEFFXXX");
        e.type = DocumentType.CREDIT_NOTE;
        e.numberLabel = "Gutschriftsnummer";
        e.number = "GS-2027-007";
        e.issue = LocalDate.of(2027, 2, 9);
        e.delivery = LocalDate.of(2027, 1, 18);
        e.refLabel = "Bestellnummer";
        e.ref = "B-7781";
        e.dueStyle = "none";
        e.totals = new String[] {"Nettobetrag", "MwSt. %s %%", "Gutschriftsbetrag"};
        buyer(e, "Bäckerei Sonnenschein OHG", "", "Marktplatz 5", "89073", "Ulm");
        e.items.add(new It("Gutschrift Montage vor Ort", "", "2", "Std", "85.00", 19));
        list.add(e);

        V f = base("06-rabattzeile", "Pixelwerk Digital GmbH", "Fabrikstraße 31", "04109", "Leipzig",
            "DE811000666", "", "DE75512108001245126199", "SSKMDEMMXXX");
        f.number = "PW-5521";
        f.issue = LocalDate.of(2027, 6, 2);
        f.delivery = LocalDate.of(2027, 5, 31);
        f.refLabel = "Ihre Referenz";
        f.ref = "WEB-19";
        f.due = LocalDate.of(2027, 6, 16);
        buyer(f, "Optik Brillant GmbH", "", "Grimmaische Straße 4", "04109", "Leipzig");
        f.items.add(new It("Shop-Konfiguration", "", "1", "psch", "1850.00", 19));
        f.items.add(new It("Produktfotos", "", "40", "Stk", "12.50", 19));
        f.items.add(new It("Treuerabatt", "", "1", "Stk", "-150.00", 19));
        list.add(f);

        V g = base("07-viele-positionen-zwei-seiten", "Großhandel Neckartal GmbH", "Industriestraße 80", "72764", "Reutlingen",
            "DE811000777", "", "DE12500105170648489890", "INGDDEFFXXX");
        g.number = "GN-2027-1180";
        g.issue = LocalDate.of(2027, 7, 14);
        g.delivery = LocalDate.of(2027, 7, 13);
        g.refLabel = "Bestellnummer";
        g.ref = "E-554433";
        g.due = LocalDate.of(2027, 8, 13);
        g.rowsPerPage = 14;
        buyer(g, "Bauhaus Müller GmbH", "Einkauf", "Tübinger Straße 50", "72762", "Reutlingen");
        for (int i = 1; i <= 24; i++) {
            g.items.add(new It("Schraubenset Typ " + (100 + i), "", String.valueOf(i % 5 + 1), "Stk", (4 + i) + ".90", i % 6 == 0 ? 7 : 19));
        }
        list.add(g);

        V h = base("08-ohne-leistungsdatum-ohne-referenz", "Mediaschmiede Schwab GmbH", "Uferstraße 6", "88045", "Friedrichshafen",
            "DE811000888", "", "DE02120300000000202051", "BYLADEM1001");
        h.number = "MS-13";
        h.issue = LocalDate.of(2027, 8, 20);
        h.delivery = null;
        h.meta = Meta.ABOVE;
        h.dueStyle = "ziel-text";
        buyer(h, "Hotel Seeblick GmbH", "", "Seestraße 12", "88045", "Friedrichshafen");
        h.items.add(new It("Imagefilm 90 Sekunden", "", "1", "psch", "3200.00", 19));
        list.add(h);

        V i = base("09-nur-sieben-prozent-summenzeile", "Buchhandlung Leseratte e.K.", "Kirchgasse 4", "72070", "Tübingen",
            "DE811000999", "", "DE89370400440532013000", "COBADEFFXXX");
        i.number = "LR-4410";
        i.issue = LocalDate.of(2027, 9, 8);
        i.delivery = LocalDate.of(2027, 9, 8);
        i.vatCol = false;
        i.refLabel = "Kundenreferenz";
        i.ref = "KR-2210";
        i.due = LocalDate.of(2027, 9, 22);
        i.totals = new String[] {"Zwischensumme", "Umsatzsteuer %s %%", "Endsumme"};
        buyer(i, "Gymnasium Alte Stadt", "Sekretariat", "Schulweg 1", "72072", "Tübingen");
        i.items.add(new It("Klassensatz Lektüre", "", "28", "Stk", "9.80", 7));
        i.items.add(new It("Atlas Weltgeschichte", "", "2", "Stk", "49.90", 7));
        list.add(i);

        V j = base("10-menge-vor-bezeichnung-detailzeilen", "Technikhaus Brandt GmbH", "Am Hafen 7", "24103", "Kiel",
            "DE811000101", "", "DE12500105170648489890", "INGDDEFFXXX");
        j.number = "TB-61";
        j.issue = LocalDate.of(2027, 10, 4);
        j.delivery = LocalDate.of(2027, 9, 1);
        j.periodEnd = LocalDate.of(2027, 9, 30);
        j.qtyFirst = true;
        j.pos = false;
        j.cur = Cur.EUR_SUFFIX;
        j.meta = Meta.ABOVE;
        j.refLabel = "Ihre Referenz";
        j.ref = "WV-0815";
        j.due = LocalDate.of(2027, 10, 18);
        buyer(j, "Reederei Nordwind GmbH", "Dezernat Technik", "Kaistraße 3", "24103", "Kiel");
        j.items.add(new It("Wartung Kühlanlage", "Quartalsprüfung nach Plan", "1", "Monat", "640.00", 19));
        j.items.add(new It("Ersatzteil Dichtungssatz", "Artikel 4412-B", "3", "Stk", "27.40", 19));
        j.items.add(new It("Servicetechniker", "inklusive Anfahrt", "4.5", "Std", "92.00", 19));
        list.add(j);
        return list;
    }

    private static V base(String label, String name, String street, String zip, String city, String vat, String tax, String iban, String bic) {
        V v = new V();
        v.label = label;
        v.sellerName = name;
        v.street = street;
        v.zip = zip;
        v.city = city;
        v.vatId = vat;
        v.taxNo = tax;
        v.iban = iban;
        v.bic = bic;
        return v;
    }

    private static void buyer(V v, String name, String addressee, String street, String zip, String city) {
        v.buyerName = name;
        v.addressee = addressee;
        v.buyerStreet = street;
        v.buyerZip = zip;
        v.buyerCity = city;
    }

    // ---- Formatierung -----------------------------------------------------------------------------------------

    static String money(V v, BigDecimal x) {
        DecimalFormat f = new DecimalFormat(v.thousands ? "#,##0.00" : "0.00", DecimalFormatSymbols.getInstance(Locale.GERMANY));
        String n = f.format(x);
        return switch (v.cur) {
            case SYMBOL -> n + " €";
            case EUR_SUFFIX -> n + " EUR";
            case EUR_PREFIX -> "EUR " + n;
        };
    }

    static String date(V v, LocalDate d) {
        return switch (v.dateFmt) {
            case DOT -> String.format("%02d.%02d.%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
            case ISO -> d.toString();
            case SHORT -> String.format("%02d.%02d.%02d", d.getDayOfMonth(), d.getMonthValue(), d.getYear() % 100);
            case LONG -> d.getDayOfMonth() + ". " + MONTHS[d.getMonthValue() - 1] + " " + d.getYear();
        };
    }

    private static String qty(It i) {
        return i.qty.replace('.', ',') + " " + i.unit;
    }

    // ---- Zeichnen ---------------------------------------------------------------------------------------------

    static byte[] render(V v) throws IOException {
        try (Doc doc = new Doc()) {
            Pg p = doc.page();
            float rightX = v.buyerRight ? 60 : 350;
            // Briefkopf
            if (v.head == Head.RIGHT_BLOCK) {
                p.text(350, 800, v.sellerName.length() > 30 ? 10 : 13, v.sellerName);
                p.text(350, 786, 9, v.phone.isEmpty() ? "Tel.: 0711 1234567" : "Tel.: " + v.phone);
                p.text(350, 774, 9, v.email.isEmpty() ? "info@aussteller.example" : v.email);
            }
            if (!v.contact.isEmpty()) {
                p.text(350, v.head == Head.RIGHT_BLOCK ? 762 : 800, 9, "Ansprechpartner: " + v.contact);
            }
            if (v.senderLine) {
                p.text(60, 750, 7, v.sellerName + " · " + v.street + " · " + v.zip + " " + v.city);
            }
            // Anschrift
            float bx = v.buyerRight ? 330 : 60;
            float by = 730;
            p.text(bx, by, 11, v.buyerName);
            by -= 14;
            if (!v.addressee.isEmpty()) {
                p.text(bx, by, 11, v.addressee);
                by -= 14;
            }
            p.text(bx, by, 11, v.buyerStreet);
            p.text(bx, by - 14, 11, v.buyerZip + " " + v.buyerCity);

            // Rechnungsdaten
            String docWord = v.type == DocumentType.CREDIT_NOTE ? "Gutschrift" : "Rechnung";
            float my = v.buyerRight ? 730 : 730;
            float metaX = v.buyerRight ? 60 : 350;
            float y;
            List<String[]> meta = new ArrayList<>();
            meta.add(new String[] {v.numberLabel, v.number});
            meta.add(new String[] {v.type == DocumentType.CREDIT_NOTE ? "Gutschriftsdatum" : "Rechnungsdatum", date(v, v.issue)});
            if (!v.ref.isEmpty()) {
                meta.add(new String[] {v.refLabel, v.ref});
            }
            if (v.delivery != null && v.periodEnd == null) {
                meta.add(new String[] {"Leistungsdatum", date(v, v.delivery)});
            }
            if (v.delivery != null && v.periodEnd != null) {
                meta.add(new String[] {"Leistungszeitraum", date(v, v.delivery) + " – " + date(v, v.periodEnd)});
            }
            if (v.meta == Meta.ABOVE) {
                p.text(60, 640, 18, docWord);
                float x = 60;
                for (String[] m : meta) {
                    p.text(x, 612, 8, m[0]);
                    p.text(x, 598, 10, m[1]);
                    x += 120;
                }
                y = 560;
            } else if (v.buyerRight) {
                float yy = my;
                for (String[] m : meta) {
                    p.text(metaX, yy, 10, v.meta == Meta.GAP ? m[0] : m[0] + ": " + m[1]);
                    if (v.meta == Meta.GAP) {
                        p.text(metaX + 130, yy, 10, m[1]);
                    }
                    yy -= 13;
                }
                p.text(60, 620, 18, docWord);
                y = 585;
            } else {
                float yy = my;
                for (String[] m : meta) {
                    p.text(metaX, yy, 10, v.meta == Meta.GAP ? m[0] : m[0] + ": " + m[1]);
                    if (v.meta == Meta.GAP) {
                        p.text(metaX + 110, yy, 10, m[1]);
                    }
                    yy -= 13;
                }
                p.text(60, 620, 18, docWord);
                y = 585;
            }

            // Tabelle
            Pg page = p;
            int pageNo = 1;
            y = tableHeader(page, v, y);
            int onPage = 0;
            for (It i : v.items) {
                if (onPage == v.rowsPerPage) {
                    page.text(400, 50, 8, "Seite " + pageNo + " von 2");
                    page = doc.page();
                    pageNo++;
                    page.text(60, 800, 9, docWord + " " + v.number + " – Seite " + pageNo + " von 2");
                    y = tableHeader(page, v, 770);
                    onPage = 0;
                }
                row(page, v, i, y, v.items.indexOf(i) + 1);
                y -= i.detail.isEmpty() ? 16 : 28;
                onPage++;
            }
            page.rule(50, y + 8, 545);
            y -= 10;
            page.text(300, y, 10, v.totals[0]);
            page.right(545, y, 10, money(v, v.net()));
            for (Map.Entry<Integer, BigDecimal> t : v.taxByRate().entrySet()) {
                y -= 14;
                page.text(300, y, 10, String.format(v.totals[1], t.getKey()));
                page.right(545, y, 10, money(v, t.getValue()));
            }
            y -= 20;
            page.text(300, y, 11, v.totals[2]);
            page.right(545, y, 11, money(v, v.gross()));
            y -= 40;
            switch (v.dueStyle) {
                case "bis" -> page.text(60, y, 10, "Zahlbar bis " + date(v, v.due) + " ohne Abzug.");
                case "faellig" -> page.text(60, y, 10, "Fällig am: " + date(v, v.due));
                case "ziel-text" -> page.text(60, y, 10, "Zahlungsziel 14 Tage netto.");
                default -> { }
            }

            // Fußzeile
            String vatText = v.vatId.isEmpty() ? "Steuernummer: " + v.taxNo : "USt-IdNr.: " + v.vatId;
            String ibanText = v.iban.replaceAll("(.{4})", "$1 ").trim();
            if (v.footerLine) {
                page.text(50, 60, 7, v.sellerName + " · " + v.street + " · " + v.zip + " " + v.city + " · " + vatText);
                page.text(50, 50, 7, "Bankverbindung IBAN " + ibanText + " · BIC " + v.bic);
            } else {
                page.text(50, 70, 8, v.sellerName);
                page.text(50, 59, 8, v.street);
                page.text(50, 48, 8, v.zip + " " + v.city);
                page.text(230, 70, 8, vatText);
                page.text(230, 59, 8, "Registergericht Beispielstadt");
                page.text(400, 70, 8, "IBAN: " + ibanText);
                page.text(400, 59, 8, "BIC: " + v.bic);
            }
            return doc.bytes();
        }
    }

    private static float tableHeader(Pg p, V v, float y) throws IOException {
        float descX = v.pos ? 80 : 50;
        if (v.qtyFirst) {
            p.text(50, y, 9, "Menge");
            p.text(130, y, 9, "Bezeichnung");
        } else {
            if (v.pos) {
                p.text(50, y, 9, "Pos");
            }
            p.text(descX, y, 9, "Leistung");
            p.text(300, y, 9, "Menge");
        }
        p.right(420, y, 9, "Einzelpreis");
        if (v.vatCol) {
            p.right(470, y, 9, "MwSt");
        }
        p.right(545, y, 9, "Gesamt");
        p.rule(50, y - 5, 545);
        return y - 22;
    }

    private static void row(Pg p, V v, It i, float y, int n) throws IOException {
        float descX = v.pos ? 80 : 50;
        if (v.qtyFirst) {
            p.text(50, y, 10, qty(i));
            p.text(130, y, 10, i.name);
            if (!i.detail.isEmpty()) {
                p.text(130, y - 11, 8, i.detail);
            }
        } else {
            if (v.pos) {
                p.text(50, y, 10, String.valueOf(n));
            }
            p.text(descX, y, 10, i.name);
            p.text(300, y, 10, qty(i));
            if (!i.detail.isEmpty()) {
                p.text(descX, y - 11, 8, i.detail);
            }
        }
        p.right(420, y, 10, money(v, new BigDecimal(i.price)));
        if (v.vatCol) {
            p.right(470, y, 10, i.vat + " %");
        }
        p.right(545, y, 10, money(v, v.lineNet(i)));
    }
}
