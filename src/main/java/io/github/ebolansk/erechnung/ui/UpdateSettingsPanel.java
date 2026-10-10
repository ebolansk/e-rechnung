// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import io.github.ebolansk.erechnung.update.UpdateSettings;
import io.github.ebolansk.erechnung.update.UpdateSettingsStore;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 * Im Konfigurations-Tab „Allgemein“ (Abschnitt „Updates“): nur die Checkbox „Beim Start nach Updates suchen“, sie wirkt sofort.
 * Repository, Token und signingKey (nur für ein privates Repository oder einen Fork) bleiben in daten/update.json und werden hier
 * nicht angefasst.
 */
public final class UpdateSettingsPanel extends JPanel {
    private final UpdateSettingsStore store;
    private final JCheckBox onStart = new JCheckBox("Beim Start nach Updates suchen");

    public UpdateSettingsPanel(UpdateSettingsStore store) {
        super(new BorderLayout());
        this.store = store;
        onStart.setToolTipText("Fragt höchstens einmal täglich bei GitHub nach der neuesten Version und weist nur darauf hin. "
            + "GitHub sieht dabei Ihre IP-Adresse. Installiert wird nichts ohne Ihre Bestätigung.");
        onStart.setSelected(store.load().checkOnStart());
        onStart.addActionListener(e -> save());
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(onStart);
        add(row, BorderLayout.NORTH);
    }

    private void save() {
        UpdateSettings current = store.load();
        try {
            store.save(current.withCheckOnStart(onStart.isSelected()));
        } catch (IOException e) {
            onStart.setSelected(current.checkOnStart());
            JOptionPane.showMessageDialog(this, "Die Einstellung konnte nicht gespeichert werden:\n" + e.getMessage(), "Update",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    boolean checkOnStartSelected() {
        return onStart.isSelected();
    }
}
