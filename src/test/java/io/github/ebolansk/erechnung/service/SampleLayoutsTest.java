// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ebolansk.erechnung.SampleInvoices;
import io.github.ebolansk.erechnung.config.AppConfig;
import io.github.ebolansk.erechnung.config.ConfigStore;
import io.github.ebolansk.erechnung.audit.AuditLog;
import io.github.ebolansk.erechnung.extract.MandantDraft;
import io.github.ebolansk.erechnung.extract.RuleBasedExtractor;
import io.github.ebolansk.erechnung.mandant.Mandant;
import io.github.ebolansk.erechnung.mandant.MandantStore;
import io.github.ebolansk.erechnung.model.DocumentType;
import io.github.ebolansk.erechnung.model.InvoiceData;
import io.github.ebolansk.erechnung.model.LineItem;
import io.github.ebolansk.erechnung.model.OutputFormat;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ganzer Weg mit drei unterschiedlich aufgebauten Beispielrechnungen: Mandant anlegen, auslesen, erzeugen, prüfen, archivieren. */
class SampleLayoutsTest {
    @TempDir
    Path tmp;

    record Item(String name, String qty, String price, String vat) {
    }

    record Expect(String sellerName, String street, String zip, String city, String vatId, String iban, String bic,
                  String number, LocalDate issue, LocalDate delivery, LocalDate deliveryEnd, String ref, LocalDate due,
                  String buyerName, String buyerStreet, String buyerZip, String buyerCity,
                  List<Item> items, String net, String tax, String gross) {
    }

    static final Expect CLASSIC = new Expect("Nordlicht Werbetechnik GmbH", "Lindenweg 12", "70173", "Stuttgart",
        "DE811234567", "DE89370400440532013000", "COBADEFFXXX", "2027-00123", LocalDate.of(2027, 1, 20),
        LocalDate.of(2027, 1, 18), null, "B-7781", LocalDate.of(2027, 2, 3),
        "Bäckerei Sonnenschein OHG", "Marktplatz 5", "89073", "Ulm",
        List.of(new Item("Leuchtreklame Aluminium 120x40", "2", "389.90", "19"), new Item("Montage vor Ort", "3.5", "85.00", "19"),
            new Item("Anfahrtspauschale", "1", "45.00", "19")), "1122.30", "213.24", "1335.54");

    static final Expect MODERN = new Expect("Studio Feldweg UG (haftungsbeschränkt)", "Gartenstraße 7", "72070", "Tübingen",
        "DE299876543", "DE75512108001245126199", "SSKMDEMMXXX", "R-2027-0815", LocalDate.of(2027, 1, 15),
        LocalDate.of(2027, 1, 12), null, "PRJ-2027-14", LocalDate.of(2027, 2, 14),
        "Rothaus Immobilien GmbH", "Hirschgasse 3", "72074", "Tübingen",
        List.of(new Item("Webdesign Relaunch (Pauschale)", "1", "4200.00", "19"), new Item("Hosting 12 Monate", "12", "29.90", "19"),
            new Item("Wartung", "6", "49.00", "19")), "4852.80", "922.03", "5774.83");

