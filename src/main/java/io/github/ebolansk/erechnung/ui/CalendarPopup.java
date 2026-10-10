// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import io.github.ebolansk.erechnung.util.GermanFormats;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

/**
 * Kalender zum Auswählen eines Datums: Monatsraster ab Montag, Blättern nach Monat und Jahr, „Heute“ und „Löschen“. Ein Klick auf
 * einen Tag trägt das Datum als TT.MM.JJJJ in das Textfeld ein. Der Monat beginnt beim Datum des Feldes, sonst bei heute.
 */
final class CalendarPopup extends JPopupMenu {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private final JTextField field;
    private final JLabel title = new JLabel("", SwingConstants.CENTER);
    private final JPanel days = new JPanel(new GridLayout(7, 7, 2, 2));
    private YearMonth shown;
    private LocalDate selected;

    CalendarPopup(JTextField field) {
        this.field = field;
        this.selected = parse(field.getText());
        this.shown = YearMonth.from(selected != null ? selected : LocalDate.now());

        JPanel nav = new JPanel(new BorderLayout(4, 0));
        JPanel left = new JPanel(new GridLayout(1, 2, 2, 0));
        left.add(navButton(MenuIcons.Kind.DOUBLE_LEFT, "Voriges Jahr", () -> shown = shown.minusYears(1)));
        left.add(navButton(MenuIcons.Kind.CHEVRON_LEFT, "Voriger Monat", () -> shown = shown.minusMonths(1)));
        JPanel right = new JPanel(new GridLayout(1, 2, 2, 0));
        right.add(navButton(MenuIcons.Kind.CHEVRON_RIGHT, "Nächster Monat", () -> shown = shown.plusMonths(1)));
        right.add(navButton(MenuIcons.Kind.DOUBLE_RIGHT, "Nächstes Jahr", () -> shown = shown.plusYears(1)));
        nav.add(left, BorderLayout.WEST);
        nav.add(title, BorderLayout.CENTER);
        nav.add(right, BorderLayout.EAST);

        JPanel footer = new JPanel(new GridLayout(1, 2, 6, 0));
        JButton today = new JButton("Heute");
        today.addActionListener(e -> pick(LocalDate.now()));
        JButton clear = new JButton("Löschen");
        clear.addActionListener(e -> {
            field.setText("");
            setVisible(false);
        });
        footer.add(today);
        footer.add(clear);

        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(nav, BorderLayout.NORTH);
        content.add(days, BorderLayout.CENTER);
        content.add(footer, BorderLayout.SOUTH);
        add(content);
        rebuild();
    }

    private JButton navButton(MenuIcons.Kind icon, String tip, Runnable change) {
        JButton b = new JButton(MenuIcons.of(icon));
        b.setToolTipText(tip);
        b.getAccessibleContext().setAccessibleName(tip);
        b.setMargin(new Insets(2, 6, 2, 6));
        b.addActionListener(e -> {
            change.run();
            rebuild();
        });
        return b;
    }

    private static LocalDate parse(String text) {
        try {
            return text == null || text.isBlank() ? null : GermanFormats.parseDate(text.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void rebuild() {
        title.setText(CalendarModel.title(shown));
        days.removeAll();
        for (String d : CalendarModel.WEEKDAYS) {
            JLabel l = new JLabel(d, SwingConstants.CENTER);
            l.setForeground(Color.GRAY);
            days.add(l);
        }
        LocalDate today = LocalDate.now();
        for (LocalDate d : CalendarModel.grid(shown)) {
            JButton b = new JButton(String.valueOf(d.getDayOfMonth()));
            b.setMargin(new Insets(2, 0, 2, 0));
            b.setFocusable(false);
            b.setName(d.toString());
            boolean inMonth = YearMonth.from(d).equals(shown);
            if (!inMonth) {
                b.setForeground(Color.GRAY);
            }
            if (d.equals(selected)) {
                b.setBackground(new Color(0xBFD4EA));
            }
            if (d.equals(today)) {
                b.setBorder(BorderFactory.createLineBorder(new Color(0x1F4E79), 2));
            }
            b.addActionListener(e -> pick(d));
            days.add(b);
        }
        days.revalidate();
        days.repaint();
        pack();
    }

    private void pick(LocalDate date) {
        field.setText(DATE.format(date));
        setVisible(false);
    }

    /** Für Tests: der angezeigte Monat. */
    YearMonth shownMonth() {
        return shown;
    }
}
