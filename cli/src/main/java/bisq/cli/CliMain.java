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

import bisq.proto.grpc.GetBalanceRequest;
import bisq.proto.grpc.GetVersionGrpc;
import bisq.proto.grpc.GetVersionRequest;
import bisq.proto.grpc.LockWalletRequest;
import bisq.proto.grpc.RemoveWalletPasswordRequest;
import bisq.proto.grpc.SetWalletPasswordRequest;
import bisq.proto.grpc.UnlockWalletRequest;
import bisq.proto.grpc.WalletGrpc;

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
        getbalance,
        lockwallet,
        unlockwallet,
        removewalletpassword,
        setwalletpassword
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
                .withRequiredArg();

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
        if (password == null) {
            err.println("Error: missing required 'password' option");
            exit(EXIT_FAILURE);
        }

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

        var versionService = GetVersionGrpc.newBlockingStub(channel).withCallCredentials(credentials);
        var walletService = WalletGrpc.newBlockingStub(channel).withCallCredentials(credentials);

        try {
            switch (method) {
                case getversion: {
                    var request = GetVersionRequest.newBuilder().build();
                    var version = versionService.getVersion(request).getVersion();
                    out.println(version);
                    exit(EXIT_SUCCESS);
                }
                case getbalance: {
                    var request = GetBalanceRequest.newBuilder().build();
                    var reply = walletService.getBalance(request);
                    out.println(formatBalance(reply.getBalance()));
                    exit(EXIT_SUCCESS);
                }
                case lockwallet: {
                    var request = LockWalletRequest.newBuilder().build();
                    walletService.lockWallet(request);
                    out.println("wallet locked");
                    exit(EXIT_SUCCESS);
                }
                case unlockwallet: {
                    if (nonOptionArgs.size() < 2) {
                        err.println("Error: no \"password\" specified");
                        exit(EXIT_FAILURE);
                    }
                    if (nonOptionArgs.size() < 3) {
                        err.println("Error: no unlock timeout specified");
                        exit(EXIT_FAILURE);
                    }
                    long timeout = 0;
                    try {
                        timeout = Long.parseLong(nonOptionArgs.get(2));
                    } catch (NumberFormatException e) {
                        err.println(nonOptionArgs.get(2) + " is not a number");
                        exit(EXIT_FAILURE);
                    }
                    var request = UnlockWalletRequest.newBuilder()
                            .setPassword(nonOptionArgs.get(1))
                            .setTimeout(timeout).build();
                    walletService.unlockWallet(request);
                    out.println("wallet unlocked");
                    exit(EXIT_SUCCESS);
                }
                case removewalletpassword: {
                    if (nonOptionArgs.size() < 2) {
                        err.println("Error: no \"password\" specified");
                        exit(EXIT_FAILURE);
                    }
                    var request = RemoveWalletPasswordRequest.newBuilder().setPassword(nonOptionArgs.get(1)).build();
                    walletService.removeWalletPassword(request);
                    out.println("wallet decrypted");
                    exit(EXIT_SUCCESS);
                }
                case setwalletpassword: {
                    if (nonOptionArgs.size() < 2) {
                        err.println("Error: no \"password\" specified");
                        exit(EXIT_FAILURE);
                    }
                    var request = (nonOptionArgs.size() == 3)
                            ? SetWalletPasswordRequest.newBuilder().setPassword(nonOptionArgs.get(1)).setNewPassword(nonOptionArgs.get(2)).build()
                            : SetWalletPasswordRequest.newBuilder().setPassword(nonOptionArgs.get(1)).build();
                    walletService.setWalletPassword(request);
                    out.println("wallet encrypted" + (nonOptionArgs.size() == 2 ? "" : " with new password"));
                    exit(EXIT_SUCCESS);
                }
                default: {
                    err.printf("Error: unhandled method '%s'\n", method);
                    exit(EXIT_FAILURE);
                }
            }
        } catch (StatusRuntimeException ex) {
            // Remove the leading gRPC status code (e.g. "UNKNOWN: ") from the message
            String message = ex.getMessage().replaceFirst("^[A-Z_]+: ", "");
            err.println("Error: " + message);
            exit(EXIT_FAILURE);
        }
    }

    private static void printHelp(OptionParser parser, PrintStream stream) {
        try {
            stream.println("Bisq RPC Client");
            stream.println();
            stream.println("Usage: bisq-cli [options] <method> [params]");
            stream.println();
            parser.printHelpOn(stream);
            stream.println();
            stream.format("%-19s%-30s%s%n", "Method", "Params", "Description");
            stream.format("%-19s%-30s%s%n", "------", "------", "------------");
            stream.format("%-19s%-30s%s%n", "getversion", "", "Get server version");
            stream.format("%-19s%-30s%s%n", "getbalance", "", "Get server wallet balance");
            stream.format("%-19s%-30s%s%n", "lockwallet", "", "Remove wallet password from memory, locking the wallet");
            stream.format("%-19s%-30s%s%n", "unlockwallet", "\"password\" timeout", "Store wallet password in memory for 'timeout' seconds");
            stream.format("%-19s%-30s%s%n", "setwalletpassword", "\"password\" [,\"newpassword\"]",
                    "Encrypt wallet with password, or set new password on encrypted wallet");
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
