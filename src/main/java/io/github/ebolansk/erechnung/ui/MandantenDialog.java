// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import io.github.ebolansk.erechnung.mandant.MandantStore;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;

/** Eigener Dialog „Mandanten“ (Menü Mandanten). */
public final class MandantenDialog extends JDialog {
    public MandantenDialog(Window owner, MandantStore mandanten) {
        this(owner, mandanten, null);
    }

    public MandantenDialog(Window owner, MandantStore mandanten, io.github.ebolansk.erechnung.service.AiAssistService ai) {
        super(owner, "Mandanten", ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout());
        MandantListPanel panel = new MandantListPanel(mandanten, this, ai);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        add(panel, BorderLayout.CENTER);
        JButton close = new JButton("Schließen");
        close.addActionListener(e -> dispose());
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bar.add(close);
        add(bar, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(620, 420));
        pack();
        setLocationRelativeTo(owner);
    }
}
