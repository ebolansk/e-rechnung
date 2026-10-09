// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.mandant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MandantMatcherTest {
    static Mandant m(String id, String vat, String tax, String iban) {
        return new Mandant(id, "Firma " + id, "Str. 1", "72760", "Reutlingen", "DE", vat, tax,
            id + "@x.de", "Max", "0711", iban, "", "");
    }

    @Test
    void matchesByVatIdIgnoringSpaces() {
        var a = m("a", "DE123456789", "", "");
        var b = m("b", "DE987654321", "", "");
        assertThat(MandantMatcher.match(List.of(a, b), "USt-IdNr.: DE 987 654 321\nRechnung")).contains(b);
    }

    @Test
    void matchesByIbanAndTaxNumber() {
        var a = m("a", "", "12345/67890", "");
        var b = m("b", "", "", "DE02120300000000202051");
        assertThat(MandantMatcher.match(List.of(a, b), "IBAN DE02 1203 0000 0000 2020 51")).contains(b);
        assertThat(MandantMatcher.match(List.of(a, b), "St.-Nr. 12345/67890")).contains(a);
    }

    @Test
    void emptyIdentifiersNeverMatch() {
        var blank = m("blank", "", "", "");
        assertThat(MandantMatcher.match(List.of(blank), "irgendein Text")).isEmpty();
    }

    @Test
    void moreHitsWin() {
        var a = m("a", "DE111111111", "", "");
        var b = m("b", "DE222222222", "", "DE02120300000000202051");
        String text = "DE111111111 und DE222222222 und IBAN DE02120300000000202051";
        assertThat(MandantMatcher.match(List.of(a, b), text)).contains(b);
    }

    @Test
    void noMatchReturnsEmpty() {
        assertThat(MandantMatcher.match(List.of(m("a", "DE111111111", "", "")), "kein Treffer")).isEmpty();
    }
}
