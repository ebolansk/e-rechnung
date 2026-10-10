// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

/** Zeigt den Status einer PDF in der Übersicht als Fortschrittsbalken mit dem Statustext. */
final class ProgressCellRenderer implements TableCellRenderer {
    private final JProgressBar bar = new JProgressBar(0, 100);

    ProgressCellRenderer() {
        bar.setStringPainted(true);
        bar.setBorderPainted(false);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
        String status = value == null ? "" : value.toString();
        bar.setValue(OverviewRules.percent(status));
        bar.setString(status);
        bar.setForeground(switch (OverviewRules.kind(status)) {
            case ACTIVE -> new Color(70, 130, 200);
            case READY -> new Color(230, 150, 40);
            case DONE -> new Color(46, 158, 79);
            case NEUTRAL -> new Color(130, 150, 170);
            case CANCELLED -> new Color(160, 160, 160);
            case FAILED -> new Color(200, 70, 70);
        });
        return bar;
    }
}
