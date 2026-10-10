// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import io.github.ebolansk.erechnung.Version;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Das Bild des Startbildschirms (Splash). Es wird beim Paketbau als app/splash.png erzeugt und von der JVM über
 * {@code -splash:} sofort gezeigt, noch bevor das Programm geladen ist. Den Fortschrittsbalken zeichnet {@link Splash}.
 */
public final class SplashImage {
    public static final int WIDTH = 520;
    public static final int HEIGHT = 260;

    private SplashImage() {
    }

    public static BufferedImage render() {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRoundRect(0, 0, WIDTH, HEIGHT, 18, 18);
            g.setColor(new Color(0x1F4E79));
            g.setStroke(new java.awt.BasicStroke(2f));
            g.drawRoundRect(1, 1, WIDTH - 3, HEIGHT - 3, 18, 18);
            g.drawImage(AppIcon.draw(112), 36, 36, null);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            g.drawString("E-Rechnung-Tool", 170, 92);
            g.setColor(new Color(0x5A6B7B));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            g.drawString("XRechnung und ZUGFeRD erzeugen", 172, 120);
            g.drawString("Version " + Version.TOOL, 172, 144);
        } finally {
            g.dispose();
        }
        return img;
    }

    /** Aufruf beim Paketbau: schreibt das PNG in die angegebene Datei. */
    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");
        ImageIO.write(render(), "png", new File(args[0]));
    }
}
