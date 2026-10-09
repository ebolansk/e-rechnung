// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.generate;

import de.provitex.erechnung.model.InvoiceData;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromPDFA;

public final class ZugferdGenerator {
    private final PdfaPreparer preparer = new PdfaPreparer();

    public byte[] generate(InvoiceData data, byte[] sourcePdf) throws PdfaNotPossibleException, IOException {
        byte[] pdfa3 = preparer.prepare(sourcePdf, "Rechnung " + data.number());
        ZUGFeRDExporterFromPDFA exporter = new ZUGFeRDExporterFromPDFA();
        try {
            exporter.load(pdfa3);
            exporter.setProfile(Profiles.getByName("EN16931"));
            exporter.setTransaction(MustangMapper.toInvoice(data));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            exporter.export(out);
            return out.toByteArray();
        } catch (IllegalArgumentException e) {
            throw new PdfaNotPossibleException("Das PDF kann nicht als ZUGFeRD verwendet werden: " + e.getMessage(), e);
        } finally {
            try {
                exporter.close();
            } catch (IOException | RuntimeException ignored) {
                // Das Dokument ist nach export() meist schon geschlossen.
            }
        }
    }
}
