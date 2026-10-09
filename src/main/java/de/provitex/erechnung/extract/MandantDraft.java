// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.extract;

public record MandantDraft(String name, String street, String zip, String city, String vatId, String taxNumber,
                           String iban, String bic, String email, String phone) {
}
