// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.model;

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
