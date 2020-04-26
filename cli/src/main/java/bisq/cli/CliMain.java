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

package bisq.cli;

import bisq.proto.grpc.GetBalanceGrpc;
import bisq.proto.grpc.GetBalanceRequest;
import bisq.proto.grpc.GetVersionGrpc;
import bisq.proto.grpc.GetVersionRequest;

import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import java.text.DecimalFormat;

import java.io.IOException;
import java.io.PrintStream;

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
public class CliMain {

    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_FAILURE = 1;

    private enum Method {
        getversion,
        getbalance
    }

    public static void main(String[] args) {
        var parser = new OptionParser();

        var helpOpt = parser.accepts("help", "Print this help text")
                .forHelp();

        var hostOpt = parser.accepts("host", "rpc server hostname or IP")
                .withRequiredArg()
                .defaultsTo("localhost");

        var portOpt = parser.accepts("port", "rpc server port")
                .withRequiredArg()
                .ofType(Integer.class)
                .defaultsTo(9998);

        var passwordOpt = parser.accepts("password", "rpc server password")
                .withRequiredArg()
                .required();

        OptionSet options = null;
        try {
            options = parser.parse(args);
        } catch (OptionException ex) {
            err.println("Error: " + ex.getMessage());
            exit(EXIT_FAILURE);
        }

        if (options.has(helpOpt)) {
            printHelp(parser, out);
            exit(EXIT_SUCCESS);
        }

        @SuppressWarnings("unchecked")
        var nonOptionArgs = (List<String>) options.nonOptionArguments();
        if (nonOptionArgs.isEmpty()) {
            printHelp(parser, err);
            err.println("Error: no method specified");
            exit(EXIT_FAILURE);
        }

        var methodName = nonOptionArgs.get(0);
        Method method = null;
        try {
            method = Method.valueOf(methodName);
        } catch (IllegalArgumentException ex) {
            err.printf("Error: '%s' is not a supported method\n", methodName);
            exit(EXIT_FAILURE);
        }

        var host = options.valueOf(hostOpt);
        var port = options.valueOf(portOpt);
        var password = options.valueOf(passwordOpt);

        var credentials = new PasswordCallCredentials(password);

        var channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                ex.printStackTrace(err);
                exit(EXIT_FAILURE);
            }
        }));

        try {
            switch (method) {
                case getversion: {
                    var stub = GetVersionGrpc.newBlockingStub(channel).withCallCredentials(credentials);
                    var request = GetVersionRequest.newBuilder().build();
                    var version = stub.getVersion(request).getVersion();
                    out.println(version);
                    exit(EXIT_SUCCESS);
                }
                case getbalance: {
                    var stub = GetBalanceGrpc.newBlockingStub(channel).withCallCredentials(credentials);
                    var request = GetBalanceRequest.newBuilder().build();
                    var balance = stub.getBalance(request).getBalance();
                    if (balance == -1) {
                        err.println("Error: server is still initializing");
                        exit(EXIT_FAILURE);
                    }
                    out.println(formatBalance(balance));
                    exit(EXIT_SUCCESS);
                }
                default: {
                    err.printf("Error: unhandled method '%s'\n", method);
                    exit(EXIT_FAILURE);
                }
            }
        } catch (StatusRuntimeException ex) {
            // This exception is thrown if the client-provided password credentials do not
            // match the value set on the server. The actual error message is in a nested
            // exception and we clean it up a bit to make it more presentable.
            Throwable t = ex.getCause() == null ? ex : ex.getCause();
            err.println("Error: " + t.getMessage().replace("UNAUTHENTICATED: ", ""));
            exit(EXIT_FAILURE);
        }
    }

    private static void printHelp(OptionParser parser, PrintStream stream) {
        try {
            stream.println("Bisq RPC Client");
            stream.println();
            stream.println("Usage: bisq-cli [options] <method>");
            stream.println();
            parser.printHelpOn(stream);
            stream.println();
            stream.println("Method      Description");
            stream.println("------      -----------");
            stream.println("getversion  Get server version");
            stream.println("getbalance  Get server wallet balance");
            stream.println();
        } catch (IOException ex) {
            ex.printStackTrace(stream);
        }
    }

    @SuppressWarnings("BigDecimalMethodWithoutRoundingCalled")
    private static String formatBalance(long satoshis) {
        var btcFormat = new DecimalFormat("###,##0.00000000");
        var satoshiDivisor = new BigDecimal(100000000);
        return btcFormat.format(BigDecimal.valueOf(satoshis).divide(satoshiDivisor));
    }
}
