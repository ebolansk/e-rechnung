// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.generate;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.AdobePDFSchema;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.schema.PDFAIdentificationSchema;
import org.apache.xmpbox.schema.XMPBasicSchema;
import org.apache.xmpbox.type.BadFieldValueException;
import org.apache.xmpbox.xml.XmpSerializer;
import javax.xml.transform.TransformerException;

/** Macht aus einem normalen Rechnungs-PDF ein PDF/A-3B (Voraussetzung für ZUGFeRD). */
public final class PdfaPreparer {
    private static final Pattern PART = Pattern.compile("pdfaid:part(?:>|=\")\\s*([123])");
    private static final String PRODUCER = "E-Rechnung-Tool";

    public byte[] prepare(byte[] source, String title) throws PdfaNotPossibleException, IOException {
        PDDocument doc;
        try {
            doc = Loader.loadPDF(source);
        } catch (IOException e) {
            throw new PdfaNotPossibleException("Die Datei ist kein lesbares PDF: " + e.getMessage(), e);
        }
        try (doc) {
            if (doc.isEncrypted()) {
                throw new PdfaNotPossibleException("Das PDF ist verschlüsselt und kann nicht in PDF/A umgewandelt werden.");
            }
            int part = pdfaPart(doc);
            if (part == 1 || part == 3) {
                return source;
            }
            List<String> missing = unembeddedFonts(doc);
            if (!missing.isEmpty()) {
                throw new PdfaNotPossibleException("Im PDF sind Schriften nicht eingebettet (" + String.join(", ", missing)
                    + "). Für ZUGFeRD ist ein PDF/A nötig, bei dem alle Schriften eingebettet sind. "
                    + "Bitte das PDF im Rechnungsprogramm mit eingebetteten Schriften erzeugen oder als Ausgabeformat XRechnung wählen.");
            }
            apply(doc, title);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static void apply(PDDocument doc, String title) throws IOException {
        Calendar now = new GregorianCalendar();
        PDDocumentInformation info = doc.getDocumentInformation();
        info.setTitle(title);
        info.setProducer(PRODUCER);
        info.setCreationDate(info.getCreationDate() != null ? info.getCreationDate() : now);
        info.setModificationDate(now);

        ByteArrayOutputStream xmpOut = new ByteArrayOutputStream();
        try {
            XMPMetadata xmp = XMPMetadata.createXMPMetadata();
            PDFAIdentificationSchema id = xmp.createAndAddPDFAIdentificationSchema();
            id.setPart(3);
            id.setConformance("B");
            DublinCoreSchema dc = xmp.createAndAddDublinCoreSchema();
            dc.setTitle(title);
            AdobePDFSchema adobe = xmp.createAndAddAdobePDFSchema();
            adobe.setProducer(PRODUCER);
            XMPBasicSchema basic = xmp.createAndAddXMPBasicSchema();
            basic.setCreateDate(info.getCreationDate());
            basic.setModifyDate(now);
            basic.setMetadataDate(now);
            basic.setCreatorTool(PRODUCER);
            new XmpSerializer().serialize(xmp, xmpOut, true);
        } catch (BadFieldValueException | TransformerException e) {
            throw new IOException("XMP-Metadaten konnten nicht erzeugt werden", e);
        }
        PDMetadata meta = new PDMetadata(doc);
        meta.importXMPMetadata(xmpOut.toByteArray());
        doc.getDocumentCatalog().setMetadata(meta);

        if (doc.getDocumentCatalog().getOutputIntents().isEmpty()) {
            ICC_Profile icc = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
            PDOutputIntent oi = new PDOutputIntent(doc, new ByteArrayInputStream(icc.getData()));
            oi.setInfo("sRGB IEC61966-2.1");
            oi.setOutputCondition("sRGB IEC61966-2.1");
            oi.setOutputConditionIdentifier("sRGB IEC61966-2.1");
            oi.setRegistryName("http://www.color.org");
            doc.getDocumentCatalog().addOutputIntent(oi);
        }
    }

    private static int pdfaPart(PDDocument doc) throws IOException {
        PDMetadata meta = doc.getDocumentCatalog().getMetadata();
        if (meta == null) {
            return 0;
        }
        String xml = new String(meta.toByteArray(), StandardCharsets.UTF_8);
        Matcher m = PART.matcher(xml);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static List<String> unembeddedFonts(PDDocument doc) throws IOException {
        Set<String> names = new LinkedHashSet<>();
        Set<COSBase> seen = new LinkedHashSet<>();
        for (PDPage page : doc.getPages()) {
            collect(page.getResources(), seen, names);
        }
        return new ArrayList<>(names);
    }

    private static void collect(PDResources res, Set<COSBase> seen, Set<String> names) throws IOException {
        if (res == null || !seen.add(res.getCOSObject())) {
            return;
        }
        for (COSName fontName : res.getFontNames()) {
            PDFont font = res.getFont(fontName);
            if (font != null && !font.isEmbedded()) {
                names.add(font.getName());
            }
        }
        for (COSName xName : res.getXObjectNames()) {
            PDXObject x = res.getXObject(xName);
            if (x instanceof PDFormXObject form) {
                collect(form.getResources(), seen, names);
            }
        }
    }
}
