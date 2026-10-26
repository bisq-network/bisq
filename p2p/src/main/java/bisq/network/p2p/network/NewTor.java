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

package bisq.network.p2p.network;

import bisq.network.tor.BridgeConfig;
import bisq.network.tor.ExternalTorUnavailableException;
import bisq.network.tor.TorSandboxConfig;
import bisq.network.tor.TorService;
import bisq.network.tor.TorTransportConfig;
import bisq.network.tor.controller.exceptions.TorBootstrapFailedException;

import bisq.common.config.Config;

import com.google.common.base.Throwables;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * This class starts Tor with the Tor library.
 *
 * When asked, the class checks, whether command line parameters such as
 * --torrcFile and --torrcOptions are set and if so, takes these settings into
 * account. Then the Tor library starts Tor, or connects to a running Tor if
 * external_tor.config in the Tor directory or the environment variable
 * TOR_SKIP_LAUNCH says so, or on Whonix. Finally, the {@link TorService} is
 * returned for further use.
 *
 * @author Florian Reimair
 *
 */
@Slf4j
public class NewTor extends TorMode {
    private static final int BOOTSTRAP_TIMEOUT = (int) TimeUnit.MINUTES.toMillis(4);
    // netlayer waited for the upload of the onion service descriptor without a time limit. Tor keeps trying to upload
    // it, so giving up early would leave a node which cannot be reached.
    private static final int HS_UPLOAD_TIMEOUT = Integer.MAX_VALUE;
    // netlayer started Tor up to 5 times, because Tor sometimes does not bootstrap with the files of an earlier run,
    // and deleted these files before each new attempt.
    private static final int START_ATTEMPTS = 5;

    static final String SANDBOX_OPT_OUT_HINT = "Start Bisq with the option --" + Config.USE_TOR_SANDBOX +
            "=false to run Tor without the sandbox.";

    private final File torrcFile;
    private final String torrcOptions;
    private final BridgeAddressProvider bridgeAddressProvider;
    private final boolean useTorSandbox;

    public NewTor(File torWorkingDirectory, @Nullable File torrcFile, String torrcOptions, BridgeAddressProvider bridgeAddressProvider) {
        this(torWorkingDirectory, torrcFile, torrcOptions, bridgeAddressProvider, false);
    }

    public NewTor(File torWorkingDirectory,
                  @Nullable File torrcFile,
                  String torrcOptions,
                  BridgeAddressProvider bridgeAddressProvider,
                  boolean useTorSandbox) {
        super(torWorkingDirectory);
        this.torrcFile = torrcFile;
        this.torrcOptions = torrcOptions;
        this.bridgeAddressProvider = bridgeAddressProvider;
        this.useTorSandbox = useTorSandbox;
    }

    @Override
    public TorService getTor() {
        long ts1 = new Date().getTime();

        TorTransportConfig transportConfig = new TorTransportConfig(torDir.toPath(),
                BOOTSTRAP_TIMEOUT,
                HS_UPLOAD_TIMEOUT,
                false,
                Set.of(),
                getTorrcOverrides(torrcFile, torrcOptions),
                false,
                getBridgeConfig(bridgeAddressProvider),
                getSandboxConfig(useTorSandbox));
        for (int attempt = 1; ; attempt++) {
            TorService torService = new TorService(transportConfig, new BisqTorPlatformServices());
            log.info("Starting tor");
            try {
                torService.initialize().join();
                log.info(
                        "\n################################################################\n"
                                + "Tor started after {} ms. Start publishing hidden service.\n"
                                + "################################################################",
                        (new Date().getTime() - ts1)); // takes usually a few seconds
                return torService;
            } catch (RuntimeException e) {
                // TorService stops the Tor process it started when a later step of the start fails.
                if (attempt == START_ATTEMPTS || !canStartAgain(e)) {
                    throw e;
                }
                log.warn("Tor did not bootstrap at attempt {} of {}. We delete the Tor files and start Tor again.",
                        attempt, START_ATTEMPTS, e);
                try {
                    deleteTorFiles(torDir);
                } catch (IOException ioException) {
                    throw new UncheckedIOException(ioException);
                }
            }
        }
    }

    // Only the Tor which the node starts can be started again with fresh files. A running Tor which did not bootstrap
    // in time fails with ExternalTorUnavailableException, and waiting for it four more times and deleting the files of
    // the node's own Tor in between cannot help; the start fails at once, as specified.
    static boolean canStartAgain(RuntimeException e) {
        return Throwables.getCausalChain(e).stream().noneMatch(ExternalTorUnavailableException.class::isInstance) &&
                Throwables.getRootCause(e) instanceof TorBootstrapFailedException;
    }

    // In its sandbox Tor has a network of its own on every platform, so it reaches the onion service of the node only
    // through a port which sandy forwards; the library refuses a sandboxed start without them. A Bisq 1 node publishes
    // one onion service. The sandbox lets Tor start no pluggable transports, so bridges which need them fail the
    // sandboxed start. A running Tor runs without the sandbox.
    static TorSandboxConfig getSandboxConfig(boolean useTorSandbox) {
        return new TorSandboxConfig(useTorSandbox,
                true,
                false,
                1,
                "bisq1",
                SANDBOX_OPT_OUT_HINT);
    }

    // The library uses the bridges only for the Tor which it starts. A running Tor uses its own bridges.
    static BridgeConfig getBridgeConfig(BridgeAddressProvider bridgeAddressProvider) {
        switch (bridgeAddressProvider.getBridgeOption()) {
            case PROVIDED:
                return BridgeConfig.builtIn(bridgeAddressProvider.getBridgeTransport().getBuiltInBridgesName());
            case CUSTOM:
                List<String> bridgeLines = getBridgeLines(bridgeAddressProvider.getCustomBridgeLines());
                if (bridgeLines.isEmpty()) {
                    log.warn("Custom bridges are selected in the Tor network settings, but no bridge is entered. " +
                            "Tor connects without bridges, as with earlier versions.");
                    return BridgeConfig.NONE;
                }
                return BridgeConfig.custom(bridgeLines);
            case NONE:
            default:
                return BridgeConfig.NONE;
        }
    }

    // netlayer used only bridge lines with more than 10 characters, so an empty line of the custom bridges is no
    // bridge.
    static List<String> getBridgeLines(@Nullable Collection<String> bridgeAddresses) {
        if (bridgeAddresses == null) {
            return List.of();
        }
        return bridgeAddresses.stream()
                .filter(line -> line != null && line.trim().length() > 10)
                .collect(Collectors.toList());
    }

    // The Tor library ignores the entries which it needs for its control connection, the Tor data and the stream
    // isolation, in any case and as the abbreviations which Tor accepts.
    static LinkedHashMap<String, String> getTorrcOverrides(@Nullable File torrcFile, String torrcOptions) {
        return TorrcOverrides.parse(torrcFile, torrcOptions);
    }
}
