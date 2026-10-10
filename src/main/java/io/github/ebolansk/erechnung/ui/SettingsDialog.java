// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import io.github.ebolansk.erechnung.config.ConfigStore;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;

public final class SettingsDialog extends JDialog {
    public SettingsDialog(Window owner, Path home, ConfigStore config) {
        this(owner, home, config, java.util.Map.of());
    }

    public SettingsDialog(Window owner, Path home, ConfigStore config, java.util.Map<String, JPanel> extraTabs) {
        this(owner, home, config, extraTabs, null);
    }

    /** updateRow: wird im Tab „Allgemein“ unter „Updates“ eingebaut. */
    public SettingsDialog(Window owner, Path home, ConfigStore config, java.util.Map<String, JPanel> extraTabs, JPanel updateRow) {
        super(owner, "Konfiguration", ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout());
        SettingsPanel panel = new SettingsPanel(home, config, updateRow);
        extraTabs.forEach(panel::addTab);
        add(panel, BorderLayout.CENTER);
        JButton close = new JButton("Schließen");
        close.addActionListener(e -> dispose());
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bar.add(close);
        add(bar, BorderLayout.SOUTH);
        pack();
        // Nie größer als der Bildschirm, nie so klein, dass Inhalt abgeschnitten wird (Tabs scrollen bei Bedarf).
        Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(getWidth() + 20, screen.width * 9 / 10), Math.min(getHeight() + 20, screen.height * 9 / 10));
        setLocationRelativeTo(owner);
    }
}
