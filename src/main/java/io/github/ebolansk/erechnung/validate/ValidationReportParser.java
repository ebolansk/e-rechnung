// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.validate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class ValidationReportParser {
    private static final Pattern RULE_ID = Pattern.compile("\\[ID ([^\\]]+)]");
    private static final Pattern PDF_FAIL = Pattern.compile(
        "specification=([^,\\]]+), clause=([^,\\]]+), testNumber=(\\d+)\\], status=failed, message=(.*?), location=",
        Pattern.DOTALL);
    private static final Pattern PDF_FAIL_DETAIL = Pattern.compile(
        "specification=([^,\\]]+), clause=([^,\\]]+), testNumber=(\\d+)\\], status=failed, message=(.*?), "
            + "location=Location \\[level=[^,]*, context=(.*?)\\], locationContext=[^,]*, errorMessage=([^\\]]*)\\]",
        Pattern.DOTALL);

    private ValidationReportParser() {
    }

    public static ValidationReport parse(String xml) {
        Document doc;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return failure("Der Prüf-Report des Validators ist nicht lesbar: " + e.getMessage(), xml);
        }
        Element root = doc.getDocumentElement();
        Element xmlEl = child(root, "xml");
        Element pdfEl = child(root, "pdf");
        if (xmlEl == null && pdfEl == null) {
            return failure("Der Validator hat weder einen XML- noch einen PDF-Teil gemeldet.", xml);
        }
        List<Finding> findings = new ArrayList<>();
        boolean xmlValid = true;
        String profile = "";
        String version = "";
        if (xmlEl != null) {
            xmlValid = isValid(xmlEl);
            Element info = child(xmlEl, "info");
            if (info != null) {
                Element p = child(info, "profile");
                profile = p == null ? "" : p.getTextContent().trim();
                Element v = child(info, "validator");
                version = v == null ? "" : v.getAttribute("version");
            }
            for (String tag : List.of("error", "warning", "notice")) {
                NodeList nodes = xmlEl.getElementsByTagName(tag);
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element e = (Element) nodes.item(i);
                    String text = e.getTextContent().trim();
                    Matcher m = RULE_ID.matcher(text);
                    String rule = m.find() ? m.group(1) : "Typ " + e.getAttribute("type");
                    findings.add(new Finding(tag.toUpperCase(java.util.Locale.ROOT), rule, e.getAttribute("location"), text));
                }
            }
        }
        Boolean pdfValid = null;
        if (pdfEl != null) {
            String text = pdfEl.getTextContent();
            pdfValid = isValid(pdfEl) && !text.contains("isCompliant=false");
            boolean any = false;
            Matcher d = PDF_FAIL_DETAIL.matcher(text);
            while (d.find()) {
                any = true;
                findings.add(new Finding("ERROR", d.group(1).trim() + " " + d.group(2).trim() + "-" + d.group(3),
                    d.group(5).trim(), d.group(4).trim() + " – " + d.group(6).trim() + " (PDF/A-Prüfung)"));
            }
            if (!any) {
                Matcher m = PDF_FAIL.matcher(text);
                while (m.find()) {
                    any = true;
                    findings.add(new Finding("ERROR", m.group(1).trim() + " " + m.group(2).trim() + "-" + m.group(3),
                        "PDF", m.group(4).trim() + " (PDF/A-Prüfung)"));
                }
            }
            if (!pdfValid && !any) {
                findings.add(new Finding("ERROR", "PDF/A", "PDF", "Die PDF/A-Prüfung ist fehlgeschlagen."));
            }
        }
        return new ValidationReport(pdfValid, xmlValid, List.copyOf(findings), profile, version, xml);
    }

    private static boolean isValid(Element part) {
        Element s = child(part, "summary");
        return s != null && "valid".equals(s.getAttribute("status"));
    }

    private static Element child(Element parent, String name) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && e.getTagName().equals(name)) {
                return e;
            }
        }
        return null;
    }

    private static ValidationReport failure(String message, String raw) {
        return new ValidationReport(null, false, List.of(new Finding("ERROR", "REPORT", "", message)), "", "", raw);
    }
}
