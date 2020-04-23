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

import bisq.proto.grpc.GetBalanceGrpc;
import bisq.proto.grpc.GetBalanceRequest;
import bisq.proto.grpc.GetVersionGrpc;
import bisq.proto.grpc.GetVersionRequest;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import joptsimple.AbstractOptionSpec;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import java.text.DecimalFormat;

import java.io.IOException;

import java.math.BigDecimal;

import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import static java.lang.System.err;
import static java.lang.System.exit;
import static java.lang.System.out;

/**
 * A command-line client for the Bisq gRPC API.
 */
@Slf4j
public class BisqCliMain {

    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_FAILURE = 1;

    public static void main(String[] args) throws IOException {
        OptionParser parser = new OptionParser();

        AbstractOptionSpec<Void> helpOpt =
                parser.accepts("help", "Print this help text")
                        .forHelp();

        ArgumentAcceptingOptionSpec<String> hostOpt =
                parser.accepts("host", "Bisq node hostname or IP")
                        .withRequiredArg()
                        .defaultsTo("localhost");

        ArgumentAcceptingOptionSpec<Integer> portOpt =
                parser.accepts("port", "Bisq node RPC port")
                        .withRequiredArg()
                        .ofType(Integer.class)
                        .defaultsTo(9998);

        ArgumentAcceptingOptionSpec<String> authOpt =
                parser.accepts("auth", "Bisq node RPC authentication token")
                        .withRequiredArg();

        OptionSet options = parser.parse(args);

        if (options.has(helpOpt)) {
            out.println("Bisq RPC Client");
            out.println();
            out.println("Usage: bisq-cli [options] <command>");
            out.println();
            parser.printHelpOn(out);
            out.println();
            out.println("Command           Descripiton");
            out.println("-------           -----------");
            out.println("getversion        Get Bisq node version");
            out.println("getbalance        Get Bisq node wallet balance");
            out.println();
            exit(EXIT_SUCCESS);
        }

        String host = options.valueOf(hostOpt);
        int port = options.valueOf(portOpt);

        String authToken = options.valueOf(authOpt);
        if (authToken == null) {
            err.println("error: rpc authentication token must not be null");
            exit(EXIT_FAILURE);
        }

        @SuppressWarnings("unchecked")
        List<String> nonOptionArgs = (List<String>) options.nonOptionArguments();
        if (nonOptionArgs.isEmpty()) {
            err.println("error: no rpc command specified");
            exit(EXIT_FAILURE);
        }

        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        BisqCallCredentials credentials = new BisqCallCredentials(authToken);

        String command = nonOptionArgs.get(0);

        if ("getversion".equals(command)) {
            GetVersionRequest request = GetVersionRequest.newBuilder().build();
            GetVersionGrpc.GetVersionBlockingStub getVersionStub =
                    GetVersionGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            out.println(getVersionStub.getVersion(request).getVersion());
            shutdown(channel);
            exit(EXIT_SUCCESS);
        }

        if ("getbalance".equals(command)) {
            GetBalanceGrpc.GetBalanceBlockingStub getBalanceStub =
                    GetBalanceGrpc.newBlockingStub(channel).withCallCredentials(credentials);
            GetBalanceRequest request = GetBalanceRequest.newBuilder().build();
            long satoshis = getBalanceStub.getBalance(request).getBalance();
            if (satoshis == -1) {
                err.println("Server initializing...");
                shutdown(channel);
                exit(EXIT_FAILURE);
            }
            out.println(formatBalance(satoshis));
            shutdown(channel);
            exit(EXIT_SUCCESS);
        }

        err.printf("error: unknown rpc command '%s'\n", command);
        exit(EXIT_FAILURE);
    }

    @SuppressWarnings("BigDecimalMethodWithoutRoundingCalled")
    private static String formatBalance(long satoshis) {
        DecimalFormat btcFormat = new DecimalFormat("###,##0.00000000");
        BigDecimal satoshiDivisor = new BigDecimal(100000000);
        return btcFormat.format(BigDecimal.valueOf(satoshis).divide(satoshiDivisor));
    }

    private static void shutdown(ManagedChannel channel) {
        try {
            channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }
}
