package io.bisq.provider.fee.providers;

import io.bisq.core.provider.fee.FeeService;

import java.io.IOException;

public class LtcFeesProvider implements FeesProvider {

    public LtcFeesProvider() {
    }

    public Long getFee() throws IOException {
        return FeeService.DEFAULT_TX_FEE;
    }
}
