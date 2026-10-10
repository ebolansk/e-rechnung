// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.time.LocalDate;
import java.time.YearMonth;
import javax.swing.JButton;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class CalendarTest {
    private static JButton find(Component c, java.util.function.Predicate<JButton> match) {
        if (c instanceof JButton b && match.test(b)) {
            return b;
        }
        if (c instanceof Container k) {
            for (Component child : k.getComponents()) {
                JButton b = find(child, match);
                if (b != null) {
                    return b;
                }
            }
        }
        return null;
    }

    @Test
    void monthGridStartsOnMondayHasSixWeeksAndCoversTheWholeMonth() {
        var grid = CalendarModel.grid(YearMonth.of(2026, 10));
        assertThat(grid).hasSize(42);
        assertThat(grid.get(0)).as("1.10.2026 ist ein Donnerstag, das Raster beginnt am Montag davor").isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(grid.get(0).getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
        assertThat(grid).contains(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        assertThat(CalendarModel.grid(YearMonth.of(2027, 2)).get(0)).as("Februar 2027 beginnt an einem Montag").isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(CalendarModel.title(YearMonth.of(2026, 10))).isEqualTo("Oktober 2026");
    }

    @Test
    void clickingADayWritesTheDateAndNavigationChangesTheMonth() {
        JTextField field = new JTextField("01.10.2026");
        var popup = new CalendarPopup(field);
        assertThat(popup.shownMonth()).isEqualTo(YearMonth.of(2026, 10));
        find(popup, b -> "Nächster Monat".equals(b.getToolTipText())).doClick();
        assertThat(popup.shownMonth()).isEqualTo(YearMonth.of(2026, 11));
        find(popup, b -> "Voriges Jahr".equals(b.getToolTipText())).doClick();
        assertThat(popup.shownMonth()).isEqualTo(YearMonth.of(2025, 11));
        find(popup, b -> "2025-11-15".equals(b.getName())).doClick();
        assertThat(field.getText()).isEqualTo("15.11.2025");
        find(popup, b -> "Löschen".equals(b.getText())).doClick();
        assertThat(field.getText()).isEmpty();
        find(popup, b -> "Heute".equals(b.getText())).doClick();
        assertThat(field.getText()).isEqualTo(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy").format(LocalDate.now()));
    }

    @Test
    void emptyOrInvalidFieldStartsAtTodaysMonth() {
        assertThat(new CalendarPopup(new JTextField("")).shownMonth()).isEqualTo(YearMonth.now());
        assertThat(new CalendarPopup(new JTextField("kein datum")).shownMonth()).isEqualTo(YearMonth.now());
    }

    @Test
    void dateRowKeepsTheFieldAndAddsACalendarButton() {
        JTextField field = new JTextField();
        var row = Ui.dateRow(field);
        assertThat(field.getParent()).isSameAs(row);
        assertThat(find(row, b -> "Kalender öffnen".equals(b.getToolTipText()))).isNotNull();
    }

    @Test
    void renderCalendarToAnImage() throws Exception {
        var popup = new CalendarPopup(new JTextField("15.10.2026"));
        popup.setSize(popup.getPreferredSize());
        UiSmokeTest.layoutDeep(popup);
        var img = new java.awt.image.BufferedImage(popup.getWidth(), popup.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        popup.paint(g);
        g.dispose();
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/ui-snapshots"));
        javax.imageio.ImageIO.write(img, "png", java.nio.file.Path.of("target/ui-snapshots/kalender.png").toFile());
    }
}
