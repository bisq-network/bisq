package network.bisq.api.app;

import bisq.core.app.BisqEnvironment;
import joptsimple.OptionSet;

public class ApiEnvironment extends BisqEnvironment {

    private final String apiHost;

    private final Integer apiPort;

    public ApiEnvironment(OptionSet options) {
        super(options);
        apiHost = (String) options.valueOf("apiHost");
        apiPort = (Integer) options.valueOf("apiPort");
    }

    public String getApiHost() {
        return apiHost;
    }

    public Integer getApiPort() {
        return apiPort;
    }
}

