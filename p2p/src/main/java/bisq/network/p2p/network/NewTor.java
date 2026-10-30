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

import bisq.network.tor.TorService;
import bisq.network.tor.TorTransportConfig;
import bisq.network.tor.controller.exceptions.TorBootstrapFailedException;

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
    static final String BRIDGES_NOT_SUPPORTED = "Bisq cannot use the configured Tor bridges yet and does not " +
            "connect without them, because your network could then see that you use Tor, and Tor may be blocked. " +
            "To connect without bridges, remove them in the Tor network settings.";

    private final File torrcFile;
    private final String torrcOptions;
    private final BridgeAddressProvider bridgeAddressProvider;

    public NewTor(File torWorkingDirectory, @Nullable File torrcFile, String torrcOptions, BridgeAddressProvider bridgeAddressProvider) {
        super(torWorkingDirectory);
        this.torrcFile = torrcFile;
        this.torrcOptions = torrcOptions;
        this.bridgeAddressProvider = bridgeAddressProvider;
    }

    @Override
    public TorService getTor() {
        long ts1 = new Date().getTime();

        if (!getBridgeLines(bridgeAddressProvider.getBridgeAddresses()).isEmpty()) {
            // TODO Use the bridges once the Tor library supports bridges and pluggable transports. Until then Tor
            //  must not start: the user relies on the bridges to get around a block or to hide the use of Tor.
            throw new IllegalStateException(BRIDGES_NOT_SUPPORTED);
        }

        TorTransportConfig transportConfig = new TorTransportConfig(torDir.toPath(),
                BOOTSTRAP_TIMEOUT,
                HS_UPLOAD_TIMEOUT,
                false,
                Set.of(),
                getTorrcOverrides(torrcFile, torrcOptions),
                false);
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
                if (attempt == START_ATTEMPTS ||
                        !(Throwables.getRootCause(e) instanceof TorBootstrapFailedException)) {
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
