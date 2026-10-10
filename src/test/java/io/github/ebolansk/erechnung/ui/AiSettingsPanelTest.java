// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.github.ebolansk.erechnung.ai.AiProvider;
import io.github.ebolansk.erechnung.ai.AiSettingsStore;
import io.github.ebolansk.erechnung.ai.SecretProtector;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiSettingsPanelTest {
    @Test
    void modelRequestGoesOutOnlyOnClickNeverOnProviderOrAddressChange(@TempDir Path dir) throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            requests.incrementAndGet();
            byte[] body = "{\"data\":[{\"id\":\"modell-a\"}]}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        try {
            var panel = new AiSettingsPanel(new AiSettingsStore(dir.resolve("ki.json"), SecretProtector.UNAVAILABLE));
            panel.providerBox().setSelectedItem(AiProvider.CLAUDE);
            panel.providerBox().setSelectedItem(AiProvider.OPENAI);
            panel.urlField().setText("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            panel.urlField().postActionEvent();
            panel.urlField().getFocusListeners();
            Thread.sleep(600);
            assertThat(requests).as("keine Anfrage ohne Klick").hasValue(0);

            panel.loadModelsButton().doClick();
            long end = System.currentTimeMillis() + 5000;
            while (requests.get() == 0 && System.currentTimeMillis() < end) {
                Thread.sleep(50);
            }
            assertThat(requests.get()).as("eine Anfrage nach dem Klick").isGreaterThanOrEqualTo(1);
        } finally {
            server.stop(0);
        }
    }
}
