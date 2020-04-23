package bisq.cli.app;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import static java.lang.System.out;

final class CliConfig {

    // Non-Option argument name constants
    static final String HELP = "help";
    static final String GETBALANCE = "getbalance";
    static final String GETVERSION = "getversion";
    static final String STOPSERVER = "stopserver";

    // Argument accepting name constants
    static final String API_TOKEN = "apiToken";

    // Argument accepting cmd options
    final String apiToken;

    // The parser that will be used to parse both cmd line and config file options
    private final OptionParser optionParser = new OptionParser();
    private final String[] params;

    CliConfig(String[] params) {
        this.params = params;

        ArgumentAcceptingOptionSpec<String> apiTokenOpt =
                optionParser.accepts(API_TOKEN, "Bisq API token")
                        .withRequiredArg();

        try {
            CompositeOptionSet options = new CompositeOptionSet();
            // Parse command line options
            OptionSet cliOpts = optionParser.parse(params);
            options.addOptionSet(cliOpts);

            this.apiToken = options.valueOf(apiTokenOpt);

            optionParser.allowsUnrecognizedOptions();
            optionParser.nonOptions(GETBALANCE).ofType(String.class).describedAs("Get btc balance");
            optionParser.nonOptions(GETVERSION).ofType(String.class).describedAs("Get bisq version");

        } catch (OptionException ex) {
            throw new ConfigException("Problem parsing option '%s': %s",
                    ex.options().get(0),
                    ex.getCause() != null ?
                            ex.getCause().getMessage() :
                            ex.getMessage());
        }
    }

    OptionParser getOptionParser() {
        return this.optionParser;
    }

    public String[] getParams() {
        return this.params;
    }

    static void printHelp() {
        out.println("Usage:  bisq-cli --apiToken=token getbalance | getversion");
    }
}
