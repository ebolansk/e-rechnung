// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.archive;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BelegStatusTest {
    private static final LocalDate CUT = LocalDate.of(2027, 1, 1);

    @Test
    void beforeCutoffPdfIsOriginal() {
        var a = BelegStatus.assess(LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 15), null, CUT);
        assertThat(a.status()).isEqualTo(BelegStatus.PDF_IST_ORIGINAL);
        assertThat(a.warnings()).isEmpty();
    }

    @Test
    void issueDateAtOrAfterCutoffMeansEInvoiceIsOriginal() {
        var a = BelegStatus.assess(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 12, 15), null, CUT);
        assertThat(a.status()).isEqualTo(BelegStatus.E_RECHNUNG_IST_ORIGINAL);
    }

    @Test
    void advanceInvoiceForServiceAfterCutoffWarns() {
        var a = BelegStatus.assess(LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 10), null, CUT);
        assertThat(a.status()).isEqualTo(BelegStatus.E_RECHNUNG_IST_ORIGINAL);
        assertThat(a.warnings()).anyMatch(w -> w.contains("Vorausrechnung"));
    }

    @Test
    void periodSpanningCutoffWarns() {
        var a = BelegStatus.assess(LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 15), LocalDate.of(2027, 1, 15), CUT);
        assertThat(a.warnings()).anyMatch(w -> w.contains("überspannt"));
    }

    @Test
    void missingDeliveryDateWarnsAndFallsBackToIssueDate() {
        var a = BelegStatus.assess(LocalDate.of(2027, 2, 1), null, null, CUT);
        assertThat(a.status()).isEqualTo(BelegStatus.E_RECHNUNG_IST_ORIGINAL);
        assertThat(a.warnings()).anyMatch(w -> w.contains("Leistungsdatum fehlt"));
    }
}
