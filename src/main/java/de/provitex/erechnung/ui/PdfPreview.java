// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

/** Zeigt die Seiten des PDFs als Bild an. */
final class PdfPreview extends JPanel {
    private static final float DPI = 100f;
    private final PDDocument doc;
    private final PDFRenderer renderer;
    private final JLabel image = new JLabel("", SwingConstants.CENTER);
    private final JLabel pageLabel = new JLabel();
    private int page;

    PdfPreview(byte[] pdf) throws IOException {
        super(new BorderLayout());
        this.doc = Loader.loadPDF(pdf);
        this.renderer = new PDFRenderer(doc);
        JButton prev = new JButton("◀");
        JButton next = new JButton("▶");
        prev.addActionListener(e -> show(page - 1));
        next.addActionListener(e -> show(page + 1));
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bar.add(prev);
        bar.add(pageLabel);
        bar.add(next);
        add(bar, BorderLayout.NORTH);
        add(new JScrollPane(image), BorderLayout.CENTER);
        show(0);
    }

    private void show(int index) {
        int pages = doc.getNumberOfPages();
        if (index < 0 || index >= pages) {
            return;
        }
        page = index;
        pageLabel.setText("Seite " + (page + 1) + " von " + pages);
        try {
            BufferedImage img = renderer.renderImageWithDPI(page, DPI);
            image.setIcon(new ImageIcon(img));
            image.setText("");
        } catch (IOException e) {
            image.setIcon(null);
            image.setText("Vorschau nicht verfügbar: " + e.getMessage());
        }
    }

    void close() {
        try {
            doc.close();
        } catch (IOException ignored) {
            // Beim Schließen nichts zu retten.
        }
    }
}
