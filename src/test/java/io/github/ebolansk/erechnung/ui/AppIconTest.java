// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class AppIconTest {
    @Test
    void drawsVisibleIconInAllSizes() throws Exception {
        var images = AppIcon.images();
        assertThat(images).hasSize(7);
        Files.createDirectories(Path.of("target/ui-snapshots"));
        for (var image : images) {
            var img = (java.awt.image.BufferedImage) image;
            int opaque = 0;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    if ((img.getRGB(x, y) >>> 24) > 0) {
                        opaque++;
                    }
                }
            }
            assertThat(opaque).as("Größe " + img.getWidth()).isGreaterThan(img.getWidth() * img.getHeight() / 3);
        }
        ImageIO.write(AppIcon.draw(256), "png", Path.of("target/ui-snapshots/icon.png").toFile());
    }
}
