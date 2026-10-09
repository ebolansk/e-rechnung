// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ArchiveLayoutTest {
    private final Path root = Path.of("/archiv").toAbsolutePath();
    private final LocalDate d = LocalDate.of(2027, 1, 15);

    @Test
    void defaultTemplateBuildsMandantYearMonthNumber() {
        Path p = ArchiveLayout.resolve(root, ArchiveLayout.DEFAULT_TEMPLATE, "Muster GmbH", d, "RE-2027-0001");
        assertThat(p).isEqualTo(root.resolve("Muster GmbH").resolve("2027").resolve("01").resolve("RE-2027-0001"));
    }

    @Test
    void hostileValuesStayInsideRoot() {
        Path p = ArchiveLayout.resolve(root, ArchiveLayout.DEFAULT_TEMPLATE, "..", d, "../../etc/passwd");
        assertThat(p.normalize().startsWith(root)).isTrue();
        Path q = ArchiveLayout.resolve(root, ArchiveLayout.DEFAULT_TEMPLATE, "Müller", d, "RE\\2027\\1");
        assertThat(q.getFileName().toString()).isEqualTo("RE_2027_1");
    }

    @Test
    void customTemplateWithLiteralsWorks() {
        Path p = ArchiveLayout.resolve(root, "Rechnungen {Jahr}/{Monat}/{Mandant}/{Rechnungsnummer}", "A", d, "1");
        assertThat(p).isEqualTo(root.resolve("Rechnungen 2027").resolve("01").resolve("A").resolve("1"));
    }

    @Test
    void invalidTemplatesAreRejected() {
        assertThatThrownBy(() -> ArchiveLayout.validateTemplate("{Foo}/{Rechnungsnummer}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ArchiveLayout.validateTemplate("{Mandant}/{Jahr}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ArchiveLayout.validateTemplate("../{Rechnungsnummer}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ArchiveLayout.validateTemplate("/abs/{Rechnungsnummer}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ArchiveLayout.validateTemplate("C:/x/{Rechnungsnummer}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ArchiveLayout.validateTemplate("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void backslashSeparatorsAreAccepted() {
        Path p = ArchiveLayout.resolve(root, "{Mandant}\\{Jahr}\\{Rechnungsnummer}", "A", d, "1");
        assertThat(p).isEqualTo(root.resolve("A").resolve("2027").resolve("1"));
    }
}
