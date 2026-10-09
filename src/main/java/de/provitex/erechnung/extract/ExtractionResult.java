// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.extract;

import de.provitex.erechnung.model.InvoiceData;
import java.math.BigDecimal;
import java.util.List;

public record ExtractionResult(InvoiceData draft, PrintedTotals printed, List<String> notes) {
    public record PrintedTotals(BigDecimal net, BigDecimal tax, BigDecimal gross) {
    }
}
