// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ebolansk.erechnung.TestData;
import io.github.ebolansk.erechnung.extract.ExtractionResult.PrintedTotals;
import io.github.ebolansk.erechnung.model.InvoiceCalculator;
import io.github.ebolansk.erechnung.model.InvoiceData;
import io.github.ebolansk.erechnung.model.LineItem;
import io.github.ebolansk.erechnung.model.OutputFormat;
import io.github.ebolansk.erechnung.model.Party;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceChecksTest {
    @Test
    void completeInvoiceHasNoMissingFields() {
        assertThat(InvoiceChecks.missingFields(TestData.invoice(), OutputFormat.XRECHNUNG)).isEmpty();
    }

    @Test
    void reportsMissingBuyerEmailAndReference() {
        var d = TestData.invoice();
        var buyer = new Party(d.buyer().name(), d.buyer().street(), d.buyer().zip(), d.buyer().city(), "DE", "", "", "", "", "");
        var broken = new InvoiceData(d.type(), d.number(), d.issueDate(), d.deliveryDate(), null, d.dueDate(), d.currency(),
            "", d.paymentTerms(), d.seller(), buyer, d.iban(), d.bic(), d.items());
        var msgs = InvoiceChecks.missingFields(broken, OutputFormat.XRECHNUNG);
        assertThat(msgs).anyMatch(m -> m.contains("E-Mail") && m.contains("Käufer"));
        assertThat(msgs).anyMatch(m -> m.contains("Käuferreferenz"));
    }

    @Test
    void reportsMissingNumberDatesAndItems() {
        var d = TestData.invoice();
        var broken = new InvoiceData(d.type(), "", null, null, null, null, d.currency(), d.buyerReference(), "",
            d.seller(), d.buyer(), "", "", List.of());
        var msgs = InvoiceChecks.missingFields(broken, OutputFormat.XRECHNUNG);
        assertThat(msgs).anyMatch(m -> m.contains("Rechnungsnummer"));
        assertThat(msgs).anyMatch(m -> m.contains("Rechnungsdatum"));
        assertThat(msgs).anyMatch(m -> m.contains("Leistungsdatum"));
        assertThat(msgs).anyMatch(m -> m.contains("IBAN"));
        assertThat(msgs).anyMatch(m -> m.contains("Position"));
        assertThat(msgs).anyMatch(m -> m.contains("Fälligkeit") || m.contains("Zahlungsbedingungen"));
    }

    @Test
    void rejectsNonPositiveQuantityOrNegativePrice() {
        var d = TestData.invoice();
        var items = List.of(new LineItem("x", "", "C62", BigDecimal.ZERO, new BigDecimal("1"), new BigDecimal("19"), "S", ""));
        var broken = new InvoiceData(d.type(), d.number(), d.issueDate(), d.deliveryDate(), null, d.dueDate(), d.currency(),
            d.buyerReference(), d.paymentTerms(), d.seller(), d.buyer(), d.iban(), d.bic(), items);
        assertThat(InvoiceChecks.missingFields(broken, OutputFormat.XRECHNUNG)).anyMatch(m -> m.contains("Menge"));
    }

    @Test
    void negativeQuantityForDiscountLinesIsAllowedButNegativePriceIsNot() {
        var d = TestData.invoice();
        var discount = new LineItem("Rabatt", "", "C62", new BigDecimal("-1"), new BigDecimal("50.00"), new BigDecimal("19"), "S", "");
        var items = new java.util.ArrayList<>(d.items());
        items.add(discount);
        var ok = new InvoiceData(d.type(), d.number(), d.issueDate(), d.deliveryDate(), null, d.dueDate(), d.currency(),
            d.buyerReference(), d.paymentTerms(), d.seller(), d.buyer(), d.iban(), d.bic(), items);
        assertThat(InvoiceChecks.missingFields(ok, OutputFormat.XRECHNUNG)).isEmpty();
        var negPrice = new LineItem("Rabatt", "", "C62", BigDecimal.ONE, new BigDecimal("-50.00"), new BigDecimal("19"), "S", "");
        var bad = new InvoiceData(d.type(), d.number(), d.issueDate(), d.deliveryDate(), null, d.dueDate(), d.currency(),
            d.buyerReference(), d.paymentTerms(), d.seller(), d.buyer(), d.iban(), d.bic(), List.of(negPrice));
        assertThat(InvoiceChecks.missingFields(bad, OutputFormat.XRECHNUNG)).anyMatch(m -> m.contains("Einzelpreis"));
    }

    @Test
    void sellerContactNameAndPhoneAreBothRequired() {
        var d = TestData.invoice();
        var s = d.seller();
        var noPhone = new Party(s.name(), s.street(), s.zip(), s.city(), s.country(), s.vatId(), "", s.email(), "Max Muster", "");
        var noName = new Party(s.name(), s.street(), s.zip(), s.city(), s.country(), s.vatId(), "", s.email(), "", "+49 7121 1");
        assertThat(InvoiceChecks.missingFields(withSeller(d, noPhone), OutputFormat.XRECHNUNG)).anyMatch(m -> m.contains("Telefon") && m.contains("BT-42"));
        assertThat(InvoiceChecks.missingFields(withSeller(d, noName), OutputFormat.XRECHNUNG)).anyMatch(m -> m.contains("Ansprechpartner") && m.contains("BT-41"));
    }

    @Test
    void zugferdNeedsNeitherContactNorBuyerReferenceButXrechnungDoes() {
        var d = TestData.invoice();
        var s = d.seller();
        var noContact = new Party(s.name(), s.street(), s.zip(), s.city(), s.country(), s.vatId(), "", s.email(), "", "");
        var noRef = new InvoiceData(d.type(), d.number(), d.issueDate(), d.deliveryDate(), d.deliveryPeriodEnd(), d.dueDate(), d.currency(),
            "", d.paymentTerms(), noContact, d.buyer(), d.iban(), d.bic(), d.items());
        assertThat(InvoiceChecks.missingFields(noRef, OutputFormat.ZUGFERD)).isEmpty();
        assertThat(InvoiceChecks.missingFields(noRef, OutputFormat.XRECHNUNG)).anyMatch(m -> m.contains("BT-41"))
            .anyMatch(m -> m.contains("BT-42")).anyMatch(m -> m.contains("BT-10"));
    }

    private static InvoiceData withSeller(InvoiceData d, Party seller) {
        return new InvoiceData(d.type(), d.number(), d.issueDate(), d.deliveryDate(), null, d.dueDate(), d.currency(),
            d.buyerReference(), d.paymentTerms(), seller, d.buyer(), d.iban(), d.bic(), d.items());
    }

    @Test
    void totalsMismatchIsReportedWithBothValues() {
        var computed = InvoiceCalculator.compute(TestData.invoice().items());
        assertThat(InvoiceChecks.totalsMismatch(computed, new PrintedTotals(null, null, null))).isEmpty();
        assertThat(InvoiceChecks.totalsMismatch(computed,
            new PrintedTotals(new BigDecimal("300.00"), new BigDecimal("57.00"), new BigDecimal("357.00")))).isEmpty();
        var msgs = InvoiceChecks.totalsMismatch(computed,
            new PrintedTotals(new BigDecimal("300.00"), new BigDecimal("57.00"), new BigDecimal("358.00")));
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("358,00").contains("357,00");
    }
}
