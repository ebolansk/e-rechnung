// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;

/** Kleine Vektor-Icons für die Menüs, in einem 16×16-Raster gezeichnet (ohne Bilddateien, scharf bei jeder Skalierung). */
final class MenuIcons {
    enum Kind { FILE, SETTINGS, EXIT, ARCHIVE, SEARCH, INTEGRITY, PEOPLE, HELP, UPDATE, INFO, NOTICE, TRASH, FOLDER, CALENDAR, PDF, PROTOCOL,
        CHEVRON_LEFT, CHEVRON_RIGHT, DOUBLE_LEFT, DOUBLE_RIGHT, MINUS, PLUS, FIT }

    private static final Color BLUE = new Color(0x1F4E79);
    private static final Color GREEN = new Color(0x2E9E4F);
    private static final Color LIGHT = new Color(0xBFD4EA);

    private MenuIcons() {
    }

    static Icon of(Kind kind) {
        return new Vector(kind, 16);
    }

    /** Dasselbe Icon in anderer Größe (zum Beispiel 20 Pixel für Knöpfe ohne Text). */
    static Icon of(Kind kind, int size) {
        return new Vector(kind, size);
    }

    private record Vector(Kind kind, int size) implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.translate(x, y);
                g2.scale(size / 16.0, size / 16.0);
                g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(BLUE);
                draw(g2, kind);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    private static void page(Graphics2D g) {
        Path2D p = new Path2D.Double();
        p.moveTo(3, 1.5);
        p.lineTo(10, 1.5);
        p.lineTo(13, 4.5);
        p.lineTo(13, 14.5);
        p.lineTo(3, 14.5);
        p.closePath();
        g.setColor(Color.WHITE);
        g.fill(p);
        g.setColor(BLUE);
        g.draw(p);
        g.draw(new Line2D.Double(10, 1.5, 10, 4.5));
        g.draw(new Line2D.Double(10, 4.5, 13, 4.5));
    }

    /** Pfeilspitze (Chevron) mit der Spitze bei tipX; direction -1 zeigt nach links, 1 nach rechts. */
    private static void chevron(Graphics2D g, double tipX, int direction) {
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D p = new Path2D.Double();
        p.moveTo(tipX - direction * 4, 3.5);
        p.lineTo(tipX, 8);
        p.lineTo(tipX - direction * 4, 12.5);
        g.draw(p);
    }

    private static void circle(Graphics2D g, double x, double y, double d, boolean fill) {
        Ellipse2D e = new Ellipse2D.Double(x, y, d, d);
        if (fill) {
            g.fill(e);
        } else {
            g.draw(e);
        }
    }

