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
    static final String RPC_USER = "rpcuser";
    static final String RPC_PASSWORD = "rpcpassword";

    // Argument accepting cmd options
    final String rpcUser;
    final String rpcPassword;

    // The parser that will be used to parse both cmd line and config file options
    private final OptionParser optionParser = new OptionParser();
    private final String[] params;

    CliConfig(String[] params) {
        this.params = params;

        ArgumentAcceptingOptionSpec<String> rpcUserOpt =
                optionParser.accepts(RPC_USER, "Bisq daemon username")
                        .withRequiredArg()
                        .defaultsTo("");
        ArgumentAcceptingOptionSpec<String> rpcPasswordOpt =
                optionParser.accepts(RPC_PASSWORD, "Bisq daemon password")
                        .withRequiredArg()
                        .defaultsTo("");
        try {
            CompositeOptionSet options = new CompositeOptionSet();
            // Parse command line options
            OptionSet cliOpts = optionParser.parse(params);
            options.addOptionSet(cliOpts);

            this.rpcUser = options.valueOf(rpcUserOpt);
            this.rpcPassword = options.valueOf(rpcPasswordOpt);

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
        out.println("Usage:  bisq-cli --rpcpassword=user --rpcpassword=password  getbalance | getversion");
    }
}
