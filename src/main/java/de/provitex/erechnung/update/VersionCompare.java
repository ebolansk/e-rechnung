// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.update;

public final class VersionCompare {
    private VersionCompare() {
    }

    /** true, wenn candidate (z. B. "v0.2.0") neuer ist als current ("0.1.0"). Nicht lesbare Nummern gelten nie als neuer. */
    public static boolean isNewer(String candidate, String current) {
        int[] a = parse(candidate);
        int[] b = parse(current);
        if (a == null || b == null) {
            return false;
        }
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    public static String normalize(String tag) {
        String t = tag == null ? "" : tag.trim();
        return t.startsWith("v") || t.startsWith("V") ? t.substring(1) : t;
    }

    private static int[] parse(String v) {
        String t = normalize(v);
        if (!t.matches("\\d+(\\.\\d+){0,3}")) {
            return null;
        }
        String[] parts = t.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return out;
    }
}
