// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.generate;

public class PdfaNotPossibleException extends Exception {
    public PdfaNotPossibleException(String message) {
        super(message);
    }

    public PdfaNotPossibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
