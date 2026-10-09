// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.ui;

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
