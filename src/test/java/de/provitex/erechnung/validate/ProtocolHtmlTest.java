// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.validate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProtocolHtmlTest {
    private ProtocolHtml.Input input(String reportXml, String number) {
        return new ProtocolHtml.Input("ausgabe-xrechnung.xml", "XRechnung", "Muster GmbH", number,
            ValidationReportParser.parse(reportXml), Instant.parse("2027-01-15T10:00:00Z"));
    }

    @Test
    void passedReportIsMarkedPassed() {
        String html = ProtocolHtml.render(input(ValidationReportParserTest.XML_VALID, "RE-1"));
        assertThat(html).contains("BESTANDEN").doesNotContain("NICHT BESTANDEN");
        assertThat(html).contains("Prüf-Protokoll").contains("Muster GmbH").contains("RE-1");
        assertThat(html).contains("inhaltliche Richtigkeit");
    }

    @Test
    void failedReportListsRuleAndMessage() {
        String html = ProtocolHtml.render(input(ValidationReportParserTest.XML_INVALID, "RE-2"));
        assertThat(html).contains("NICHT BESTANDEN").contains("PEPPOL-EN16931-R010");
    }

    @Test
    void untrustedTextIsEscaped() {
        String html = ProtocolHtml.render(input(ValidationReportParserTest.XML_VALID, "<script>alert(1)</script>"));
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
    }
}
