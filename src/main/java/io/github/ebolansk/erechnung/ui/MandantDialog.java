// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import io.github.ebolansk.erechnung.extract.MandantDraft;
import io.github.ebolansk.erechnung.mandant.Mandant;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;

public final class MandantDialog extends JDialog {
    private final MandantPanel panel;
    private Optional<Mandant> result = Optional.empty();

    /** Neuer Mandant, vorbelegt aus dem PDF-Text. */
    public MandantDialog(Window owner, MandantDraft draft) {
        this(owner, draft, null);
    }

    /** Neuer Mandant, vorbelegt aus dem PDF-Text, mit KI-Hilfe (ai: die Rechnung, aus der gelesen wird). */
    MandantDialog(Window owner, MandantDraft draft, MandantAi ai) {
        super(owner, "Neuer Rechnungsaussteller erkannt", ModalityType.APPLICATION_MODAL);
        this.panel = new MandantPanel(draft, ai);
        build("Anlegen");
    }

    /** Vorhandenen Mandanten bearbeiten. */
    public MandantDialog(Window owner, Mandant existing) {
        this(owner, existing, null);
    }

    /** Vorhandenen Mandanten bearbeiten, mit KI-Hilfe (liest aus einer Rechnung, die der Nutzer wählt). */
    MandantDialog(Window owner, Mandant existing, MandantAi ai) {
        super(owner, "Rechnungsaussteller bearbeiten", ModalityType.APPLICATION_MODAL);
        this.panel = new MandantPanel(existing, ai);
        build("Speichern");
    }

    private void build(String okText) {
        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
        JButton ok = new JButton(okText);
        JButton cancel = new JButton("Abbrechen");
        ok.addActionListener(e -> panel.collect().ifPresent(m -> {
            result = Optional.of(m);
            dispose();
        }));
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(ok);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(ok);
        pack();
        setLocationRelativeTo(getOwner());
    }

    public Optional<Mandant> showAndGet() {
        setVisible(true);
        return result;
    }
}
