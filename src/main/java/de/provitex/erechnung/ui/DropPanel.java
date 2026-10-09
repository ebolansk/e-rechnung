// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Drop-Zone für PDF-Rechnungen. */
final class DropPanel extends JPanel {
    DropPanel(Consumer<List<File>> onFiles) {
        super(new BorderLayout());
        JLabel text = new JLabel("<html><div style='text-align:center'>PDF-Rechnungen hier ablegen<br>"
            + "oder Dateien auswählen</div></html>", SwingConstants.CENTER);
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
        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER));
        south.setOpaque(false);
        south.add(choose);
        add(south, BorderLayout.SOUTH);
        setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport s) {
                return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport s) {
                if (!canImport(s)) {
                    return false;
                }
                try {
                    onFiles.accept((List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor));
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            }
        });
        setBorder(new javax.swing.border.EmptyBorder(10, 10, 22, 10));
        setBackground(new Color(248, 250, 252));
        setPreferredSize(new java.awt.Dimension(400, 170));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(120, 144, 180));
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {8f, 6f}, 0f));
        g2.drawRoundRect(8, 8, getWidth() - 17, getHeight() - 17, 18, 18);
        g2.dispose();
    }
}
