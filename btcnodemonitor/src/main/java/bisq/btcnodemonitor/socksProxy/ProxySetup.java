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
import bisq.network.p2p.network.BridgeAddressProvider;
import bisq.network.p2p.network.NewTor;
import bisq.network.tor.TorService;

import bisq.common.config.Config;
import bisq.common.util.SingleThreadExecutorUtils;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;

import java.nio.file.Paths;

import java.io.File;

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
    private TorService torService;

    public ProxySetup(Config config) {
        this.config = config;
        socksProxyFactory = new SocksProxyFactory();
        Socks5ProxyProvider socks5ProxyProvider = new Socks5ProxyProvider("", "");
        socks5ProxyProvider.setSocks5ProxyInternal(socksProxyFactory);
        String networkDirName = config.getBaseCurrencyNetwork().name().toLowerCase();
        torDir = Paths.get(config.appDataDir.getPath(), networkDirName, "tor").toFile();
    }

    public CompletableFuture<Optional<Socks5Proxy>> createSocksProxy() {
        log.info("createSocksProxy");
        if (!config.useTorForBtcMonitor) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        checkArgument(torService == null);
        return CompletableFuture.supplyAsync(() -> {
            torMode = new NewTor(torDir, null, "", BridgeAddressProvider.NONE);
            // blocking
            torService = torMode.getTor();
            socksProxyFactory.setTorService(torService);
            return Optional.of(socksProxyFactory.getSocksProxy());
        }, SingleThreadExecutorUtils.getSingleThreadExecutor("ProxySetup.start"));
    }

    public CompletableFuture<Void> shutdown() {
        log.info("Shutdown tor");
        return CompletableFuture.runAsync(() -> {
                    if (torService != null) {
                        torService.shutdown().join();
                        log.info("Tor shutdown completed");
                    }
                })
                .orTimeout(2, TimeUnit.SECONDS);
    }
}
