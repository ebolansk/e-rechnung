// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.generate;

import io.github.ebolansk.erechnung.model.InvoiceData;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.ZUGFeRD2PullProvider;

public final class XRechnungGenerator {
    public byte[] generate(InvoiceData data) {
        ZUGFeRD2PullProvider provider = new ZUGFeRD2PullProvider();
        provider.setProfile(Profiles.getByName("XRechnung"));
        provider.generateXML(MustangMapper.toInvoice(data));
        return provider.getXML();
    }
}
