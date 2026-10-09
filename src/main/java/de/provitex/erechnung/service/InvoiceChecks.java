// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.service;

import de.provitex.erechnung.extract.ExtractionResult.PrintedTotals;
import de.provitex.erechnung.model.InvoiceCalculator.Totals;
import de.provitex.erechnung.model.InvoiceData;
import de.provitex.erechnung.model.LineItem;
import de.provitex.erechnung.model.Party;
import de.provitex.erechnung.util.GermanFormats;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class InvoiceChecks {
    private InvoiceChecks() {
    }

    public static List<String> missingFields(InvoiceData d) {
        List<String> m = new ArrayList<>();
        if (blank(d.number())) {
            m.add("Rechnungsnummer fehlt.");
        }
        if (d.issueDate() == null) {
            m.add("Rechnungsdatum fehlt.");
        }
        if (d.deliveryDate() == null) {
            m.add("Leistungsdatum fehlt (Pflichtangabe nach § 14 Abs. 4 UStG).");
        }
        if (blank(d.currency())) {
            m.add("Währung fehlt.");
        }
        party("Verkäufer", d.seller(), true, m);
        party("Käufer", d.buyer(), false, m);
        if (blank(d.buyerReference())) {
            m.add("Käuferreferenz (BT-10) fehlt. Bei Rechnungen an die öffentliche Hand ist das die Leitweg-ID.");
        }
        if (blank(d.iban())) {
            m.add("IBAN fehlt.");
        }
        if (d.dueDate() == null && blank(d.paymentTerms())) {
            m.add("Fälligkeitsdatum oder Zahlungsbedingungen fehlen.");
        }
        if (d.items() == null || d.items().isEmpty()) {
            m.add("Mindestens eine Position ist erforderlich.");
        } else {
            for (int i = 0; i < d.items().size(); i++) {
                LineItem it = d.items().get(i);
                String p = "Position " + (i + 1);
                if (blank(it.name())) {
                    m.add(p + ": Bezeichnung fehlt.");
                }
                if (it.quantity() == null || it.quantity().signum() == 0) {
                    m.add(p + ": Menge darf nicht 0 sein (negative Mengen für Rabatt- oder Korrekturzeilen sind erlaubt).");
                }
                if (it.unitPrice() == null || it.unitPrice().signum() < 0) {
                    m.add(p + ": Einzelpreis darf nicht negativ sein.");
                }
                if (it.vatPercent() == null) {
                    m.add(p + ": Steuersatz fehlt.");
                }
                if ("E".equals(it.vatCategory()) && blank(it.exemptionReason())) {
                    m.add(p + ": Bei Steuerbefreiung muss ein Befreiungsgrund angegeben werden.");
                }
            }
        }
        return m;
    }

    public static List<String> totalsMismatch(Totals computed, PrintedTotals printed) {
        List<String> m = new ArrayList<>();
        compare("Netto", computed.netTotal(), printed.net(), m);
        compare("Steuer", computed.taxTotal(), printed.tax(), m);
        compare("Brutto", computed.grossTotal(), printed.gross(), m);
        return m;
    }

    private static void compare(String label, BigDecimal computed, BigDecimal printed, List<String> out) {
        if (printed != null && printed.compareTo(computed) != 0) {
            out.add(label + ": im PDF " + GermanFormats.formatAmount(printed) + " €, berechnet "
                + GermanFormats.formatAmount(computed) + " €.");
        }
    }

    private static void party(String role, Party p, boolean seller, List<String> m) {
        if (p == null || blank(p.name())) {
            m.add(role + ": Name fehlt.");
            return;
        }
        if (blank(p.street()) || blank(p.zip()) || blank(p.city()) || blank(p.country())) {
            m.add(role + ": Anschrift ist unvollständig.");
        }
        if (blank(p.email())) {
            m.add(role + ": E-Mail-Adresse fehlt (elektronische Adresse " + (seller ? "BT-34" : "BT-49") + ").");
        }
        if (seller) {
            if (blank(p.vatId()) && blank(p.taxNumber())) {
                m.add("Verkäufer: USt-IdNr. oder Steuernummer fehlt.");
            }
            if (blank(p.contactName())) {
                m.add("Verkäufer: Ansprechpartner (BT-41) fehlt. Die XRechnung verlangt Name, Telefon und E-Mail.");
            }
            if (blank(p.contactPhone())) {
                m.add("Verkäufer: Telefonnummer des Ansprechpartners (BT-42) fehlt. Die XRechnung verlangt sie.");
            }
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
