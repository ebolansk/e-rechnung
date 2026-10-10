// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.table.TableColumn;

/**
 * Spalten einer Tabelle ein- und ausblenden: Rechtsklick auf die Kopfzeile öffnet die Auswahl. Die Spalten behalten ihre
 * Reihenfolge; die Wahl wird über {@link ColumnVisibility} gemerkt. Spalten aus {@code always} bleiben immer sichtbar.
 */
final class ColumnChooser {
    private final JTable table;
    private final ColumnVisibility visibility;
    private final Set<String> always;
    private final List<TableColumn> all = new ArrayList<>();

    ColumnChooser(JTable table, ColumnVisibility visibility, Set<String> always) {
        this.table = table;
        this.visibility = visibility;
        this.always = always;
        all.addAll(Collections.list(table.getColumnModel().getColumns()));
        apply();
        table.getTableHeader().setToolTipText("Rechtsklick: Spalten ein- und ausblenden");
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                show(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                show(e);
            }
        });
    }

    private void show(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        JPopupMenu popup = new JPopupMenu();
        for (TableColumn col : all) {
            String name = String.valueOf(col.getIdentifier());
            if (always.contains(name)) {
                continue;
            }
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(name, !visibility.isHidden(name));
            item.addActionListener(ev -> setVisible(name, item.isSelected()));
            popup.add(item);
        }
        popup.addSeparator();
        JMenuItem showAll = new JMenuItem("Alle Spalten anzeigen");
        showAll.addActionListener(ev -> {
            visibility.showAll();
            apply();
        });
        popup.add(showAll);
        popup.show(table.getTableHeader(), e.getX(), e.getY());
    }

    void setVisible(String column, boolean visible) {
        if (always.contains(column)) {
            return;
        }
        visibility.setHidden(column, !visible);
        apply();
    }

    private void apply() {
        var model = table.getColumnModel();
        for (TableColumn col : all) {
            model.removeColumn(col);
        }
        for (TableColumn col : all) {
            String name = String.valueOf(col.getIdentifier());
            if (always.contains(name) || !visibility.isHidden(name)) {
                model.addColumn(col);
            }
        }
    }
}
