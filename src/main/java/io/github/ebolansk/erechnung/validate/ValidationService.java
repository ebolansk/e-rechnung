// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.validate;

import java.util.List;
import org.mustangproject.validator.ZUGFeRDValidator;

public final class ValidationService {
    /** Prüft XML (XRechnung/ZUGFeRD-XML) oder ein ZUGFeRD-PDF. Ein neuer Validator pro Aufruf (nicht thread-sicher). */
    public ValidationReport validate(byte[] content, String fileName) {
        try {
            ZUGFeRDValidator validator = new ZUGFeRDValidator();
            validator.disableNotices();
            String xml = validator.validate(content, fileName);
            return ValidationReportParser.parse(xml);
        } catch (RuntimeException e) {
            return new ValidationReport(null, false,
                List.of(new Finding("ERROR", "VALIDATOR", "", "Die Prüfung ist abgebrochen: " + e.getMessage())),
                "", "", "");
        }
    }
}
