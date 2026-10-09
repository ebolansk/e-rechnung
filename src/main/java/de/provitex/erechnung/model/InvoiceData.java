// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.model;

import java.time.LocalDate;
import java.util.List;

/** deliveryDate = Leistungsdatum bzw. Beginn des Leistungszeitraums; deliveryPeriodEnd nur bei Zeitraum. */
public record InvoiceData(DocumentType type, String number, LocalDate issueDate,
                          LocalDate deliveryDate, LocalDate deliveryPeriodEnd, LocalDate dueDate,
                          String currency, String buyerReference, String paymentTerms,
                          Party seller, Party buyer, String iban, String bic,
                          List<LineItem> items) {
}
