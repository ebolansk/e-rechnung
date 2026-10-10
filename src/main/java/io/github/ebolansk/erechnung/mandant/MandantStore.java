// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.mandant;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ebolansk.erechnung.util.AtomicFiles;
import io.github.ebolansk.erechnung.util.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MandantStore {
    private final Path file;
    private final List<Mandant> items = new ArrayList<>();

    public MandantStore(Path file) throws IOException {
        this.file = file;
        if (Files.exists(file)) {
            items.addAll(Json.mapper().readValue(file.toFile(), new TypeReference<List<Mandant>>() { }));
        }
    }

    public synchronized List<Mandant> all() {
        return List.copyOf(items);
    }

    public synchronized Mandant add(Mandant m) throws IOException {
        Mandant withId = m.id() == null || m.id().isBlank()
            ? new Mandant(UUID.randomUUID().toString(), m.name(), m.street(), m.zip(), m.city(), m.country(),
                m.vatId(), m.taxNumber(), m.email(), m.contactName(), m.contactPhone(), m.iban(), m.bic(), m.paymentTerms())
            : m;
        items.add(withId);
        persist();
        return withId;
    }

    public synchronized void update(Mandant m) throws IOException {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(m.id())) {
                items.set(i, m);
                persist();
                return;
            }
        }
        throw new IllegalArgumentException("Mandant nicht gefunden: " + m.id());
    }

    public synchronized void remove(String id) throws IOException {
        items.removeIf(m -> m.id().equals(id));
        persist();
    }

    private void persist() throws IOException {
        AtomicFiles.write(file, Json.mapper().writeValueAsBytes(items));
    }
}
