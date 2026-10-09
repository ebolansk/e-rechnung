// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.generate;

public class PdfaNotPossibleException extends Exception {
    public PdfaNotPossibleException(String message) {
        super(message);
    }

    public PdfaNotPossibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
