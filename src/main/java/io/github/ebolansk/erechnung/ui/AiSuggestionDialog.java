// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/** Zeigt den KI-Vorschlag feldweise: Regelwert, KI-Wert, Hinweis. Der Nutzer wählt, was er übernimmt. */
public final class AiSuggestionDialog extends JDialog {
    public record Row(String key, String label, String ruleValue, String aiValue, String warning) {
    }

    private final List<Row> rows;
    private final boolean[] apply;
    private boolean accepted;

    public AiSuggestionDialog(Window owner, List<Row> rows, List<String> notes) {
        super(owner, "KI-Vorschlag prüfen", ModalityType.APPLICATION_MODAL);
        this.rows = rows;
        this.apply = new boolean[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            apply[i] = rows.get(i).warning().isEmpty();
        }
        JTable table = new JTable(new Model());
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setMaxWidth(80);
        table.getColumnModel().getColumn(0).setPreferredWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(220);
        table.getColumnModel().getColumn(3).setPreferredWidth(220);
        table.getColumnModel().getColumn(4).setPreferredWidth(300);
        table.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, focus, r, c);
                comp.setForeground(sel ? t.getSelectionForeground() : new Color(176, 0, 32));
                return comp;
            }
        });
        String head = "<html><div style='width:760px'>Die KI hat diese Abweichungen zu den eingelesenen Werten vorgeschlagen. "
            + "Es wird nur übernommen, was angehakt ist. Felder mit Hinweis sind nicht vorausgewählt.";
        if (!notes.isEmpty()) {
            head += "<br><i>" + String.join(" ", notes) + "</i>";
        }
        JButton ok = new JButton("Ausgewählte übernehmen");
        JButton cancel = new JButton("Verwerfen");
        ok.addActionListener(e -> {
            accepted = true;
            dispose();
        });
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(ok);
        setLayout(new BorderLayout(0, 6));
        add(new JLabel(head + "</div></html>"), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(1000, 480));
        pack();
        setLocationRelativeTo(owner);
    }

    /** Liefert die gewählten Zeilen; leer bei „Verwerfen“. */
    public List<Row> showAndGet() {
        setVisible(true);
        List<Row> chosen = new ArrayList<>();
        if (accepted) {
            for (int i = 0; i < rows.size(); i++) {
                if (apply[i]) {
                    chosen.add(rows.get(i));
                }
            }
        }
        return chosen;
    }

    private final class Model extends AbstractTableModel {
        private final String[] cols = {"Übernehmen", "Feld", "Eingelesen (Regeln)", "Vorschlag der KI", "Plausibilität"};

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public String getColumnName(int c) {
            return cols[c];
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return c == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int r, int c) {
            return c == 0;
        }

        @Override
        public Object getValueAt(int r, int c) {
            Row row = rows.get(r);
            return switch (c) {
                case 0 -> apply[r];
                case 1 -> row.label();
                case 2 -> row.ruleValue();
                case 3 -> row.aiValue();
                default -> row.warning();
            };
        }

        @Override
        public void setValueAt(Object v, int r, int c) {
            if (c == 0) {
                apply[r] = Boolean.TRUE.equals(v);
                fireTableCellUpdated(r, c);
            }
        }
    }
}
