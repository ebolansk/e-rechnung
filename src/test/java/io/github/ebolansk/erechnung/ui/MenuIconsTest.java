// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import org.junit.jupiter.api.Test;

class MenuIconsTest {
    @Test
    void everyIconDrawsSomethingAndSheetIsWritten() throws Exception {
        MenuIcons.Kind[] kinds = MenuIcons.Kind.values();
        int scale = 6;
        BufferedImage sheet = new BufferedImage(kinds.length * 16 * scale + 16, 16 * scale + 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int i = 0; i < kinds.length; i++) {
            Icon icon = MenuIcons.of(kinds[i]);
            assertThat(icon.getIconWidth()).isEqualTo(16);
            BufferedImage one = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g1 = one.createGraphics();
            icon.paintIcon(null, g1, 0, 0);
            g1.dispose();
            int painted = 0;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    if ((one.getRGB(x, y) >>> 24) > 0) {
                        painted++;
                    }
                }
            }
            assertThat(painted).as(kinds[i].name()).isGreaterThan(12);
            Graphics2D gs = (Graphics2D) g.create(8 + i * 16 * scale, 8, 16 * scale, 16 * scale);
            gs.scale(scale, scale);
            icon.paintIcon(null, gs, 0, 0);
            gs.dispose();
        }
        g.dispose();
        Files.createDirectories(Path.of("target/ui-snapshots"));
        ImageIO.write(sheet, "png", Path.of("target/ui-snapshots/menu-icons.png").toFile());
    }
}
