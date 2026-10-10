// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.validate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class ValidationReportParserTest {
    public static final String XML_VALID = """
        <?xml version="1.0" encoding="UTF-8"?>
        <validation filename="x.xml" datetime="2027-01-15 10:00:00">
          <xml>
            <info><version>2</version><profile>urn:xeinkauf.de:kosit:xrechnung_3.0</profile><validator version="2.26.0"/></info>
            <messages></messages>
            <summary status="valid"/>
          </xml>
          <messages></messages>
          <summary status="valid"/>
        </validation>""";

    public static final String XML_INVALID = """
        <?xml version="1.0" encoding="UTF-8"?>
        <validation filename="x.xml" datetime="2027-01-15 10:00:00">
          <xml>
            <info><version>2</version><profile>urn:xeinkauf.de:kosit:xrechnung_3.0</profile><validator version="2.26.0"/></info>
            <messages>
              <error type="27" location="/rsm:CrossIndustryInvoice/rsm:SupplyChainTradeTransaction[1]/ram:ApplicableHeaderTradeAgreement[1]/ram:BuyerTradeParty[1]" criterion="ram:URIUniversalCommunication/ram:URIID">Buyer electronic address MUST be provided [ID PEPPOL-EN16931-R010] from /xslt/XR_30/XRechnung-CII-validation.xslt)</error>
              <notice type="27" location="/rsm:CrossIndustryInvoice" criterion="x">Business process MUST be provided. [ID PEPPOL-EN16931-R001] from /xslt/x.xslt)</notice>
            </messages>
            <summary status="invalid"/>
          </xml>
          <messages></messages>
          <summary status="invalid"/>
        </validation>""";

    // Der Gesamt-summary meldet "valid", obwohl der pdf-Teil "invalid" ist (im Spike beobachtet).
    public static final String PDF_INVALID = """
        <?xml version="1.0" encoding="UTF-8"?>
        <validation filename="z.pdf" datetime="2027-01-15 10:00:00">
          <pdf>ValidationResult [flavour=3u, totalAssertions=503, assertions=[TestAssertion [ruleId=RuleId [specification=ISO 19005-3:2012, clause=6.2.11.4.1, testNumber=1], status=failed, message=The font programs for all fonts used for rendering within a conforming file shall be embedded within that file, as defined in ISO 32000-1:2008, 9.9, location=Location [level=CosDocument, context=root/document[0]/pages[0]/font[0](Helvetica)], locationContext=null, errorMessage=The font program is not embedded]], isCompliant=false]
            <info><signature>Mustang</signature><duration unit="ms">1160</duration></info>
            <summary status="invalid"/>
          </pdf>
          <xml>
            <info><version>2</version><profile>urn:cen.eu:en16931:2017</profile><validator version="2.26.0"/></info>
            <messages>
              <notice type="27" location="/rsm:CrossIndustryInvoice" criterion="x">Business process MUST be provided. [ID PEPPOL-EN16931-R001] from /xslt/x.xslt)</notice>
            </messages>
            <summary status="valid"/>
          </xml>
          <messages></messages>
          <summary status="valid"/>
        </validation>""";

    public static final String BOTH_VALID = XML_VALID.replace("<xml>",
        "<pdf>ValidationResult [flavour=3u, totalAssertions=503, assertions=[], isCompliant=true]<summary status=\"valid\"/></pdf><xml>");

    @Test
    void validXmlPasses() {
        var r = ValidationReportParser.parse(XML_VALID);
        assertThat(r.xmlValid()).isTrue();
        assertThat(r.pdfValid()).isNull();
        assertThat(r.passed()).isTrue();
        assertThat(r.validatorVersion()).isEqualTo("2.26.0");
        assertThat(r.profile()).contains("xrechnung_3.0");
    }

    @Test
    void invalidXmlFailsAndReportsRuleId() {
        var r = ValidationReportParser.parse(XML_INVALID);
        assertThat(r.passed()).isFalse();
        assertThat(r.errors()).hasSize(1);
        assertThat(r.errors().get(0).ruleId()).isEqualTo("PEPPOL-EN16931-R010");
        assertThat(r.findings()).extracting(Finding::severity).contains("ERROR", "NOTICE");
    }

    @Test
    void invalidPdfPartFailsDespiteValidOverallSummary() {
        var r = ValidationReportParser.parse(PDF_INVALID);
        assertThat(r.xmlValid()).isTrue();
        assertThat(r.pdfValid()).isFalse();
        assertThat(r.passed()).isFalse();
        assertThat(r.errors()).anyMatch(f -> f.ruleId().contains("6.2.11.4.1") && f.message().contains("not embedded"));
    }

    @Test
    void bothPartsValidPass() {
        var r = ValidationReportParser.parse(BOTH_VALID);
        assertThat(r.pdfValid()).isTrue();
        assertThat(r.passed()).isTrue();
    }

    @Test
    void unparsableReportFails() {
        var r = ValidationReportParser.parse("<kaputt");
        assertThat(r.passed()).isFalse();
        assertThat(r.errors()).hasSize(1);
    }

    @Test
    void reportWithoutAnyPartFails() {
        var r = ValidationReportParser.parse("<validation filename=\"x\"><summary status=\"valid\"/></validation>");
        assertThat(r.passed()).isFalse();
    }
}