    static final Expect SERVICE = new Expect("Hartmann Ingenieurleistungen e.K.", "Werkstraße 4a", "73728", "Esslingen am Neckar",
        "DE312345678", "DE12500105170648489890", "INGDDEFFXXX", "2027-0042", LocalDate.of(2027, 2, 3),
        LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31), "08116000-1234-21", LocalDate.of(2027, 2, 17),
        "Landratsamt Beispielkreis", "Amtsplatz 1", "73728", "Esslingen am Neckar",
        List.of(new Item("Bestandsaufnahme Heizungsanlage", "2.5", "95.00", "19"), new Item("Planungsleistung Phase 2", "18", "95.00", "19"),
            new Item("Fachbuch Heizlastberechnung", "1", "39.00", "7"), new Item("Reisekosten pauschal", "1", "120.00", "19")),
        "2106.50", "395.56", "2502.06");

    @Test
    void classicLayout() throws Exception {
        run("klassisch", SampleInvoices.classic(), CLASSIC);
    }

    @Test
    void modernLayout() throws Exception {
        run("modern", SampleInvoices.modern(), MODERN, new Opts("86/123/45678", false, false, false, DocumentType.INVOICE));
    }

    @Test
    void serviceLayoutWithTwoVatRatesAndTwoPages() throws Exception {
        run("dienstleister", SampleInvoices.service(), SERVICE, new Opts("59/123/45678", false, false, false, DocumentType.INVOICE));
    }

    /** Was die Person in der Prüfmaske ergänzt, weil die Vorlage es nicht hergibt; taxNo null = nicht prüfen. */
    record Opts(String taxNo, boolean fillDelivery, boolean fillRef, boolean fillTerms, DocumentType type) {
        static final Opts NONE = new Opts(null, false, false, false, DocumentType.INVOICE);
    }

    private void run(String label, byte[] pdf, Expect e) throws Exception {
        run(label, pdf, e, Opts.NONE);
    }

    private void run(String label, byte[] pdf, Expect e, Opts o) throws Exception {
        Path samples = Path.of("target", "samples");
        Files.createDirectories(samples);
        Files.write(samples.resolve("beispiel-" + label + ".pdf"), pdf);
        Files.writeString(samples.resolve("beispiel-" + label + ".txt"), io.github.ebolansk.erechnung.extract.PdfText.extract(pdf).text());
        SoftAssertions soft = new SoftAssertions();
        for (OutputFormat format : OutputFormat.values()) {
            Path home = tmp.resolve(label + "-" + format);
            Files.createDirectories(home.resolve("eingang"));
            Path file = home.resolve("eingang/rechnung.pdf");
            Files.write(file, pdf);
            Clock clock = Clock.fixed(Instant.parse("2027-02-10T10:00:00Z"), ZoneOffset.UTC);
            ConfigStore config = new ConfigStore(home.resolve("daten/konfiguration.json"));
            config.save(new AppConfig(home.resolve("archiv").toString(), "{Mandant}/{Jahr}/{Monat}/{Rechnungsnummer}", format,
                LocalDate.of(2027, 1, 1)));
            var service = new ProcessingService(home, config, new MandantStore(home.resolve("daten/mandanten.json")),
                new AuditLog(home.resolve("daten/protokoll.jsonl"), clock, "tester"), clock);

            var first = service.prepare(file);
            soft.assertThat(first.mandant()).as(label + ": neuer Aussteller ist unbekannt").isEmpty();
            MandantDraft md = RuleBasedExtractor.draftMandant(first.text(), first.cells());
            soft.assertThat(md.name()).as(label + ": Mandant Name").isEqualTo(e.sellerName());
            soft.assertThat(md.street()).as(label + ": Mandant Straße").isEqualTo(e.street());
            soft.assertThat(md.zip()).as(label + ": Mandant PLZ").isEqualTo(e.zip());
            soft.assertThat(md.city()).as(label + ": Mandant Ort").isEqualTo(e.city());
            soft.assertThat(md.vatId()).as(label + ": Mandant USt-IdNr.").isEqualTo(e.vatId());
            soft.assertThat(md.iban()).as(label + ": Mandant IBAN").isEqualTo(e.iban());
            soft.assertThat(md.bic()).as(label + ": Mandant BIC").isEqualTo(e.bic());
            if (o.taxNo() != null) {
                soft.assertThat(md.taxNumber()).as(label + ": Mandant Steuernummer").isEqualTo(o.taxNo());
            }

            // So wie der Mensch im Dialog: erkannte Werte übernehmen, Kontakt ergänzen.
            Mandant mandant = service.registerMandant(new Mandant(null, e.sellerName(), e.street(), e.zip(), e.city(), "DE",
                e.vatId(), o.taxNo() == null ? "" : o.taxNo(), "rechnung@aussteller.example", "Erika Beispiel", "+49 7121 000000", e.iban(), e.bic(), ""));
            var p = service.prepare(file);
            soft.assertThat(p.mandant()).as(label + ": Aussteller wird wiedererkannt").isPresent();
            InvoiceData d = p.extraction().draft();
            soft.assertThat(d.type()).as(label + ": Dokumentart").isEqualTo(o.type());
            soft.assertThat(d.number()).as(label + ": Nummer").isEqualTo(e.number());
            soft.assertThat(d.issueDate()).as(label + ": Rechnungsdatum").isEqualTo(e.issue());
            soft.assertThat(d.deliveryDate()).as(label + ": Leistungsdatum").isEqualTo(e.delivery());
            soft.assertThat(d.deliveryPeriodEnd()).as(label + ": Leistungsende").isEqualTo(e.deliveryEnd());
            soft.assertThat(d.buyerReference()).as(label + ": Käuferreferenz").isEqualTo(e.ref());
            soft.assertThat(d.dueDate()).as(label + ": Fälligkeit").isEqualTo(e.due());
            soft.assertThat(d.buyer().name()).as(label + ": Käufer Name").isEqualTo(e.buyerName());
            soft.assertThat(d.buyer().street()).as(label + ": Käufer Straße").isEqualTo(e.buyerStreet());
            soft.assertThat(d.buyer().zip()).as(label + ": Käufer PLZ").isEqualTo(e.buyerZip());
            soft.assertThat(d.buyer().city()).as(label + ": Käufer Ort").isEqualTo(e.buyerCity());
            soft.assertThat(d.items()).as(label + ": Positionen").hasSize(e.items().size());
            for (int i = 0; i < Math.min(d.items().size(), e.items().size()); i++) {
                LineItem got = d.items().get(i);
                Item want = e.items().get(i);
                soft.assertThat(got.name()).as(label + ": Position " + (i + 1) + " Text").isEqualTo(want.name());
                soft.assertThat(got.quantity()).as(label + ": Position " + (i + 1) + " Menge").isEqualByComparingTo(new BigDecimal(want.qty()));
                soft.assertThat(got.unitPrice()).as(label + ": Position " + (i + 1) + " Preis").isEqualByComparingTo(new BigDecimal(want.price()));
                soft.assertThat(got.vatPercent()).as(label + ": Position " + (i + 1) + " USt").isEqualByComparingTo(new BigDecimal(want.vat()));
            }
            var printed = p.extraction().printed();
            soft.assertThat(printed.net()).as(label + ": gedruckt netto").isEqualByComparingTo(e.net());
            soft.assertThat(printed.tax()).as(label + ": gedruckt Steuer").isEqualByComparingTo(e.tax());
            soft.assertThat(printed.gross()).as(label + ": gedruckt brutto").isEqualByComparingTo(e.gross());

            if (o.fillDelivery()) {
                soft.assertThat(d.deliveryDate()).as(label + ": Leistungsdatum fehlt in der Vorlage").isNull();
                d = copy(d, d.issueDate(), d.buyerReference(), d.paymentTerms());
            }
            if (o.fillRef()) {
                soft.assertThat(d.buyerReference()).as(label + ": Referenz fehlt in der Vorlage").isEmpty();
                d = copy(d, d.deliveryDate(), "KR-" + e.number(), d.paymentTerms());
            }
            if (o.fillTerms()) {
                d = copy(d, d.deliveryDate(), d.buyerReference(), "Zahlbar innerhalb von 14 Tagen netto");
            }
            var out = service.generateAndArchive(p, mandant, d, "tester");
            soft.assertThat(out.problems()).as(label + " " + format + ": Probleme").isEmpty();
            soft.assertThat(out.archived()).as(label + " " + format + ": archiviert").isTrue();
            if (out.report() != null) {
                soft.assertThat(out.report().passed()).as(label + " " + format + ": Prüfung bestanden").isTrue();
            }
        }
        soft.assertAll();
    }

    private static InvoiceData copy(InvoiceData d, LocalDate delivery, String ref, String terms) {
        return new InvoiceData(d.type(), d.number(), d.issueDate(), delivery, d.deliveryPeriodEnd(), d.dueDate(), d.currency(),
            ref, terms, d.seller(), d.buyer(), d.iban(), d.bic(), d.items());
    }

    @Test
    void tenFurtherLayouts() throws Exception {
        List<String> failures = new java.util.ArrayList<>();
        for (SampleVariants.V v : SampleVariants.all()) {
            List<Item> items = new java.util.ArrayList<>();
            for (SampleVariants.It i : v.items) {
                boolean negative = new BigDecimal(i.price()).signum() < 0;
                items.add(new Item(i.name(), negative ? "-" + i.qty() : i.qty(), negative ? i.price().substring(1) : i.price(), String.valueOf(i.vat())));
            }
            LocalDate due = v.dueStyle.equals("bis") || v.dueStyle.equals("faellig") ? v.due : null;
            Expect e = new Expect(v.sellerName, v.street, v.zip, v.city, v.vatId, v.iban, v.bic, v.number, v.issue, v.delivery,
                v.periodEnd, v.ref, due, v.buyerName, v.buyerStreet, v.buyerZip, v.buyerCity, items,
                v.net().toPlainString(), v.tax().toPlainString(), v.gross().toPlainString());
            Opts o = new Opts(v.taxNo.isEmpty() ? null : v.taxNo, v.delivery == null, v.ref.isEmpty(),
                v.dueStyle.equals("none"), v.type);
            try {
                run(v.label, SampleVariants.render(v), e, o);
            } catch (AssertionError ex) {
                failures.add("### " + v.label + "\n" + ex.getMessage());
            }
        }
        assertThat(failures).as(String.join("\n\n", failures)).isEmpty();
    }
}
