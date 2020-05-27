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

import bisq.proto.grpc.CallServiceGrpc;
import bisq.proto.grpc.Params;

import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import java.io.IOException;
import java.io.PrintStream;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static java.lang.System.err;
import static java.lang.System.exit;
import static java.lang.System.out;

/**
 * A command-line client for the Bisq gRPC API.
 */
@Slf4j
public class CliMainV1 {

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

        // The parser accepts this opt if '--help' is passed on the command line.
        // To get help from the server, the client has to be authenticated;
        // params '--password=xyz help' is passed on the command line.
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

        var callService = CallServiceGrpc.newBlockingStub(channel).withCallCredentials(credentials);

        // The joptsimple non-option args in the command line need to be passed as is
        // (quotes included) to the server, instead of being parsed into tokens used to
        // set gRPC service stub fields.  Below, the nonOptionArgs.stream() is re-mapped
        // into a single string, with multi-word tokens enclosed in double quotes.
        // This avoids the scenario where "a new password" client param is passed to the
        // server without its enclosing quotes, and interpreted by the server as three
        // separate params.
        var params = nonOptionArgs.stream()
                .map(p -> {
                    if (p.contains(" ") || p.contains("\t"))
                        return "\"" + p + "\"";
                    else
                        return p;
                })
                .collect(Collectors.joining(" "));

        try {
            var request = Params.newBuilder().setParams(params).build();
            var result = callService.call(request).getResult();
            out.println(result);
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
