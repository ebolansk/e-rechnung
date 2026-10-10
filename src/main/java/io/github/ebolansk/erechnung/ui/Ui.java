// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import java.awt.Color;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

/** Kleine Hilfen für die Formulare. */
public final class Ui {
    static final Color BAD = new Color(255, 214, 214);

    private Ui() {
    }

    static void onChange(JTextComponent c, Runnable r) {
        c.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                r.run();
            }
        });
    }

    static void mark(JTextComponent c, boolean bad) {
        c.setBackground(bad ? BAD : UIManager.getColor("TextField.background"));
    }

    /** Hält ein Formular auf Breite des sichtbaren Bereichs, damit nie horizontal gescrollt werden muss. */
    static final class TrackingPanel extends JPanel implements Scrollable {
        TrackingPanel(JComponent content) {
            super(new BorderLayout());
            add(content, BorderLayout.NORTH);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 100;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /**
     * Setzt alle Schriften des Look-and-Feel auf Familie und Größe der Menüschrift (nur Fett/Kursiv bleibt je Komponente). Das
     * Windows-Design nimmt sonst Menüs, Beschriftungen, Tabellen und Felder aus verschiedenen Windows-Systemschriften, und die
     * Oberfläche zeigt mehrere Größen nebeneinander.
     */
    public static void unifyFonts() {
        java.awt.Font base = UIManager.getFont("Menu.font");
        if (base == null) {
            base = UIManager.getFont("Label.font");
        }
        if (base == null) {
            return;
        }
        var defaults = UIManager.getDefaults();
        for (Object key : java.util.Collections.list(defaults.keys())) {
            if (key instanceof String name && name.toLowerCase(java.util.Locale.ROOT).endsWith("font")
                && defaults.get(key) instanceof java.awt.Font f) {
                UIManager.put(key, new javax.swing.plaf.FontUIResource(
                    new java.awt.Font(base.getFamily(), f.getStyle(), base.getSize())));
            }
        }
    }

    /** Ein Datumsfeld mit Kalender-Knopf daneben; das Textfeld selbst bleibt unverändert (TT.MM.JJJJ per Hand oder aus dem Kalender). */
    static javax.swing.JPanel dateRow(javax.swing.JTextField field) {
        javax.swing.JButton button = new javax.swing.JButton(MenuIcons.of(MenuIcons.Kind.CALENDAR));
        button.setToolTipText("Kalender öffnen");
        button.setMargin(new Insets(1, 4, 1, 4));
        button.setFocusable(false);
        button.addActionListener(e -> new CalendarPopup(field).show(button, 0, button.getHeight()));
        javax.swing.JPanel row = new javax.swing.JPanel(new java.awt.BorderLayout(4, 0));
        row.setOpaque(false);
        row.add(field, java.awt.BorderLayout.CENTER);
        row.add(button, java.awt.BorderLayout.EAST);
        return row;
    }

    static final class Form {
        private final JPanel panel = new JPanel(new GridBagLayout());
        private int row;

        Form() {
            panel.setBorder(new EmptyBorder(6, 8, 6, 8));
        }

        Form section(String title) {
            JLabel l = new JLabel(title);
            l.setFont(l.getFont().deriveFont(Font.BOLD));
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.gridwidth = 2;
            c.anchor = GridBagConstraints.WEST;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            c.insets = new Insets(10, 0, 4, 0);
            panel.add(l, c);
            return this;
        }

        Form row(String label, JComponent field) {
            GridBagConstraints a = new GridBagConstraints();
            a.gridx = 0;
            a.gridy = row;
            a.anchor = GridBagConstraints.NORTHWEST;
            a.insets = new Insets(2, 0, 2, 8);
            panel.add(new JLabel(label), a);
            GridBagConstraints b = new GridBagConstraints();
            b.gridx = 1;
            b.gridy = row++;
            b.weightx = 1;
            b.fill = GridBagConstraints.HORIZONTAL;
            b.insets = new Insets(2, 0, 2, 0);
            panel.add(field, b);
            return this;
        }

        Form wide(JComponent comp) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.gridwidth = 2;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(2, 0, 2, 0);
            panel.add(comp, c);
            return this;
        }

        JPanel build() {
            GridBagConstraints filler = new GridBagConstraints();
            filler.gridx = 0;
            filler.gridy = row;
            filler.weighty = 1;
            JPanel space = new JPanel();
            space.setPreferredSize(new Dimension(1, 1));
            panel.add(space, filler);
            return panel;
        }
    }
}
