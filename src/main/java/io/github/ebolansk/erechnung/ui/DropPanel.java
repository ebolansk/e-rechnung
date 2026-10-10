// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Schmale Drop-Zeile für PDF-Rechnungen: Hinweis und Auswahl-Knopf in einer Zeile (abgelegt werden kann im ganzen Fenster). */
final class DropPanel extends JPanel {
    DropPanel(Consumer<List<File>> onFiles) {
        super(new BorderLayout(12, 0));
        JLabel text = new JLabel("PDF-Rechnungen hier oder irgendwo im Fenster ablegen (auch mehrere)");
        text.setForeground(new Color(70, 70, 70));
        add(text, BorderLayout.CENTER);
        JButton choose = new JButton("Dateien auswählen…");
        choose.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setMultiSelectionEnabled(true);
            fc.setFileFilter(new FileNameExtensionFilter("PDF-Dateien", "pdf"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                onFiles.accept(List.of(fc.getSelectedFiles()));
            }
        });
        add(choose, BorderLayout.EAST);
        setTransferHandler(new FileDropHandler(onFiles));
        setBorder(new javax.swing.border.EmptyBorder(8, 16, 8, 12));
        setBackground(new Color(248, 250, 252));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(120, 144, 180));
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {6f, 4f}, 0f));
        g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 12, 12);
        g2.dispose();
    }
}
