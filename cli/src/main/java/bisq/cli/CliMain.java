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

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import java.text.DecimalFormat;

import java.io.IOException;
import java.io.PrintStream;

import java.math.BigDecimal;

import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import static java.lang.String.format;
import static java.lang.System.err;
import static java.lang.System.exit;
import static java.lang.System.out;

/**
 * A command-line client for the Bisq gRPC API.
 */
@Slf4j
public class CliMain {

    private enum Method {
        getversion,
        getbalance,
        lockwallet,
        unlockwallet,
        removewalletpassword,
        setwalletpassword
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable t) {
            err.println("Error: " + t.getMessage());
            exit(1);
        }
    }

    public static void run(String[] args) {
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

        OptionSet options = parser.parse(args);

        if (options.has(helpOpt)) {
            printHelp(parser, out);
            return;
        }

        @SuppressWarnings("unchecked")
        var nonOptionArgs = (List<String>) options.nonOptionArguments();
        if (nonOptionArgs.isEmpty()) {
            printHelp(parser, err);
            throw new IllegalArgumentException("no method specified");
        }

        var methodName = nonOptionArgs.get(0);
        final Method method;
        try {
            method = Method.valueOf(methodName);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(format("'%s' is not a supported method", methodName));
        }

        var host = options.valueOf(hostOpt);
        var port = options.valueOf(portOpt);
        var password = options.valueOf(passwordOpt);
        if (password == null)
            throw new IllegalArgumentException("missing required 'password' option");

        var credentials = new PasswordCallCredentials(password);

        var channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
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
                    return;
                }
                case getbalance: {
                    var request = GetBalanceRequest.newBuilder().build();
                    var reply = walletService.getBalance(request);
                    var satoshiBalance = reply.getBalance();
                    var satoshiDivisor = new BigDecimal(100000000);
                    var btcFormat = new DecimalFormat("###,##0.00000000");
                    @SuppressWarnings("BigDecimalMethodWithoutRoundingCalled")
                    var btcBalance = btcFormat.format(BigDecimal.valueOf(satoshiBalance).divide(satoshiDivisor));
                    out.println(btcBalance);
                    return;
                }
                case lockwallet: {
                    var request = LockWalletRequest.newBuilder().build();
                    walletService.lockWallet(request);
                    out.println("wallet locked");
                    return;
                }
                case unlockwallet: {
                    if (nonOptionArgs.size() < 2)
                        throw new IllegalArgumentException("no password specified");

                    if (nonOptionArgs.size() < 3)
                        throw new IllegalArgumentException("no unlock timeout specified");

                    long timeout;
                    try {
                        timeout = Long.parseLong(nonOptionArgs.get(2));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(format("'%s' is not a number", nonOptionArgs.get(2)));
                    }
                    var request = UnlockWalletRequest.newBuilder()
                            .setPassword(nonOptionArgs.get(1))
                            .setTimeout(timeout).build();
                    walletService.unlockWallet(request);
                    out.println("wallet unlocked");
                    return;
                }
                case removewalletpassword: {
                    if (nonOptionArgs.size() < 2)
                        throw new IllegalArgumentException("no password specified");

                    var request = RemoveWalletPasswordRequest.newBuilder().setPassword(nonOptionArgs.get(1)).build();
                    walletService.removeWalletPassword(request);
                    out.println("wallet decrypted");
                    return;
                }
                case setwalletpassword: {
                    if (nonOptionArgs.size() < 2)
                        throw new IllegalArgumentException("no password specified");

                    var requestBuilder = SetWalletPasswordRequest.newBuilder().setPassword(nonOptionArgs.get(1));
                    var hasNewPassword = nonOptionArgs.size() == 3;
                    if (hasNewPassword)
                        requestBuilder.setNewPassword(nonOptionArgs.get(2));
                    walletService.setWalletPassword(requestBuilder.build());
                    out.println("wallet encrypted" + (hasNewPassword ? " with new password" : ""));
                    return;
                }
                default: {
                    throw new RuntimeException(format("unhandled method '%s'", method));
               }
            }
        } catch (StatusRuntimeException ex) {
            // Remove the leading gRPC status code (e.g. "UNKNOWN: ") from the message
            String message = ex.getMessage().replaceFirst("^[A-Z_]+: ", "");
            throw new RuntimeException(message, ex);
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
            stream.format("%-19s%-30s%s%n", "unlockwallet", "password timeout",
                    "Store wallet password in memory for timeout seconds");
            stream.format("%-19s%-30s%s%n", "setwalletpassword", "password [newpassword]",
                    "Encrypt wallet with password, or set new password on encrypted wallet");
            stream.println();
        } catch (IOException ex) {
            ex.printStackTrace(stream);
        }
    }
}
