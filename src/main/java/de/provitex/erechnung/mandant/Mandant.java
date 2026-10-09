// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.mandant;

import de.provitex.erechnung.model.Party;

public record Mandant(String id, String name, String street, String zip, String city, String country,
                      String vatId, String taxNumber, String email, String contactName, String contactPhone,
                      String iban, String bic, String paymentTerms) {
    public Party toParty() {
        return new Party(name, street, zip, city, country, vatId, taxNumber, email, contactName, contactPhone);
    }
}
