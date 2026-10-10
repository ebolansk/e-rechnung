// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.SplashScreen;
import java.awt.geom.RoundRectangle2D;

/**
 * Fortschrittsbalken auf dem Startbildschirm. Gibt es keinen Splash (zum Beispiel in Tests oder wenn das Programm ohne
 * {@code -splash:} gestartet wird), tun alle Aufrufe nichts.
 */
public final class Splash {
    private static final int MARGIN = 36;

    private Splash() {
    }

    /** Zeigt den Fortschritt (0 bis 100) mit einem kurzen Text unter dem Logo. */
    public static void step(int percent, String text) {
        SplashScreen splash;
        try {
            splash = SplashScreen.getSplashScreen();
        } catch (RuntimeException e) {
            return;
        }
        if (splash == null || !splash.isVisible()) {
            return;
        }
        try {
            Graphics2D g = splash.createGraphics();
            try {
                int w = splash.getSize().width;
                int h = splash.getSize().height;
                int barY = h - 44;
                int barW = w - 2 * MARGIN;
                g.setComposite(AlphaComposite.Clear);
                g.fillRect(MARGIN - 2, barY - 28, barW + 4, 50);
                g.setComposite(AlphaComposite.SrcOver);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(new Color(0x5A6B7B));
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
                g.drawString(text, MARGIN, barY - 8);
                g.setColor(new Color(0xDCE6F1));
                g.fill(new RoundRectangle2D.Float(MARGIN, barY, barW, 10, 10, 10));
                g.setColor(new Color(0x2E9E4F));
                g.fill(new RoundRectangle2D.Float(MARGIN, barY, Math.max(10, barW * Math.min(100, Math.max(0, percent)) / 100f), 10, 10, 10));
            } finally {
                g.dispose();
            }
            splash.update();
        } catch (IllegalStateException e) {
            // Der Splash wurde schon geschlossen: kein Fortschritt mehr nötig.
        }
    }

    public static void close() {
        try {
            SplashScreen splash = SplashScreen.getSplashScreen();
            if (splash != null) {
                splash.close();
            }
        } catch (RuntimeException e) {
            // schon geschlossen
        }
    }
}
