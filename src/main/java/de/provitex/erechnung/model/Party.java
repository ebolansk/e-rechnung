// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.model;

public record Party(String name, String street, String zip, String city, String country,
                    String vatId, String taxNumber, String email,
                    String contactName, String contactPhone) {
    public static Party empty() {
        return new Party("", "", "", "", "DE", "", "", "", "", "");
    }
}
