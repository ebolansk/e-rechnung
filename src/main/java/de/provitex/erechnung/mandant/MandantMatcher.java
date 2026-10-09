// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.mandant;

import de.provitex.erechnung.util.GermanFormats;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class MandantMatcher {
    private MandantMatcher() {
    }

    public static Optional<Mandant> match(List<Mandant> mandanten, String pdfText) {
        String compact = compact(pdfText);
        Mandant best = null;
        int bestScore = 0;
        for (Mandant m : mandanten) {
            int score = 0;
            score += hit(compact, compact(m.vatId()));
            score += hit(compact, compact(GermanFormats.normalizeIban(m.iban())));
            score += hit(compact, compact(m.taxNumber()));
            if (score > bestScore) {
                best = m;
                bestScore = score;
            }
        }
        return Optional.ofNullable(best);
    }

    private static int hit(String haystack, String needle) {
        return !needle.isEmpty() && haystack.contains(needle) ? 1 : 0;
    }

    private static String compact(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
