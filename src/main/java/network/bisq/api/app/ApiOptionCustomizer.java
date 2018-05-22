package network.bisq.api.app;

import joptsimple.OptionParser;

public class ApiOptionCustomizer {

    public static void customizeOptionParsing(OptionParser parser) {
        parser.accepts(ApiOptionKeys.OPTION_API_PORT, "API port (default: value of env variable BISQ_API_PORT or 8080")
                .withRequiredArg()
                .ofType(int.class);
        parser.accepts(ApiOptionKeys.OPTION_API_HOST, "API hostname (default: value of env variable BISQ_API_HOST or 127.0.0.1)")
                .withRequiredArg()
                .ofType(String.class);
    }
}
