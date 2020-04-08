package bisq.cli.app;

import joptsimple.OptionParser;

import static java.lang.System.out;

final class CommandParser {

    // Option name constants
    static final String HELP = "help";
    static final String GETBALANCE = "getbalance";
    static final String GETVERSION = "getversion";
    static final String STOPSERVER = "stopserver";

    OptionParser configure() {
        OptionParser parser = new OptionParser();
        parser.allowsUnrecognizedOptions();
        parser.nonOptions(GETBALANCE).ofType(String.class).describedAs("get btc balance");
        parser.nonOptions(GETVERSION).ofType(String.class).describedAs("get bisq version");
        return parser;
    }

    static void printHelp() {
        out.println("Usage:  bisq-cli getbalance | getversion");
    }

}
