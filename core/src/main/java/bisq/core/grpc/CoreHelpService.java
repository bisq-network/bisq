package bisq.core.grpc;

import static java.lang.String.format;

class CoreHelpService {
    
    private final String help = "Bisq Client" +
            "\n" +
            "Usage: bisq-cli [options] <method> [params]" +
            "\n\n" +
            format("%-19s%-30s%s%n", "Method", "Params", "Description") +
            format("%-19s%-30s%s%n", "------", "------", "------------") +
            format("%-19s%-30s%s%n", "getversion", "", "Get server version") +
            format("%-19s%-30s%s%n", "getbalance", "", "Get server wallet balance") +
            format("%-19s%-30s%s%n", "lockwallet", "", "Remove wallet password from memory, locking the wallet") +
            format("%-19s%-30s%s%n", "unlockwallet", "password timeout",
                    "Store wallet password in memory for timeout seconds") +
            format("%-19s%-30s%s%n", "setwalletpassword", "password [newpassword]",
                    "Encrypt wallet with password, or set new password on encrypted wallet") +
            "\n";

    public String getHelp() {
        return this.help;
    }

    public String getHelp(Method method) {
        // TODO return detailed help for method
        return this.help;
    }
}
