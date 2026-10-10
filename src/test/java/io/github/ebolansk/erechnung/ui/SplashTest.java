// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class SplashTest {
    @Test
    void progressCallsAreHarmlessWithoutASplashScreen() {
        assertThatCode(() -> {
            Splash.step(0, "Start");
            Splash.step(55, "Dienste");
            Splash.step(100, "Fertig");
            Splash.close();
        }).doesNotThrowAnyException();
    }

    @Test
    void splashImageIsWrittenAsPngWithLogoAndVersion() throws Exception {
        Path png = Files.createTempFile("splash", ".png");
        try {
            SplashImage.main(new String[] {png.toString()});
            var img = ImageIO.read(png.toFile());
            assertThat(img.getWidth()).isEqualTo(SplashImage.WIDTH);
            assertThat(img.getHeight()).isEqualTo(SplashImage.HEIGHT);
            int opaque = 0;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    if ((img.getRGB(x, y) >>> 24) > 0) {
                        opaque++;
                    }
                }
            }
            assertThat(opaque).isGreaterThan(SplashImage.WIDTH * SplashImage.HEIGHT / 2);
        } finally {
            Files.deleteIfExists(png);
        }
    }
}
