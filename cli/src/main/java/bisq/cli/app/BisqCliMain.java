/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.cli.app;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import static bisq.cli.app.CommandParser.GETBALANCE;
import static bisq.cli.app.CommandParser.GETVERSION;
import static bisq.cli.app.CommandParser.HELP;
import static bisq.cli.app.CommandParser.STOPSERVER;
import static java.lang.String.format;
import static java.lang.System.exit;
import static java.lang.System.out;

/**
 * gRPC client.
 */
@Slf4j
public class BisqCliMain {

    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_FAILURE = 1;

    private final ManagedChannel channel;
    private final CliCommand cmd;
    private final OptionParser parser;

    public static void main(String[] args) {
        new BisqCliMain("localhost", 9998, args);
    }

    private BisqCliMain(String host, int port, String[] args) {
        // Channels are secure by default (via SSL/TLS);  for the example disable TLS to avoid needing certificates.
        this(ManagedChannelBuilder.forAddress(host, port).usePlaintext().build());
        String command = parseCommand(args);
        String result = runCommand(command);
        out.println(result);
        try {
            shutdown(); // Orderly channel shutdown
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * Construct client for accessing server using the existing channel.
     */
    private BisqCliMain(ManagedChannel channel) {
        this.channel = channel;
        this.cmd = new CliCommand(channel);
        this.parser = new CommandParser().configure();
    }

    private String runCommand(String command) {
        final String result;
        switch (command) {
            case HELP:
                CommandParser.printHelp();
                exit(EXIT_SUCCESS);
            case GETBALANCE:
                long satoshis = cmd.getBalance();
                result = satoshis == -1 ? "Server initializing..." : cmd.prettyBalance.apply(satoshis);
                break;
            case GETVERSION:
                result = cmd.getVersion();
                break;
            case STOPSERVER:
                cmd.stopServer();
                result = "Server stopped";
                break;
            default:
                result = format("Unknown command '%s'", command);
        }
        return result;
    }

    private String parseCommand(String[] params) {
        OptionSpec<String> nonOptions = parser.nonOptions().ofType(String.class);
        OptionSet options = parser.parse(params);
        List<String> detectedOptions = nonOptions.values(options);
        if (detectedOptions.isEmpty()) {
            CommandParser.printHelp();
            exit(EXIT_FAILURE);
        }
        return detectedOptions.get(0);
    }

    private void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
        exit(EXIT_SUCCESS);
    }
}
