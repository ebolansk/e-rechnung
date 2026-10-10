// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.extract;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ebolansk.erechnung.mandant.Mandant;
import io.github.ebolansk.erechnung.model.DocumentType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

public class RuleBasedExtractorTest {
    public static final String TEXT = """
        Muster GmbH · Hauptstr. 1 · 72760 Reutlingen
        Kunde AG
        Einkauf
        Nebenstr. 2
        10115 Berlin
        Rechnung
        Rechnungsnummer: RE-2027-0001
        Rechnungsdatum: 15.01.2027
        Leistungsdatum: 10.01.2027
        Leitweg-ID: 04011000-12345-34
        Pos Bezeichnung Menge Einheit Einzelpreis Gesamt
        1 Beratung 3 Std 100,00 € 300,00 €
        2 Software-Lizenz 2 Stk 1.250,50 € 2.501,00 €
        Zwischensumme 2.801,00 €
        MwSt. 19 % 532,19 €
        Gesamtbetrag 3.333,19 €
        Zahlbar innerhalb von 30 Tagen netto bis 14.02.2027
        USt-IdNr.: DE123456789
        IBAN: DE02 1203 0000 0000 2020 51 BIC: BYLADEM1001
        rechnung@muster.example
        """;

    public static Mandant mandant() {
        return new Mandant("m1", "Muster GmbH", "Hauptstr. 1", "72760", "Reutlingen", "DE", "DE123456789", "",
            "rechnung@muster.example", "Max Muster", "+49 7121 123456", "DE02120300000000202051", "BYLADEM1001", "");
    }

    @Test
    void extractsHeaderFields() {
        var r = RuleBasedExtractor.extract(TEXT, mandant());
        var d = r.draft();
        assertThat(d.type()).isEqualTo(DocumentType.INVOICE);
        assertThat(d.number()).isEqualTo("RE-2027-0001");
        assertThat(d.issueDate()).isEqualTo(LocalDate.of(2027, 1, 15));
        assertThat(d.deliveryDate()).isEqualTo(LocalDate.of(2027, 1, 10));
        assertThat(d.buyerReference()).isEqualTo("04011000-12345-34");
        assertThat(d.dueDate()).isEqualTo(LocalDate.of(2027, 2, 14));
        assertThat(d.seller().name()).isEqualTo("Muster GmbH");
        assertThat(d.iban()).isEqualTo("DE02120300000000202051");
        assertThat(d.currency()).isEqualTo("EUR");
    }

    @Test
    void extractsBuyerAddressBlock() {
        var b = RuleBasedExtractor.extract(TEXT, mandant()).draft().buyer();
        assertThat(b.name()).isEqualTo("Kunde AG");
        assertThat(b.street()).isEqualTo("Nebenstr. 2");
        assertThat(b.zip()).isEqualTo("10115");
        assertThat(b.city()).isEqualTo("Berlin");
    }

    @Test
    void extractsLineItemsWithGermanNumbers() {
        var items = RuleBasedExtractor.extract(TEXT, mandant()).draft().items();
        assertThat(items).hasSize(2);
        assertThat(items.get(0).name()).isEqualTo("Beratung");
        assertThat(items.get(0).quantity()).isEqualByComparingTo("3");
        assertThat(items.get(0).unit()).isEqualTo("HUR");
        assertThat(items.get(0).unitPrice()).isEqualByComparingTo("100.00");
        assertThat(items.get(0).vatPercent()).isEqualByComparingTo("19");
        assertThat(items.get(1).unitPrice()).isEqualByComparingTo("1250.50");
        assertThat(items.get(1).unit()).isEqualTo("C62");
    }

    @Test
    void discountRowWithNegativePriceBecomesNegativeQuantity() {
        String text = TEXT.replace("Zwischensumme", "3 Treuerabatt 1 Stk -50,00 € -50,00 €\nZwischensumme");
        var items = RuleBasedExtractor.extract(text, mandant()).draft().items();
        assertThat(items).hasSize(3);
        assertThat(items.get(2).name()).isEqualTo("Treuerabatt");
        assertThat(items.get(2).quantity()).isEqualByComparingTo("-1");
        assertThat(items.get(2).unitPrice()).isEqualByComparingTo("50.00");
    }

    @Test
    void readsPrintedTotals() {
        var p = RuleBasedExtractor.extract(TEXT, mandant()).printed();
        assertThat(p.net()).isEqualByComparingTo("2801.00");
        assertThat(p.tax()).isEqualByComparingTo("532.19");
        assertThat(p.gross()).isEqualByComparingTo("3333.19");
    }

    @Test
    void creditNoteIsRecognizedFromHeading() {
        String text = TEXT.replace("Rechnung\nRechnungsnummer", "Gutschrift\nGutschriftnummer");
        var r = RuleBasedExtractor.extract(text, mandant());
        assertThat(r.draft().type()).isEqualTo(DocumentType.CREDIT_NOTE);
    }

    @Test
    void missingFieldsAreReportedInNotesNotInvented() {
        var r = RuleBasedExtractor.extract("Irgendein Text ohne Rechnungsdaten", mandant());
        assertThat(r.draft().number()).isEmpty();
        assertThat(r.draft().issueDate()).isNull();
        assertThat(r.draft().items()).isEmpty();
        assertThat(r.notes()).anyMatch(n -> n.contains("Rechnungsnummer"));
        assertThat(r.notes()).anyMatch(n -> n.contains("Positionen"));
    }

    @Test
    void mandantDraftIsReadFromText() {
        var d = RuleBasedExtractor.draftMandant(TEXT);
        assertThat(d.vatId()).isEqualTo("DE123456789");
        assertThat(d.iban()).isEqualTo("DE02120300000000202051");
        assertThat(d.bic()).isEqualTo("BYLADEM1001");
        assertThat(d.email()).isEqualTo("rechnung@muster.example");
        assertThat(d.name()).isEqualTo("Muster GmbH");
        assertThat(d.street()).isEqualTo("Hauptstr. 1");
        assertThat(d.zip()).isEqualTo("72760");
        assertThat(d.city()).isEqualTo("Reutlingen");
    }

    @Test
    void mandantDraftTakesOnlyAnIbanWithAValidChecksum() {
        String base = "Muster GmbH · Hauptstr. 1 · 70173 Stuttgart\nUSt-IdNr. DE123456789\n";
        assertThat(RuleBasedExtractor.draftMandant(base + "IBAN DE02 1203 0000 0000 2020 52").iban())
            .as("Tippfehler: keine IBAN vorschlagen").isEmpty();
        assertThat(RuleBasedExtractor.draftMandant(base + "IBAN DE02 1203 0000 0000 2020 52\nLastschrift: DE89 3704 0044 0532 0130 00").iban())
            .as("die erste IBAN mit gültiger Prüfsumme").isEqualTo("DE89370400440532013000");
        assertThat(RuleBasedExtractor.draftMandant(base + "IBAN DE02 1203 0000 0000 2020 51").iban()).isEqualTo("DE02120300000000202051");
    }
}
