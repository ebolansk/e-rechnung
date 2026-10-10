// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OverviewRulesTest {
    private static final List<String> STATUS = List.of("Archiviert", "Abgebrochen", "Wartet", "Abgebrochen", "Nicht lesbar");

    @Test
    void deleteRemovesFinishedRowsDescendingAndNothingWhileBusy() {
        // Archiviert, Abgebrochen, Wartet, Abgebrochen, Nicht lesbar
        assertThat(OverviewRules.removable(STATUS, new int[] {0, 1, 3, 4}, false)).containsExactly(4, 3, 1, 0);
        assertThat(OverviewRules.removable(STATUS, new int[] {2}, false)).as("Wartende Zeilen bleiben").isEmpty();
        assertThat(OverviewRules.removable(STATUS, new int[] {1, 3}, true)).isEmpty();
        assertThat(OverviewRules.removable(STATUS, new int[] {-1, 9}, false)).isEmpty();
    }

    @Test
    void reprocessTakesOnlyCancelledAndFailedRowsInTableOrder() {
        assertThat(OverviewRules.reprocessable(STATUS, new int[] {4, 3, 1, 0, 2})).containsExactly(1, 3, 4);
        assertThat(OverviewRules.reprocessable(STATUS, new int[] {0, 2})).isEmpty();
        assertThat(OverviewRules.reprocessable(STATUS, new int[] {-1, 7})).isEmpty();
    }

    @Test
    void generatedDraftsCanBeAdoptedAndAreRemovableButNotWhileBusy() {
        List<String> status = List.of("Erzeugt", "Archiviert", "Erzeugt", "Abgebrochen", "Wartet", "Wird archiviert …");
        assertThat(OverviewRules.adoptable(status, new int[] {5, 2, 1, 0, 3})).containsExactly(0, 2);
        assertThat(OverviewRules.removable(status, new int[] {0, 4, 5}, false)).containsExactly(0);
        assertThat(OverviewRules.removable(status, new int[] {0}, true)).isEmpty();
        assertThat(OverviewRules.kind("Erzeugt")).isEqualTo(OverviewRules.Kind.READY);
        assertThat(OverviewRules.percent("Erzeugt")).isBetween(80, 99);
        assertThat(OverviewRules.percent("Erzeugt")).isLessThan(OverviewRules.percent("Archiviert"));
    }

    @Test
    void masterCheckboxStateAndTargetRows() {
        assertThat(OverviewRules.allChecked(List.of())).isFalse();
        assertThat(OverviewRules.allChecked(List.of(true, true))).isTrue();
        assertThat(OverviewRules.allChecked(List.of(true, false))).isFalse();
        assertThat(OverviewRules.target(List.of(false, true, true), new int[] {0})).as("angehakte gehen vor").containsExactly(1, 2);
        assertThat(OverviewRules.target(List.of(false, false), new int[] {1})).as("sonst die markierten").containsExactly(1);
    }

    @Test
    void progressFollowsTheProcessingSteps() {
        assertThat(OverviewRules.percent("Wartet")).isZero();
        assertThat(OverviewRules.percent("In Bearbeitung")).isLessThan(OverviewRules.percent("In Prüfung"));
        assertThat(OverviewRules.percent("In Prüfung")).isLessThan(OverviewRules.percent("Wird erzeugt …"));
        assertThat(OverviewRules.percent("Archiviert")).isEqualTo(100);
        assertThat(OverviewRules.kind("Archiviert")).isEqualTo(OverviewRules.Kind.DONE);
        assertThat(OverviewRules.kind("Abgebrochen")).isEqualTo(OverviewRules.Kind.CANCELLED);
        assertThat(OverviewRules.kind("Nicht lesbar")).isEqualTo(OverviewRules.Kind.FAILED);
        assertThat(OverviewRules.kind("Wartet")).isEqualTo(OverviewRules.Kind.ACTIVE);
    }
}
