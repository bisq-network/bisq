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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.exit;
import static java.lang.System.in;

/**
 * gRPC client.
 */
@Slf4j
public class BisqCliMain {

    private final ManagedChannel channel;
    private final CliCommand cmd;

    public static void main(String[] args) {
        new BisqCliMain("localhost", 8888);
    }

    private BisqCliMain(String host, int port) {
        // Channels are secure by default (via SSL/TLS);  for the example disable TLS to avoid needing certificates.
        this(ManagedChannelBuilder.forAddress(host, port).usePlaintext().build());

        // Simple input scanner
        // TODO use some more sophisticated input processing with validation....
        try (Scanner scanner = new Scanner(in)) {
            while (true) {
                long startTs = currentTimeMillis();

                String[] tokens = scanner.nextLine().split(" ");
                if (tokens.length == 0) {
                    return;
                }
                String command = tokens[0];
                if (tokens.length > 1) {
                    List<String> params = new ArrayList<>(Arrays.asList(tokens));
                    params.remove(0);
                }
                String result;

                switch (command) {
                    case "getBalance":
                        long satoshis = cmd.getBalance();
                        // TODO mimic bitcoin-cli?  Depends on an error code: Loading block index... Verifying blocks...
                        result = satoshis == -1 ? "Server initializing..." : cmd.prettyBalance.apply(satoshis);
                        break;
                    case "getVersion":
                        result = cmd.getVersion();
                        break;
                    case "stop":
                        result = "Shut down client";
                        try {
                            shutdown();
                        } catch (InterruptedException e) {
                            log.error(e.toString(), e);
                        }
                        break;
                    case "stopServer":
                        cmd.stopServer();
                        result = "Server stopped";
                        break;
                    default:
                        result = format("Unknown command '%s'", command);
                }

                // First response is rather slow (300 ms) but following responses are fast (3-5 ms).
                log.info("{}\t{}", result, cmd.responseTime.apply(startTs));
            }
        }
    }

    /**
     * Construct client for accessing server using the existing channel.
     */
    private BisqCliMain(ManagedChannel channel) {
        this.channel = channel;
        this.cmd = new CliCommand(channel);
    }

    private void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
        exit(0);
    }
}
