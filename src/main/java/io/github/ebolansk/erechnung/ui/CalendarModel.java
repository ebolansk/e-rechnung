// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Rechenlogik des Kalenders: das Raster eines Monats (Wochen ab Montag) und die Monatsüberschrift. */
final class CalendarModel {
    static final String[] WEEKDAYS = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};

    private CalendarModel() {
    }

    /** 42 Tage (6 Wochen), beginnend mit dem Montag der Woche, in der der Monat anfängt. */
    static List<LocalDate> grid(YearMonth month) {
        LocalDate first = month.atDay(1);
        LocalDate start = first.minusDays(first.getDayOfWeek().getValue() - 1L);
        List<LocalDate> days = new ArrayList<>(42);
        for (int i = 0; i < 42; i++) {
            days.add(start.plusDays(i));
        }
        return days;
    }

    static String title(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL, Locale.GERMAN) + " " + month.getYear();
    }
}
