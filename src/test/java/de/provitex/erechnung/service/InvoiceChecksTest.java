// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.provitex.erechnung.TestData;
import de.provitex.erechnung.extract.ExtractionResult.PrintedTotals;
import de.provitex.erechnung.model.InvoiceCalculator;
import de.provitex.erechnung.model.InvoiceData;
import de.provitex.erechnung.model.LineItem;
import de.provitex.erechnung.model.Party;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceChecksTest {
    @Test
    void completeInvoiceHasNoMissingFields() {
        assertThat(InvoiceChecks.missingFields(TestData.invoice())).isEmpty();
    }

    @Test
    void reportsMissingBuyerEmailAndReference() {
        var d = TestData.invoice();
        var buyer = new Party(d.buyer().name(), d.buyer().street(), d.buyer().zip(), d.buyer().city(), "DE", "", "", "", "", "");
        var broken = new InvoiceData(d.type(), d.number(), d.issueDate(), d.deliveryDate(), null, d.dueDate(), d.currency(),
            "", d.paymentTerms(), d.seller(), buyer, d.iban(), d.bic(), d.items());
        var msgs = InvoiceChecks.missingFields(broken);
        assertThat(msgs).anyMatch(m -> m.contains("E-Mail") && m.contains("Käufer"));
        assertThat(msgs).anyMatch(m -> m.contains("Käuferreferenz"));
    }

    @Test
    void reportsMissingNumberDatesAndItems() {
        var d = TestData.invoice();
        var broken = new InvoiceData(d.type(), "", null, null, null, null, d.currency(), d.buyerReference(), "",
            d.seller(), d.buyer(), "", "", List.of());
        var msgs = InvoiceChecks.missingFields(broken);
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
        assertThat(InvoiceChecks.missingFields(broken)).anyMatch(m -> m.contains("Menge"));
    }

    @Test
    void negativeQuantityForDiscountLinesIsAllowedButNegativePriceIsNot() {
        var d = TestData.invoice();
        var discount = new LineItem("Rabatt", "", "C62", new BigDecimal("-1"), new BigDecimal("50.00"), new BigDecimal("19"), "S", "");
        var items = new java.util.ArrayList<>(d.items());
        items.add(discount);
        var ok = new InvoiceData(d.type(), d.number(), d.issueDate(), d.deliveryDate(), null, d.dueDate(), d.currency(),
            d.buyerReference(), d.paymentTerms(), d.seller(), d.buyer(), d.iban(), d.bic(), items);
        assertThat(InvoiceChecks.missingFields(ok)).isEmpty();
        var negPrice = new LineItem("Rabatt", "", "C62", BigDecimal.ONE, new BigDecimal("-50.00"), new BigDecimal("19"), "S", "");
        var bad = new InvoiceData(d.type(), d.number(), d.issueDate(), d.deliveryDate(), null, d.dueDate(), d.currency(),
            d.buyerReference(), d.paymentTerms(), d.seller(), d.buyer(), d.iban(), d.bic(), List.of(negPrice));
        assertThat(InvoiceChecks.missingFields(bad)).anyMatch(m -> m.contains("Einzelpreis"));
    }

    @Test
    void sellerContactNameAndPhoneAreBothRequired() {
        var d = TestData.invoice();
        var s = d.seller();
        var noPhone = new Party(s.name(), s.street(), s.zip(), s.city(), s.country(), s.vatId(), "", s.email(), "Max Muster", "");
        var noName = new Party(s.name(), s.street(), s.zip(), s.city(), s.country(), s.vatId(), "", s.email(), "", "+49 7121 1");
        assertThat(InvoiceChecks.missingFields(withSeller(d, noPhone))).anyMatch(m -> m.contains("Telefon") && m.contains("BT-42"));
        assertThat(InvoiceChecks.missingFields(withSeller(d, noName))).anyMatch(m -> m.contains("Ansprechpartner") && m.contains("BT-41"));
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
