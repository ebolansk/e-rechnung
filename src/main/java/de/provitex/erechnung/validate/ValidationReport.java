// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.validate;

import java.util.List;

public record ValidationReport(Boolean pdfValid, boolean xmlValid, List<Finding> findings,
                               String profile, String validatorVersion, String rawXml) {
    public boolean passed() {
        return xmlValid && (pdfValid == null || pdfValid) && errors().isEmpty();
    }

    public List<Finding> errors() {
        return findings.stream().filter(f -> "ERROR".equals(f.severity())).toList();
    }
}
