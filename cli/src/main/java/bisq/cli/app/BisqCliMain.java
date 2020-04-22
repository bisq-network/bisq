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

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import static bisq.cli.app.CliConfig.GETBALANCE;
import static bisq.cli.app.CliConfig.GETVERSION;
import static bisq.cli.app.CliConfig.HELP;
import static bisq.cli.app.CliConfig.STOPSERVER;
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
    private final RpcCommand rpcCommand;
    private final CliConfig config;
    private final CommandParser parser;

    public static void main(String[] args) {
        new BisqCliMain("localhost", 9998, args);
    }

    private BisqCliMain(String host, int port, String[] args) {
        this(ManagedChannelBuilder.forAddress(host, port).usePlaintext().build(), args);
        String result = runCommand();
        out.println(result);
        try {
            shutdown(); // Orderly channel shutdown
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * Construct client for accessing server using the existing channel.
     */
    private BisqCliMain(ManagedChannel channel, String[] args) {
        this.channel = channel;
        this.config = new CliConfig(args);
        this.parser = new CommandParser(config);
        this.rpcCommand = new RpcCommand(channel, parser);
    }

    private String runCommand() {
        if (parser.getCmdToken().isPresent()) {
            final String cmdToken = parser.getCmdToken().get();
            final String result;
            switch (cmdToken) {
                case HELP:
                    CliConfig.printHelp();
                    exit(EXIT_SUCCESS);
                case GETBALANCE:
                    long satoshis = rpcCommand.getBalance();
                    result = satoshis == -1 ? "Server initializing..." : rpcCommand.prettyBalance.apply(satoshis);
                    break;
                case GETVERSION:
                    result = rpcCommand.getVersion();
                    break;
                case STOPSERVER:
                    rpcCommand.stopServer();
                    result = "Server stopped";
                    break;
                default:
                    result = format("Unknown command '%s'", cmdToken);
            }
            return result;
        } else {
            CliConfig.printHelp();
            exit(EXIT_FAILURE);
            return null;
        }
    }

    private void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
        exit(EXIT_SUCCESS);
    }
}
