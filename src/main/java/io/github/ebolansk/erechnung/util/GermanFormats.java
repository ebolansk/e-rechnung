// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GermanFormats {
    private static final Map<String, Integer> MONTHS = Map.ofEntries(
        Map.entry("januar", 1), Map.entry("februar", 2), Map.entry("märz", 3), Map.entry("maerz", 3),
        Map.entry("april", 4), Map.entry("mai", 5), Map.entry("juni", 6), Map.entry("juli", 7),
        Map.entry("august", 8), Map.entry("september", 9), Map.entry("oktober", 10),
        Map.entry("november", 11), Map.entry("dezember", 12));
    private static final Pattern DOTTED = Pattern.compile("^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{2}|\\d{4})$");
    private static final Pattern ISO = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");
    private static final Pattern WORDS = Pattern.compile("^(\\d{1,2})\\.?\\s*(\\p{L}+)\\s+(\\d{4})$");
    /** Punkt als Tausendertrenner, wenn nur Punkte vorkommen: nie bei führender 0 ("0.125" ist ein Dezimalwert, nicht 125). */
    private static final Pattern THOUSANDS_ONLY = Pattern.compile("^-?[1-9]\\d{0,2}(\\.\\d{3})+$");
    private static final Pattern MINUS_LIKE = Pattern.compile("[\\u2212\\u2012\\u2013](?=\\d)");
    private static final Pattern AMOUNT_SHAPE = Pattern.compile("^-?[0-9.,]*[0-9][0-9.,]*$");

    private GermanFormats() {
    }

    /** Typografisches Minus (U+2212, Gedankenstrich) direkt vor einer Ziffer wird zum ASCII-Minus; sonst ginge das Vorzeichen verloren. */
    public static String normalizeMinus(String text) {
        return text == null ? null : MINUS_LIKE.matcher(text).replaceAll("-");
    }

    /**
     * Liest einen Betrag in deutscher oder englischer Schreibweise. Erlaubt sind nur Ziffern, Punkt, Komma, ein Vorzeichen
     * (vorn oder hinten), Leerzeichen und Währungsangabe (€, EUR); alles andere wird abgelehnt, statt fremde Ziffern in den
     * Betrag zu kleben.
     */
    public static BigDecimal parseAmount(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Betrag fehlt");
        }
        String s = normalizeMinus(raw).replaceAll("(?i)EUR|€|[\\s\\u00A0\\u202F]", "");
        if (s.endsWith("-") && s.length() > 1) {
            s = "-" + s.substring(0, s.length() - 1);
        }
        if (!AMOUNT_SHAPE.matcher(s).matches()) {
            throw new IllegalArgumentException("Kein Betrag: " + raw);
        }
        int comma = s.lastIndexOf(',');
        int dot = s.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            if (comma > dot) {
                s = s.replace(".", "").replace(',', '.');
            } else {
                s = s.replace(",", "");
            }
        } else if (comma >= 0) {
            s = s.replace(".", "").replace(',', '.');
        } else if (dot >= 0 && THOUSANDS_ONLY.matcher(s).matches()) {
            s = s.replace(".", "");
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Kein Betrag: " + raw, e);
        }
    }

    public static LocalDate parseDate(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Datum fehlt");
        }
        String s = raw.trim();
        try {
            Matcher m = DOTTED.matcher(s);
            if (m.matches()) {
                int year = Integer.parseInt(m.group(3));
                if (m.group(3).length() == 2) {
                    year += 2000;
                }
                return LocalDate.of(year, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
            }
            m = ISO.matcher(s);
            if (m.matches()) {
                return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            }
            m = WORDS.matcher(s);
            if (m.matches()) {
                Integer month = MONTHS.get(m.group(2).toLowerCase(Locale.GERMAN));
                if (month != null) {
                    return LocalDate.of(Integer.parseInt(m.group(3)), month, Integer.parseInt(m.group(1)));
                }
            }
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Ungültiges Datum: " + raw, e);
        }
        throw new IllegalArgumentException("Unbekanntes Datumsformat: " + raw);
    }

    public static String normalizeIban(String raw) {
        return raw == null ? "" : raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    /** IBAN-Prüfsumme nach ISO 7064 (Modulo 97); Leerzeichen und Kleinschreibung werden ignoriert. */
    public static boolean isValidIban(String raw) {
        String iban = normalizeIban(raw);
        if (!iban.matches("^[A-Z]{2}\\d{2}[A-Z0-9]{11,30}$")) {
            return false;
        }
        String r = iban.substring(4) + iban.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : r.toCharArray()) {
            digits.append(Character.isDigit(c) ? String.valueOf(c) : String.valueOf(c - 'A' + 10));
        }
        return new java.math.BigInteger(digits.toString()).mod(java.math.BigInteger.valueOf(97)).intValue() == 1;
    }

    public static String formatAmount(BigDecimal value) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.GERMANY));
        return f.format(value);
    }
}
