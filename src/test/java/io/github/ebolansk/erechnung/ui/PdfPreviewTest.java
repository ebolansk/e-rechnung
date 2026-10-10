// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ebolansk.erechnung.TestData;
import java.awt.Point;
import org.junit.jupiter.api.Test;

class PdfPreviewTest {
    private static PdfPreview preview() throws Exception {
        PdfPreview pv = new PdfPreview(TestData.embeddedFontPdf("Rechnung RE-2027-0001"));
        pv.setSize(600, 800);
        UiSmokeTest.layoutDeep(pv);
        return pv;
    }

    @Test
    void zoomInAndOutChangeTheRenderedWidthWithinLimitsAndFitResets() throws Exception {
        PdfPreview pv = preview();
        int fitWidth = pv.imageWidth();
        assertThat(pv.zoomPercent()).isEqualTo(100);
        assertThat(fitWidth).isGreaterThan(400);

        pv.zoomIn();
        UiSmokeTest.layoutDeep(pv);
        assertThat(pv.zoomPercent()).isEqualTo(125);
        assertThat(pv.imageWidth()).isBetween((int) (fitWidth * 1.2), (int) (fitWidth * 1.3));

        pv.zoomOut();
        pv.zoomOut();
        UiSmokeTest.layoutDeep(pv);
        assertThat(pv.imageWidth()).isLessThan(fitWidth);

        for (int i = 0; i < 20; i++) {
            pv.zoomIn();
        }
        assertThat(pv.zoomPercent()).as("Obergrenze").isEqualTo(400);
        for (int i = 0; i < 30; i++) {
            pv.zoomOut();
        }
        assertThat(pv.zoomPercent()).as("Untergrenze").isEqualTo(40);

        pv.fit();
        UiSmokeTest.layoutDeep(pv);
        assertThat(pv.zoomPercent()).isEqualTo(100);
        assertThat(pv.imageWidth()).isEqualTo(fitWidth);
        pv.close();
    }

    @Test
    void handDragMovesTheVisibleSectionAndStaysOnThePage() throws Exception {
        PdfPreview pv = preview();
        assertThat(pv.viewPosition()).isEqualTo(new Point(0, 0));
        pv.zoomIn();
        pv.zoomIn();
        pv.zoomIn();
        UiSmokeTest.layoutDeep(pv);
        pv.panBy(80, 120);
        UiSmokeTest.layoutDeep(pv);
        assertThat(pv.viewPosition().x).as("nach rechts verschoben").isPositive();
        assertThat(pv.viewPosition().y).as("nach unten verschoben").isPositive();
        pv.panBy(-100_000, -100_000);
        assertThat(pv.viewPosition()).as("nicht über den Seitenrand hinaus").isEqualTo(new Point(0, 0));
        pv.panBy(100_000, 100_000);
        Point end = pv.viewPosition();
        pv.panBy(50, 50);
        assertThat(pv.viewPosition()).as("auch am unteren/rechten Rand begrenzt").isEqualTo(end);
        pv.close();
    }

    private static java.awt.event.MouseWheelEvent wheel(PdfPreview pv, int rotation, int modifiers) {
        return new java.awt.event.MouseWheelEvent(pv, java.awt.event.MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), modifiers, 200, 300, 0, false,
            java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation);
    }

    @Test
    void mouseWheelZoomsWithoutCtrlAndShiftWheelScrolls() throws Exception {
        PdfPreview pv = preview();
        pv.onWheel(wheel(pv, -1, 0));
        UiSmokeTest.layoutDeep(pv);
        assertThat(pv.zoomPercent()).as("Rad nach oben vergrößert").isEqualTo(125);
        pv.onWheel(wheel(pv, -1, 0));
        UiSmokeTest.layoutDeep(pv);
        assertThat(pv.zoomPercent()).isEqualTo(156);
        pv.onWheel(wheel(pv, 1, 0));
        pv.onWheel(wheel(pv, 1, 0));
        UiSmokeTest.layoutDeep(pv);
        assertThat(pv.zoomPercent()).as("Rad nach unten verkleinert").isEqualTo(100);

        pv.zoomIn();
        pv.zoomIn();
        UiSmokeTest.layoutDeep(pv);
        int zoomBefore = pv.zoomPercent();
        pv.onWheel(wheel(pv, 3, java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        UiSmokeTest.layoutDeep(pv);
        assertThat(pv.zoomPercent()).as("Umschalt + Rad zoomt nicht").isEqualTo(zoomBefore);
        assertThat(pv.viewPosition().y).as("sondern scrollt").isPositive();
        pv.close();
    }
}
