// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.ui;

import de.provitex.erechnung.extract.MandantDraft;
import de.provitex.erechnung.mandant.Mandant;
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
        super(owner, "Neuer Rechnungsaussteller erkannt", ModalityType.APPLICATION_MODAL);
        this.panel = new MandantPanel(draft);
        build("Anlegen");
    }

    /** Vorhandenen Mandanten bearbeiten. */
    public MandantDialog(Window owner, Mandant existing) {
        super(owner, "Rechnungsaussteller bearbeiten", ModalityType.APPLICATION_MODAL);
        this.panel = new MandantPanel(existing);
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