    private static void draw(Graphics2D g, Kind kind) {
        switch (kind) {
            case FILE -> {
                page(g);
                g.draw(new Line2D.Double(5.5, 8, 10.5, 8));
                g.draw(new Line2D.Double(5.5, 10.8, 10.5, 10.8));
            }
            case NOTICE -> {
                page(g);
                g.draw(new Line2D.Double(8, 6.5, 8, 10));
                circle(g, 7.2, 11.6, 1.6, true);
            }
            case SETTINGS -> {
                double[][] bars = {{4, 10}, {8, 5}, {12, 11}};
                for (double[] b : bars) {
                    g.draw(new Line2D.Double(2, b[0], 14, b[0]));
                    g.setColor(Color.WHITE);
                    circle(g, b[1] - 2, b[0] - 2, 4, true);
                    g.setColor(BLUE);
                    circle(g, b[1] - 2, b[0] - 2, 4, false);
                }
            }
            case EXIT -> {
                Path2D door = new Path2D.Double();
                door.moveTo(7, 2);
                door.lineTo(3, 2);
                door.lineTo(3, 14);
                door.lineTo(7, 14);
                g.draw(door);
                g.draw(new Line2D.Double(6.5, 8, 13, 8));
                Path2D head = new Path2D.Double();
                head.moveTo(10, 5);
                head.lineTo(13, 8);
                head.lineTo(10, 11);
                g.draw(head);
            }
            case ARCHIVE -> {
                g.setColor(LIGHT);
                g.fill(new RoundRectangle2D.Double(2.5, 5.5, 11, 8.5, 2, 2));
                g.setColor(BLUE);
                g.draw(new RoundRectangle2D.Double(2.5, 5.5, 11, 8.5, 2, 2));
                g.setColor(Color.WHITE);
                g.fill(new RoundRectangle2D.Double(1.5, 2, 13, 3.5, 1.5, 1.5));
                g.setColor(BLUE);
                g.draw(new RoundRectangle2D.Double(1.5, 2, 13, 3.5, 1.5, 1.5));
                g.draw(new Line2D.Double(6.5, 9, 9.5, 9));
            }
            case SEARCH -> {
                circle(g, 2.2, 2.2, 8, false);
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(9, 9, 13.5, 13.5));
            }
            case INTEGRITY -> {
                Path2D shield = new Path2D.Double();
                shield.moveTo(8, 1.5);
                shield.lineTo(13.5, 3.5);
                shield.lineTo(13, 9);
                shield.lineTo(8, 14.5);
                shield.lineTo(3, 9);
                shield.lineTo(2.5, 3.5);
                shield.closePath();
                g.setColor(LIGHT);
                g.fill(shield);
                g.setColor(BLUE);
                g.draw(shield);
                g.setColor(GREEN);
                g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D tick = new Path2D.Double();
                tick.moveTo(5.5, 8);
                tick.lineTo(7.5, 10);
                tick.lineTo(10.8, 6);
                g.draw(tick);
            }
            case PEOPLE -> {
                g.setColor(LIGHT);
                circle(g, 8.8, 3.2, 4, true);
                g.setColor(BLUE);
                circle(g, 8.8, 3.2, 4, false);
                g.draw(new Arc2D.Double(8, 9, 6.5, 8, 0, 180, Arc2D.OPEN));
                circle(g, 3.2, 2.4, 4.4, false);
                g.setColor(LIGHT);
                circle(g, 3.2, 2.4, 4.4, true);
                g.setColor(BLUE);
                circle(g, 3.2, 2.4, 4.4, false);
                g.draw(new Arc2D.Double(1.6, 8.6, 7.8, 9, 0, 180, Arc2D.OPEN));
            }
            case HELP -> {
                circle(g, 1.5, 1.5, 13, false);
                Path2D q = new Path2D.Double();
                q.moveTo(5.8, 6.3);
                q.curveTo(5.8, 3.4, 10.2, 3.4, 10.2, 6.1);
                q.curveTo(10.2, 7.9, 8, 7.7, 8, 9.6);
                g.draw(q);
                circle(g, 7.2, 11.2, 1.6, true);
            }
            case INFO -> {
                circle(g, 1.5, 1.5, 13, false);
                circle(g, 7.2, 4, 1.6, true);
                g.draw(new Line2D.Double(8, 7, 8, 11.8));
            }
            case UPDATE -> {
                g.draw(new Arc2D.Double(3, 3, 10, 10, 60, 270, Arc2D.OPEN));
                Path2D head = new Path2D.Double();
                head.moveTo(13.4, 8.6);
                head.lineTo(13.9, 11.4);
                head.lineTo(10.8, 9.6);
                head.closePath();
                g.fill(head);
            }
            case FOLDER -> {
                Path2D folder = new Path2D.Double();
                folder.moveTo(1.5, 3.5);
                folder.lineTo(6.2, 3.5);
                folder.lineTo(7.8, 5.5);
                folder.lineTo(14.5, 5.5);
                folder.lineTo(14.5, 13.5);
                folder.lineTo(1.5, 13.5);
                folder.closePath();
                g.setColor(LIGHT);
                g.fill(folder);
                g.setColor(BLUE);
                g.draw(folder);
                g.draw(new Line2D.Double(1.5, 7.8, 14.5, 7.8));
            }
            case CALENDAR -> {
                RoundRectangle2D body = new RoundRectangle2D.Double(2, 3, 12, 11, 2.5, 2.5);
                g.setColor(Color.WHITE);
                g.fill(body);
                g.setColor(BLUE);
                g.draw(body);
                g.fill(new RoundRectangle2D.Double(2, 3, 12, 3.6, 2.5, 2.5));
                g.draw(new Line2D.Double(5, 1.5, 5, 4.2));
                g.draw(new Line2D.Double(11, 1.5, 11, 4.2));
                for (double y : new double[] {8.2, 11}) {
                    for (double x : new double[] {4.4, 7.2, 10}) {
                        g.fill(new RoundRectangle2D.Double(x, y, 1.7, 1.7, 0.6, 0.6));
                    }
                }
            }
            case PDF -> {
                page(g);
                g.setColor(new Color(0xC83C3C));
                g.fill(new RoundRectangle2D.Double(4.6, 8.4, 7, 4, 1.2, 1.2));
            }
            case PROTOCOL -> {
                page(g);
                g.setColor(GREEN);
                g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (double y : new double[] {6.4, 10.4}) {
                    Path2D tick = new Path2D.Double();
                    tick.moveTo(5, y);
                    tick.lineTo(6.2, y + 1.3);
                    tick.lineTo(8.2, y - 1);
                    g.draw(tick);
                }
                g.setColor(BLUE);
                g.draw(new Line2D.Double(9.4, 7.2, 11.2, 7.2));
                g.draw(new Line2D.Double(9.4, 11.2, 11.2, 11.2));
            }
            case CHEVRON_LEFT -> chevron(g, 9.5, -1);
            case CHEVRON_RIGHT -> chevron(g, 6.5, 1);
            case DOUBLE_LEFT -> {
                chevron(g, 6.5, -1);
                chevron(g, 11.5, -1);
            }
            case DOUBLE_RIGHT -> {
                chevron(g, 4.5, 1);
                chevron(g, 9.5, 1);
            }
            case MINUS -> {
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(3.5, 8, 12.5, 8));
            }
            case PLUS -> {
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(3.5, 8, 12.5, 8));
                g.draw(new Line2D.Double(8, 3.5, 8, 12.5));
            }
            case FIT -> {
                g.draw(new RoundRectangle2D.Double(2.5, 2, 11, 12, 2, 2));
                g.draw(new Line2D.Double(5, 8, 11, 8));
                Path2D left = new Path2D.Double();
                left.moveTo(6.6, 6.2);
                left.lineTo(5, 8);
                left.lineTo(6.6, 9.8);
                g.draw(left);
                Path2D right = new Path2D.Double();
                right.moveTo(9.4, 6.2);
                right.lineTo(11, 8);
                right.lineTo(9.4, 9.8);
                g.draw(right);
            }
            case TRASH -> {
                g.draw(new Line2D.Double(2.5, 4, 13.5, 4));
                g.draw(new Line2D.Double(6, 4, 6, 2));
                g.draw(new Line2D.Double(6, 2, 10, 2));
                g.draw(new Line2D.Double(10, 2, 10, 4));
                Path2D bin = new Path2D.Double();
                bin.moveTo(3.8, 4.5);
                bin.lineTo(4.6, 14);
                bin.lineTo(11.4, 14);
                bin.lineTo(12.2, 4.5);
                g.setColor(LIGHT);
                g.fill(bin);
                g.setColor(BLUE);
                g.draw(bin);
                g.draw(new Line2D.Double(6.6, 6.5, 6.8, 12));
                g.draw(new Line2D.Double(9.4, 6.5, 9.2, 12));
            }
            default -> {
            }
        }
    }
}
