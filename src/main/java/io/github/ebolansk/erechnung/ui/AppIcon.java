// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/** Fenster-Icon: ein Rechnungsblatt mit Eselsohr und grünem Prüfhaken, in mehreren Größen gezeichnet (ohne Bilddateien). */
final class AppIcon {
    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    private AppIcon() {
    }

    static List<Image> images() {
        return java.util.Arrays.stream(SIZES).mapToObj(AppIcon::draw).map(Image.class::cast).toList();
    }

    static BufferedImage draw(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(size / 256.0, size / 256.0);
            Color blue = new Color(0x1F4E79);

            // Blatt mit abgeschnittener Ecke oben rechts
            Path2D page = new Path2D.Double();
            page.moveTo(44, 16);
            page.lineTo(158, 16);
            page.lineTo(212, 70);
            page.lineTo(212, 232);
            page.quadTo(212, 240, 204, 240);
            page.lineTo(44, 240);
            page.quadTo(36, 240, 36, 232);
            page.lineTo(36, 24);
            page.quadTo(36, 16, 44, 16);
            page.closePath();
            g.setColor(Color.WHITE);
            g.fill(page);
            g.setColor(blue);
            g.setStroke(new BasicStroke(12, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(page);

            // Eselsohr
            Path2D fold = new Path2D.Double();
            fold.moveTo(158, 16);
            fold.lineTo(158, 70);
            fold.lineTo(212, 70);
            g.setColor(new Color(0xBFD4EA));
            g.fill(fold);
            g.setColor(blue);
            g.draw(fold);

            // Kopfband und Textzeilen
            g.setColor(blue);
            g.fill(new RoundRectangle2D.Double(62, 92, 74, 18, 8, 8));
            g.setColor(new Color(0x9AAEC4));
            g.fill(new RoundRectangle2D.Double(62, 128, 128, 14, 7, 7));
            g.fill(new RoundRectangle2D.Double(62, 156, 128, 14, 7, 7));
            g.fill(new RoundRectangle2D.Double(62, 184, 78, 14, 7, 7));

            // Prüfhaken auf grünem Kreis
            g.setColor(new Color(0x2E9E4F));
            g.fillOval(132, 150, 112, 112);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(16, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D tick = new Path2D.Double();
            tick.moveTo(158, 208);
            tick.lineTo(182, 232);
            tick.lineTo(222, 182);
            g.draw(tick);
        } finally {
            g.dispose();
        }
        return img;
    }
}
