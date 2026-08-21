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

package bisq.btcnodemonitor;


import bisq.core.btc.nodes.BtcNodes;

import bisq.common.config.Config;

import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;



import bisq.btcnodemonitor.btc.PeerConncetionModel;
import bisq.btcnodemonitor.btc.PeerGroupService;
import bisq.btcnodemonitor.server.SimpleHttpServer;
import bisq.btcnodemonitor.socksProxy.ProxySetup;

@Slf4j
public class BtcNodeMonitor {
    private final PeerGroupService peerGroupService;
    private final ProxySetup proxySetup;
    private final SimpleHttpServer simpleHttpServer;

    public BtcNodeMonitor(Config config) {
        PeerConncetionModel peerConncetionModel = new PeerConncetionModel(new BtcNodes().getProvidedBtcNodes(), this::onChange);
        simpleHttpServer = new SimpleHttpServer(config, peerConncetionModel);
        proxySetup = new ProxySetup(config);
        peerGroupService = new PeerGroupService(config, peerConncetionModel);
    }

    public void onChange() {
        simpleHttpServer.onChange();
    }

    public CompletableFuture<Void> start() {
        return simpleHttpServer.start()
                .thenCompose(nil -> proxySetup.createSocksProxy())
                .thenAccept(peerGroupService::applySocks5Proxy)
                .thenCompose(nil -> peerGroupService.start());
    }

    public CompletableFuture<Void> shutdown() {
        return peerGroupService.shutdown()
                .thenCompose(nil -> proxySetup.shutdown())
                .thenCompose(nil -> simpleHttpServer.shutdown());
    }
}
