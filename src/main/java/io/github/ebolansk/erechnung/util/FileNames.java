// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

public final class FileNames {
    private static final int MAX_LENGTH = 80;
    private static final Set<String> RESERVED = Set.of("CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private FileNames() {
    }

    /** Liefert ein einzelnes, auf Windows und Linux gültiges Pfadsegment. */
    public static String sanitize(String raw) {
        if (raw == null) {
            return "_";
        }
        String s = raw.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
            .replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue").replace("ß", "ss");
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s.replaceAll("[^A-Za-z0-9 ._\\-]", "_");
        s = s.replaceAll("^[ .]+", "").replaceAll("[ .]+$", "");
        if (s.isEmpty()) {
            return "_";
        }
        if (s.length() > MAX_LENGTH) {
            s = s.substring(0, MAX_LENGTH).replaceAll("[ .]+$", "");
        }
        int dot = s.indexOf('.');
        String base = dot >= 0 ? s.substring(0, dot) : s;
        if (RESERVED.contains(base.toUpperCase(Locale.ROOT))) {
            s = base + "_" + (dot >= 0 ? s.substring(dot) : "");
        }
        return s.isEmpty() ? "_" : s;
    }
}
