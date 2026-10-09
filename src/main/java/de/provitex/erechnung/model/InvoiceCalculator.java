// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InvoiceCalculator {
    public record TaxLine(String category, BigDecimal percent, BigDecimal basis, BigDecimal tax) {
    }

    public record Totals(BigDecimal netTotal, BigDecimal taxTotal, BigDecimal grossTotal, List<TaxLine> taxLines) {
    }

    private InvoiceCalculator() {
    }

    public static BigDecimal lineNet(LineItem i) {
        return i.quantity().multiply(i.unitPrice()).setScale(2, RoundingMode.HALF_UP);
    }

    public static Totals compute(List<LineItem> items) {
        Map<String, BigDecimal[]> groups = new LinkedHashMap<>();
        for (LineItem i : items) {
            String key = i.vatCategory() + "|" + i.vatPercent().stripTrailingZeros().toPlainString();
            groups.computeIfAbsent(key, k -> new BigDecimal[] {BigDecimal.ZERO.setScale(2), i.vatPercent()});
            BigDecimal[] g = groups.get(key);
            g[0] = g[0].add(lineNet(i));
        }
        List<TaxLine> lines = new ArrayList<>();
        BigDecimal net = BigDecimal.ZERO.setScale(2);
        BigDecimal tax = BigDecimal.ZERO.setScale(2);
        for (Map.Entry<String, BigDecimal[]> e : groups.entrySet()) {
            BigDecimal basis = e.getValue()[0];
            BigDecimal percent = e.getValue()[1];
            BigDecimal t = basis.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            lines.add(new TaxLine(e.getKey().substring(0, e.getKey().indexOf('|')), percent, basis, t));
            net = net.add(basis);
            tax = tax.add(t);
        }
        return new Totals(net, tax, net.add(tax), lines);
    }
}
