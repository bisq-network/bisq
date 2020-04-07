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
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import org.apache.commons.codec.binary.Hex;

import javax.net.ssl.SSLException;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.io.File;
import java.io.IOException;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

import static bisq.cli.app.CommandParser.GETBALANCE;
import static bisq.cli.app.CommandParser.GETVERSION;
import static bisq.cli.app.CommandParser.HELP;
import static bisq.cli.app.CommandParser.STOPSERVER;
import static java.lang.String.format;
import static java.lang.System.err;
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
        try {
            String certPath = "cert/aes256/server.crt";
            NettyChannelBuilder channelBuilder = NettyChannelBuilder.forAddress("localhost", 9998)
                    .sslContext(GrpcSslContexts.forClient().trustManager(new File(certPath)).build());
            new BisqCliMain(channelBuilder.build(), args);
        } catch (SSLException e) {
            e.printStackTrace();
            exit(EXIT_FAILURE);
        }
    }

    /**
     * Construct client for accessing server using the existing channel.
     */
    private BisqCliMain(ManagedChannel channel, String[] args) {
        this.channel = channel;
        this.cmd = new CliCommand(channel, loadMacaroon());
        this.parser = new CommandParser().configure();
        String command = parseCommand(args);
        String result = runCommand(command);
        out.println(result);
        try {
            shutdown(); // Orderly channel shutdown
        } catch (InterruptedException ignored) {
        }
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

    private String loadMacaroon() {
        String macaroonPath = appDataDirHack.get() + File.separatorChar + "bisqd.macaroon";
        try {
            return Hex.encodeHexString(Files.readAllBytes(Paths.get(macaroonPath)));
        } catch (IOException e) {
            err.println("Error encoding authentication token " + macaroonPath);
            exit(EXIT_FAILURE);
            return null;
        }
    }

    private void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
        exit(EXIT_SUCCESS);
    }

    // TODO Avoid duplicating these methods from bisq.common.util.Utilities, which are not visible to :cli.
    private final Supplier<String> os = () -> System.getProperty("os.name").toLowerCase(Locale.US);
    private final Supplier<Boolean> isLinux = () -> os.get().contains("linux");
    private final Supplier<Boolean> isOSX = () -> os.get().contains("mac") || os.get().contains("osx");
    private final Supplier<String> appDataDirHack = () -> {
        String userHome = System.getProperty("user.home");
        if (isLinux.get()) {
            return userHome + File.separatorChar + ".local/share/Bisq";
        } else if (isOSX.get()) {
            return userHome + File.separatorChar + "Library/Application Support/Bisq";
        } else {
            throw new RuntimeException("OS " + os.get() + " not supported");
        }
    };
}
