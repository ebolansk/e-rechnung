// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.archive;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public enum BelegStatus {
    /** Der Kunde bekommt weiterhin das PDF, die E-Rechnung ist eine abgeleitete Kopie. */
    PDF_IST_ORIGINAL,
    /** Der Kunde bekommt ausschließlich die E-Rechnung, sie ist der Original-Beleg. */
    E_RECHNUNG_IST_ORIGINAL;

    public record BelegAssessment(BelegStatus status, List<String> warnings) {
    }

    public static BelegAssessment assess(LocalDate issue, LocalDate delivery, LocalDate deliveryEnd, LocalDate cutoff) {
        List<String> warnings = new ArrayList<>();
        LocalDate effective = deliveryEnd != null ? deliveryEnd : delivery;
        if (effective == null) {
            warnings.add("Leistungsdatum fehlt, ersatzweise wird das Rechnungsdatum verwendet.");
            effective = issue;
        }
        boolean issueAfter = !issue.isBefore(cutoff);
        boolean deliveryAfter = !effective.isBefore(cutoff);
        if (deliveryAfter && !issueAfter) {
            warnings.add("Vorausrechnung: Die Leistung liegt ab dem Stichtag, daher ist eine E-Rechnung erforderlich.");
        }
        if (delivery != null && deliveryEnd != null && delivery.isBefore(cutoff) && !deliveryEnd.isBefore(cutoff)) {
            warnings.add("Der Leistungszeitraum überspannt den Stichtag. Bitte mit der Steuerberatung klären.");
        }
        BelegStatus status = issueAfter || deliveryAfter ? E_RECHNUNG_IST_ORIGINAL : PDF_IST_ORIGINAL;
        return new BelegAssessment(status, List.copyOf(warnings));
    }
}
