// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import java.awt.Component;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;

/** Zeigt statt des Pfads ein Symbol: Ordner für archivierte Rechnungen, Blatt für erzeugte Entwürfe. Der Pfad steht im Hilfetext. */
final class FolderCellRenderer implements TableCellRenderer {
    private static final Icon FOLDER = MenuIcons.of(MenuIcons.Kind.FOLDER);
    private static final Icon DRAFT = MenuIcons.of(MenuIcons.Kind.FILE);
    private final JLabel label = new JLabel("", SwingConstants.CENTER);
    private final int statusModelColumn;

    FolderCellRenderer(int statusModelColumn) {
        this.statusModelColumn = statusModelColumn;
        label.setOpaque(true);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
        String path = value == null ? "" : value.toString();
        String status = String.valueOf(table.getModel().getValueAt(table.convertRowIndexToModel(row), statusModelColumn));
        label.setIcon(path.isBlank() ? null : OverviewRules.READY.equals(status) ? DRAFT : FOLDER);
        label.setBackground(selected ? table.getSelectionBackground() : table.getBackground());
        return label;
    }
}
