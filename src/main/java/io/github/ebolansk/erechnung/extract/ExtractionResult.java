// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.extract;

import io.github.ebolansk.erechnung.model.InvoiceData;
import java.math.BigDecimal;
import java.util.List;

public record ExtractionResult(InvoiceData draft, PrintedTotals printed, List<String> notes) {
    public record PrintedTotals(BigDecimal net, BigDecimal tax, BigDecimal gross) {
    }
}
