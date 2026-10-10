// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * Zeigt die Seiten des PDFs als Bild an: zuerst auf die Breite des Fensterbereichs eingepasst (100 %). Das Mausrad zoomt (um die
 * Stelle unter dem Mauszeiger), ebenso die Knöpfe; mit gedrückter Maustaste lässt sich der Ausschnitt verschieben (Hand),
 * Umschalt+Mausrad scrollt senkrecht.
 */
final class PdfPreview extends JPanel {
    private static final float DEFAULT_SCALE = 100f / 72f;
    private static final float MAX_SCALE = 6f;
    private static final float MIN_ZOOM = 0.4f;
    private static final float MAX_ZOOM = 4f;
    private static final float STEP = 1.25f;
    private static final int MIN_WIDTH = 200;
    private final PDDocument doc;
    private final PDFRenderer renderer;
    private final JLabel image = new JLabel("", SwingConstants.CENTER);
    private final JLabel pageLabel = new JLabel();
    private final JLabel zoomLabel = new JLabel("100 %");
    private final JScrollPane scroll;
    private int page;
    private int renderedWidth;
    private float zoom = 1f;

    PdfPreview(byte[] pdf) throws IOException {
        super(new BorderLayout());
        this.doc = Loader.loadPDF(pdf);
        this.renderer = new PDFRenderer(doc);
        JButton prev = iconButton(MenuIcons.Kind.CHEVRON_LEFT, "Vorige Seite");
        JButton next = iconButton(MenuIcons.Kind.CHEVRON_RIGHT, "Nächste Seite");
        prev.addActionListener(e -> show(page - 1));
        next.addActionListener(e -> show(page + 1));
        JButton out = iconButton(MenuIcons.Kind.MINUS, "Verkleinern (Mausrad)");
        JButton in = iconButton(MenuIcons.Kind.PLUS, "Vergrößern (Mausrad)");
        JButton fit = iconButton(MenuIcons.Kind.FIT, "Seite auf die Breite einpassen");
        out.addActionListener(e -> zoomOut());
        in.addActionListener(e -> zoomIn());
        fit.addActionListener(e -> fit());
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        nav.add(prev);
        nav.add(pageLabel);
        nav.add(next);
        JPanel zoomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        zoomBar.add(out);
        zoomBar.add(zoomLabel);
        zoomBar.add(in);
        zoomBar.add(fit);
        JPanel bar = new JPanel(new BorderLayout());
        bar.add(nav, BorderLayout.WEST);
        bar.add(zoomBar, BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);

        scroll = new JScrollPane(image, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setWheelScrollingEnabled(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        image.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        image.setToolTipText("Mausrad: zoomen · Maustaste gedrückt halten: verschieben · Umschalt + Mausrad: scrollen");
        MouseAdapter drag = new MouseAdapter() {
            private Point last;

            @Override
            public void mousePressed(MouseEvent e) {
                last = e.getLocationOnScreen();
                image.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (last == null) {
                    return;
                }
                Point now = e.getLocationOnScreen();
                panBy(last.x - now.x, last.y - now.y);
                last = now;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                last = null;
                image.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
        };
        image.addMouseListener(drag);
        image.addMouseMotionListener(drag);
        MouseWheelListener wheel = this::onWheel;
        image.addMouseWheelListener(wheel);
        scroll.addMouseWheelListener(wheel);
        add(scroll, BorderLayout.CENTER);
        show(0);
    }

    /** Mausrad: zoomt um die Stelle unter dem Mauszeiger (nach oben vergrößern); mit Umschalt scrollt es senkrecht. */
    void onWheel(MouseWheelEvent e) {
        if (e.isShiftDown()) {
            panBy(0, (int) Math.round(e.getPreciseWheelRotation() * 48));
        } else {
            Point anchor = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), scroll.getViewport());
            zoomTo(zoom * (float) Math.pow(STEP, -e.getPreciseWheelRotation()), anchor);
        }
    }

    void zoomIn() {
        zoomTo(zoom * STEP, null);
    }

    void zoomOut() {
        zoomTo(zoom / STEP, null);
    }

    /** Zurück auf die eingepasste Seitenbreite (100 %). */
    void fit() {
        zoomTo(1f, null);
    }

    int zoomPercent() {
        return Math.round(zoom * 100);
    }

    /** Verschiebt den sichtbaren Ausschnitt um dx/dy Pixel (die Hand-Funktion), begrenzt auf die Seite. */
    void panBy(int dx, int dy) {
        JViewport vp = scroll.getViewport();
        Point p = vp.getViewPosition();
        int maxX = Math.max(0, vp.getView().getWidth() - vp.getExtentSize().width);
        int maxY = Math.max(0, vp.getView().getHeight() - vp.getExtentSize().height);
        vp.setViewPosition(new Point(Math.max(0, Math.min(maxX, p.x + dx)), Math.max(0, Math.min(maxY, p.y + dy))));
    }

    Point viewPosition() {
        return scroll.getViewport().getViewPosition();
    }

    int imageWidth() {
        return image.getIcon() == null ? 0 : image.getIcon().getIconWidth();
    }

    /**
     * Ändert die Vergrößerung und hält dabei den Ankerpunkt (Mauszeiger, sonst die Mitte des sichtbaren Ausschnitts) an derselben
     * Stelle der Seite.
     */
    private void zoomTo(float target, Point anchor) {
        float newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, target));
        if (Math.abs(newZoom - zoom) < 0.001f) {
            return;
        }
        JViewport vp = scroll.getViewport();
        double oldW = Math.max(1, vp.getView().getWidth());
        double oldH = Math.max(1, vp.getView().getHeight());
        double ax = anchor != null ? anchor.x : vp.getExtentSize().width / 2.0;
        double ay = anchor != null ? anchor.y : vp.getExtentSize().height / 2.0;
        double cx = (vp.getViewPosition().x + ax) / oldW;
        double cy = (vp.getViewPosition().y + ay) / oldH;
        zoom = newZoom;
        zoomLabel.setText(zoomPercent() + " %");
        show(page);
        scroll.validate();
        int nx = (int) Math.round(cx * vp.getView().getWidth() - ax);
        int ny = (int) Math.round(cy * vp.getView().getHeight() - ay);
        vp.setViewPosition(new Point(0, 0));
        panBy(nx, ny);
    }

    /** Knopf nur mit gezeichnetem Symbol (Zeichen wie ◀ oder − fehlen in manchen Windows-Schriften und erscheinen als Kästchen). */
    private static JButton iconButton(MenuIcons.Kind kind, String tooltip) {
        JButton b = new JButton(MenuIcons.of(kind));
        b.setToolTipText(tooltip);
        b.getAccessibleContext().setAccessibleName(tooltip);
        b.setMargin(new java.awt.Insets(2, 6, 2, 6));
        return b;
    }

    private void show(int index) {
        int pages = doc.getNumberOfPages();
        if (index < 0 || index >= pages) {
            return;
        }
        page = index;
        pageLabel.setText("Seite " + (page + 1) + " von " + pages);
        try {
            BufferedImage img = renderer.renderImage(page, scale());
            image.setIcon(new ImageIcon(img));
            renderedWidth = availableWidth();
            image.setText("");
        } catch (IOException e) {
            image.setIcon(null);
            image.setText("Vorschau nicht verfügbar: " + e.getMessage());
        }
    }

    /** Verfügbare Breite für die Seite: Bereich der Bildlaufleiste ohne Rand und senkrechte Leiste. */
    private int availableWidth() {
        return scroll.getWidth() - 4 - UIManager.getInt("ScrollBar.width");
    }

    /** Maßstab: eingepasste Seitenbreite mal Vergrößerung (Seitenbreite = 100 %), nach oben begrenzt. */
    private float scale() {
        int width = availableWidth();
        float base = DEFAULT_SCALE;
        if (width >= MIN_WIDTH) {
            float pageWidth = doc.getPage(page).getCropBox().getWidth();
            base = Math.min(width / pageWidth, 2.5f);
        }
        return Math.min(base * zoom, MAX_SCALE);
    }

    @Override
    public void doLayout() {
        super.doLayout();
        int width = availableWidth();
        if (width >= MIN_WIDTH && Math.abs(width - renderedWidth) > 12) {
            show(page);
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
