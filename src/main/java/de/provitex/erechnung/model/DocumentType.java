// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.model;

public enum DocumentType {
    INVOICE("380"), CREDIT_NOTE("381");

    private final String code;

    DocumentType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
