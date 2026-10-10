// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.mandant;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MandantStoreTest {
    @Test
    void addAssignsIdPersistsAndReloads(@TempDir Path dir) throws Exception {
        var store = new MandantStore(dir.resolve("mandanten.json"));
        assertThat(store.all()).isEmpty();
        var saved = store.add(MandantMatcherTest.m("", "DE123456789", "", ""));
        assertThat(saved.id()).isNotBlank();
        var reopened = new MandantStore(dir.resolve("mandanten.json"));
        assertThat(reopened.all()).hasSize(1);
        assertThat(reopened.all().get(0).vatId()).isEqualTo("DE123456789");
    }

    @Test
    void updateAndRemove(@TempDir Path dir) throws Exception {
        var store = new MandantStore(dir.resolve("mandanten.json"));
        var saved = store.add(MandantMatcherTest.m("", "DE1", "", ""));
        store.update(new Mandant(saved.id(), "Neu GmbH", "S", "1", "O", "DE", "DE1", "", "e@x", "", "", "", "", ""));
        assertThat(store.all().get(0).name()).isEqualTo("Neu GmbH");
        store.remove(saved.id());
        assertThat(store.all()).isEmpty();
        assertThat(new MandantStore(dir.resolve("mandanten.json")).all()).isEmpty();
    }
}
