// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.ai;

/** apiKey wird nur gespeichert, wenn saveKey gesetzt ist; sonst hält ihn der AiSettingsStore nur im Arbeitsspeicher. */
public record AiSettings(AiProvider provider, String url, String model, String apiKey, boolean saveKey) {
    public static final String CLAUDE_URL = "https://api.anthropic.com/v1/messages";
    public static final String OPENAI_URL = "https://api.openai.com/v1";

    public AiSettings {
        provider = provider == null ? AiProvider.OFF : provider;
        url = url == null ? "" : url.trim();
        model = model == null ? "" : model.trim();
        apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public static AiSettings off() {
        return new AiSettings(AiProvider.OFF, "", "", "", false);
    }

    /** Konfiguriert genug, um eine Anfrage senden zu können. */
    public boolean usable() {
        if (provider == AiProvider.OFF || model.isBlank()) {
            return false;
        }
        return provider == AiProvider.OPENAI ? !endpoint().isBlank() : !apiKey.isBlank();
    }

    public String endpoint() {
        if (!url.isBlank()) {
            return url;
        }
        return provider == AiProvider.CLAUDE ? CLAUDE_URL : provider == AiProvider.OPENAI ? OPENAI_URL : "";
    }

    public String describe() {
        return provider.label() + ", Modell " + model + ", " + endpoint();
    }
}
