// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.extract;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

public final class PdfText {
    /** Zusammenhängender Textabschnitt einer Zeile samt Lage auf der Seite (y wächst nach unten). */
    public record Cell(int page, float x, float xEnd, float y, String text) {
    }

    public record Result(String text, int pages, List<Cell> cells) {
        public Result(String text, int pages) {
            this(text, pages, List.of());
        }
    }

    private static final float SAME_LINE = 2.5f;

    private PdfText() {
    }

    public static Result extract(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            Collector stripper = new Collector();
            stripper.setSortByPosition(true);
            String text = io.github.ebolansk.erechnung.util.GermanFormats.normalizeMinus(stripper.getText(doc));
            return new Result(text, doc.getNumberOfPages(), stripper.cells());
        }
    }

    /** Sammelt Wörter mit Position und fasst sie zu Zellen zusammen: ein größerer Abstand als eine Schrifthöhe trennt Spalten. */
    private static final class Collector extends PDFTextStripper {
        private final List<Cell> cells = new ArrayList<>();
        private StringBuilder current;
        private int page;
        private float x;
        private float xEnd;
        private float y;
        private float size;

        Collector() throws IOException {
            super();
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) throws IOException {
            super.writeString(text, positions);
            if (positions.isEmpty() || text.isBlank()) {
                return;
            }
            TextPosition first = positions.get(0);
            TextPosition last = positions.get(positions.size() - 1);
            float wx = first.getXDirAdj();
            float wEnd = last.getXDirAdj() + last.getWidthDirAdj();
            float wy = first.getYDirAdj();
            int pg = getCurrentPageNo();
            boolean sameCell = current != null && pg == page && Math.abs(wy - y) < SAME_LINE
                && wx - xEnd <= Math.max(size, first.getFontSizeInPt()) && wx >= x;
            if (sameCell) {
                current.append(' ').append(text.strip());
                xEnd = wEnd;
            } else {
                flush();
                current = new StringBuilder(text.strip());
                page = pg;
                x = wx;
                xEnd = wEnd;
                y = wy;
                size = first.getFontSizeInPt();
            }
        }

        private void flush() {
            if (current != null && !current.isEmpty()) {
                cells.add(new Cell(page, x, xEnd, y, io.github.ebolansk.erechnung.util.GermanFormats.normalizeMinus(current.toString())));
            }
            current = null;
        }

        List<Cell> cells() {
            flush();
            return cells;
        }
    }
}
