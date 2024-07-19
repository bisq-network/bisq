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

package bisq.btcnodemonitor.socksProxy;

import bisq.network.Socks5ProxyProvider;
import bisq.network.p2p.network.NewTor;

import bisq.common.config.Config;
import bisq.common.util.SingleThreadExecutorUtils;

import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;

import java.nio.file.Paths;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class ProxySetup {
    @Getter
    private final File torDir;
    private final SocksProxyFactory socksProxyFactory;
    private final Config config;
    private NewTor torMode;
    private Tor tor;

    public ProxySetup(Config config) {
        this.config = config;
        socksProxyFactory = new SocksProxyFactory("127.0.0.1");
        Socks5ProxyProvider socks5ProxyProvider = new Socks5ProxyProvider("", "");
        socks5ProxyProvider.setSocks5ProxyInternal(socksProxyFactory);
        String networkDirName = config.baseCurrencyNetwork.name().toLowerCase();
        torDir = Paths.get(config.appDataDir.getPath(), networkDirName, "tor").toFile();
    }

    public CompletableFuture<Optional<Socks5Proxy>> createSocksProxy() {
        log.info("createSocksProxy");
        if (!config.useTorForBtcMonitor) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        checkArgument(tor == null);
        return CompletableFuture.supplyAsync(() -> {
            torMode = new NewTor(torDir, null, "", ArrayList::new);
            try {
                // blocking
                tor = torMode.getTor();
                socksProxyFactory.setTor(tor);
                return Optional.of(socksProxyFactory.getSocksProxy());
            } catch (IOException | TorCtlException e) {
                throw new RuntimeException(e);
            }
        }, SingleThreadExecutorUtils.getSingleThreadExecutor("ProxySetup.start"));
    }

    public CompletableFuture<Void> shutdown() {
        log.info("Shutdown tor");
        return CompletableFuture.runAsync(() -> {
                    if (tor != null) {
                        tor.shutdown();
                        log.info("Tor shutdown completed");
                    }
                })
                .orTimeout(2, TimeUnit.SECONDS);
    }
}
