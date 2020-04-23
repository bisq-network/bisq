package bisq.cli.app;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.util.List;
import java.util.Optional;

final class CommandParser {

    private Optional<String> cmdToken;
    private String apiToken;
    private final CliConfig config;

    public CommandParser(CliConfig config) {
        this.config = config;
        init();
    }

    public Optional<String> getCmdToken() {
        return cmdToken;
    }

    public String getApiToken() {
        return apiToken;
    }

    private void init() {
        OptionParser parser = config.getOptionParser();
        OptionSpec<String> nonOptions = parser.nonOptions().ofType(String.class);
        OptionSet options = parser.parse(config.getParams());
        apiToken = (String) options.valueOf("apiToken");
        List<String> detectedOptions = nonOptions.values(options);
        cmdToken = detectedOptions.isEmpty() ? Optional.empty() : Optional.of(detectedOptions.get(0));
    }
}
