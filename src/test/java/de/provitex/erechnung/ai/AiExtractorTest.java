// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.provitex.erechnung.extract.ExtractionResult.PrintedTotals;
import java.io.IOException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AiExtractorTest {
    private static final String ANSWER = """
        ```json
        {"number":"RE-77","issueDate":"2027-03-05","deliveryDate":"2027-03-01","dueDate":"2027-02-01",
         "currency":"eur","iban":"DE89370400440532013000",
         "buyer":{"name":"Kunde AG","street":"Weg 1","zip":"70173","city":"Stuttgart","country":"de",
                  "vatId":"DE 123 456 789","email":"kein-mail"},
         "items":[{"name":"Beratung","quantity":2,"unitPrice":"100.00","vatPercent":19},
                  {"name":"Rabatt","quantity":-1,"unitPrice":20,"vatPercent":19,"vatCategory":"S"},
                  {"name":"","quantity":1,"unitPrice":5,"vatPercent":19}],
         "totals":{"net":180.00,"tax":34.20,"gross":214.20}}
        ```""";

    @Test
    void parsesFencedJsonIntoGermanFieldsAndItems() throws Exception {
        AiSuggestion s = AiExtractor.parse(ANSWER, new PrintedTotals(new BigDecimal("180.00"), null, null));
        assertThat(s.fields()).containsEntry("number", "RE-77").containsEntry("issue", "05.03.2027")
            .containsEntry("delivery", "01.03.2027").containsEntry("currency", "EUR")
            .containsEntry("bCountry", "DE").containsEntry("bVat", "DE123456789");
        assertThat(s.items()).hasSize(2);
        assertThat(s.items().get(0).unitPrice()).isEqualByComparingTo("100.00");
        assertThat(s.items().get(0).vatCategory()).isEqualTo("S");
        assertThat(s.items().get(1).quantity()).isEqualByComparingTo("-1");
        assertThat(s.iban()).isEqualTo("DE89370400440532013000");
    }

    @Test
    void flagsImplausibleValuesInsteadOfHidingThem() throws Exception {
        AiSuggestion s = AiExtractor.parse(ANSWER, new PrintedTotals(new BigDecimal("180.00"), null, null));
        assertThat(s.warnings()).containsKey("bEmail").containsKey("due");
        assertThat(s.warnings().get("items")).contains("Position 3 ist unvollständig");
        assertThat(s.warnings()).doesNotContainKeys("bVat", "issue", "currency");
    }

    @Test
    void warnsWhenItemSumDiffersFromPrintedTotal() throws Exception {
        AiSuggestion s = AiExtractor.parse(ANSWER, new PrintedTotals(new BigDecimal("999.00"), null, null));
        assertThat(s.warnings().get("items")).contains("im PDF gelesenen Netto-Summe");
    }

    @Test
    void dropsIbanWithWrongChecksumAndSaysSo() throws Exception {
        AiSuggestion s = AiExtractor.parse("{\"iban\":\"DE89370400440532013001\",\"items\":[]}", null);
        assertThat(s.iban()).isEmpty();
        assertThat(s.notes()).anyMatch(n -> n.contains("IBAN"));
        assertThat(AiExtractor.validIban("DE89370400440532013000")).isTrue();
        assertThat(AiExtractor.validIban("DE00")).isFalse();
    }

    @Test
    void unreadableAnswerIsAnError() {
        assertThatThrownBy(() -> AiExtractor.parse("Das kann ich leider nicht.", null)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> AiExtractor.parse("[1,2]", null)).isInstanceOf(IOException.class);
    }

    @Test
    void unparsableDateKeepsRawValueWithWarning() throws Exception {
        AiSuggestion s = AiExtractor.parse("{\"issueDate\":\"gestern\",\"items\":[]}", null);
        assertThat(s.fields()).containsEntry("issue", "gestern");
        assertThat(s.warnings()).containsKey("issue");
    }
}
