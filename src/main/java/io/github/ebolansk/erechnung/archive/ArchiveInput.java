// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.archive;

import io.github.ebolansk.erechnung.model.InvoiceData;
import io.github.ebolansk.erechnung.model.OutputFormat;
import io.github.ebolansk.erechnung.validate.ValidationReport;
import java.time.Instant;

public record ArchiveInput(String mandantId, String mandantName, InvoiceData invoice, OutputFormat format,
                           byte[] originalPdf, String originalFileName, byte[] output, String outputFileName,
                           String protocolHtml, ValidationReport report, BelegStatus.BelegAssessment beleg,
                           String confirmedBy, Instant confirmedAt, Confirmation confirmation) {
    public ArchiveInput(String mandantId, String mandantName, InvoiceData invoice, OutputFormat format,
                        byte[] originalPdf, String originalFileName, byte[] output, String outputFileName,
                        String protocolHtml, ValidationReport report, BelegStatus.BelegAssessment beleg,
                        String confirmedBy, Instant confirmedAt) {
        this(mandantId, mandantName, invoice, format, originalPdf, originalFileName, output, outputFileName,
            protocolHtml, report, beleg, confirmedBy, confirmedAt, Confirmation.none());
    }
}
