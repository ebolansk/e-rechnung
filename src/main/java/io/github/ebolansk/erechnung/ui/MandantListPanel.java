// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import io.github.ebolansk.erechnung.mandant.Mandant;
import io.github.ebolansk.erechnung.mandant.MandantStore;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.IOException;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/** Mandanten (Rechnungsaussteller) anlegen, bearbeiten und entfernen. */
public final class MandantListPanel extends JPanel {
    private final MandantStore mandantStore;
    private final Component dialogParent;
    private final DefaultListModel<Mandant> mandantModel = new DefaultListModel<>();
    private final io.github.ebolansk.erechnung.service.AiAssistService ai;

    public MandantListPanel(MandantStore mandantStore, Component dialogParent) {
        this(mandantStore, dialogParent, null);
    }

    /** ai (optional): KI-Hilfe in den Dialogen zum Anlegen und Bearbeiten (liest aus einer vom Nutzer gewählten Rechnung). */
    public MandantListPanel(MandantStore mandantStore, Component dialogParent, io.github.ebolansk.erechnung.service.AiAssistService ai) {
        super(new BorderLayout());
        this.ai = ai;
        this.mandantStore = mandantStore;
        this.dialogParent = dialogParent;
        build();
        reloadMandanten();
    }

    private void build() {
        JList<Mandant> list = new JList<>(mandantModel);
        list.setCellRenderer((l, m, i, sel, focus) -> {
            JLabel c = new JLabel(m.name() + "  (" + (m.vatId().isBlank() ? "St.-Nr. " + m.taxNumber() : m.vatId()) + ")");
            c.setOpaque(true);
            c.setBackground(sel ? l.getSelectionBackground() : l.getBackground());
            c.setForeground(sel ? l.getSelectionForeground() : l.getForeground());
            return c;
        });
        JButton add = new JButton("Anlegen…");
        JButton edit = new JButton("Bearbeiten…");
        JButton remove = new JButton("Entfernen…");
        add.addActionListener(e -> {
            var draft = new io.github.ebolansk.erechnung.extract.MandantDraft("", "", "", "", "", "", "", "", "", "");
            new MandantDialog(owner(), draft, ai == null ? null : new MandantAi(ai, null, null)).showAndGet().ifPresent(m -> {
                try {
                    mandantStore.add(m);
                    reloadMandanten();
                } catch (IOException ex) {
                    error(ex);
                }
            });
        });
        edit.addActionListener(e -> {
            Mandant sel = list.getSelectedValue();
            if (sel != null) {
                new MandantDialog(owner(), sel, ai == null ? null : new MandantAi(ai, null, null)).showAndGet().ifPresent(m -> {
                    try {
                        mandantStore.update(m);
                        reloadMandanten();
                    } catch (IOException ex) {
                        error(ex);
                    }
                });
            }
        });
        remove.addActionListener(e -> {
            Mandant sel = list.getSelectedValue();
            if (sel != null && JOptionPane.showConfirmDialog(this,
                "Mandant „" + sel.name() + "“ entfernen?\nArchivierte Rechnungen bleiben unverändert erhalten.",
                "Mandant entfernen", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                try {
                    mandantStore.remove(sel.id());
                    reloadMandanten();
                } catch (IOException ex) {
                    error(ex);
                }
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(add);
        buttons.add(edit);
        buttons.add(remove);
        add(new JLabel("Rechnungsaussteller. Neue Aussteller werden beim Verarbeiten erkannt und nach Bestätigung angelegt."),
            BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    private void reloadMandanten() {
        mandantModel.clear();
        mandantStore.all().forEach(mandantModel::addElement);
    }

    private Window owner() {
        return SwingUtilities.getWindowAncestor(dialogParent != null ? dialogParent : this);
    }

    private void error(Exception e) {
        JOptionPane.showMessageDialog(this, e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
    }
}
