// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.generate;

import de.provitex.erechnung.model.InvoiceData;
import de.provitex.erechnung.model.LineItem;
import de.provitex.erechnung.model.Party;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import org.mustangproject.BankDetails;
import org.mustangproject.Contact;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.SchemedID;
import org.mustangproject.TradeParty;

public final class MustangMapper {
    private MustangMapper() {
    }

    public static Invoice toInvoice(InvoiceData d) {
        TradeParty seller = party(d.seller());
        if (notBlank(d.iban())) {
            seller.addBankDetails(notBlank(d.bic()) ? new BankDetails(d.iban(), d.bic()) : new BankDetails(d.iban()));
        }
        if (notBlank(d.seller().contactName()) || notBlank(d.seller().contactPhone()) || notBlank(d.seller().email())) {
            seller.setContact(new Contact(nz(d.seller().contactName()), nz(d.seller().contactPhone()), nz(d.seller().email())));
        }
        Invoice inv = new Invoice()
            .setDocumentCode(d.type().code())
            .setNumber(d.number())
            .setIssueDate(date(d.issueDate()))
            .setSender(seller)
            .setRecipient(party(d.buyer()))
            .setCurrency(d.currency());
        if (d.dueDate() != null) {
            inv.setDueDate(date(d.dueDate()));
        }
        if (d.deliveryPeriodEnd() != null) {
            inv.setDetailedDeliveryPeriod(date(d.deliveryDate()), date(d.deliveryPeriodEnd()));
        } else if (d.deliveryDate() != null) {
            inv.setDeliveryDate(date(d.deliveryDate()));
        }
        if (notBlank(d.buyerReference())) {
            inv.setReferenceNumber(d.buyerReference());
        }
        if (notBlank(d.paymentTerms())) {
            inv.setPaymentTermDescription(d.paymentTerms());
        }
        for (LineItem i : d.items()) {
            Product p = new Product(i.name(), nz(i.description()), i.unit(), i.vatPercent());
            p.setTaxCategoryCode(i.vatCategory());
            if (notBlank(i.exemptionReason())) {
                p.setTaxExemptionReason(i.exemptionReason());
            }
            inv.addItem(new Item(p, i.unitPrice(), i.quantity()));
        }
        return inv;
    }

    private static TradeParty party(Party p) {
        TradeParty t = new TradeParty(p.name(), p.street(), p.zip(), p.city(), p.country());
        if (notBlank(p.vatId())) {
            t.addVATID(p.vatId());
        }
        if (notBlank(p.taxNumber())) {
            t.addTaxID(p.taxNumber());
            if (!notBlank(p.vatId())) {
                // BR-CO-26 verlangt BT-29, BT-30 oder BT-31. Ohne USt-IdNr. dient die Steuernummer als Verkäuferkennung (BT-29).
                t.setID(p.taxNumber());
            }
        }
        if (notBlank(p.email())) {
            t.addUriUniversalCommunicationID(new SchemedID().setScheme("EM").setId(p.email()));
        }
        return t;
    }

    /** Mustang formatiert Datumswerte in der Standard-Zeitzone der JVM, daher dieselbe Zone verwenden. */
    private static Date date(LocalDate d) {
        return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
