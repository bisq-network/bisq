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

package bisq.btcnodemonitor.btc;

import bisq.core.btc.nodes.BtcNodes;
import bisq.core.btc.nodes.BtcNodesRepository;
import bisq.core.btc.nodes.LocalBitcoinNode;
import bisq.core.btc.nodes.ProxySocketFactory;

import bisq.common.UserThread;
import bisq.common.config.Config;
import bisq.common.util.SingleThreadExecutorUtils;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.net.BlockingClientManager;
import org.bitcoinj.utils.Threading;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;

import java.time.Duration;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PeerGroupService {
    private final NetworkParameters params;
    private final LocalBitcoinNode localBitcoinNode;
    private final Context context;
    private final PeerConncetionModel peerConncetionModel;
    private Set<PeerConnection> peerConnections;
    private BlockingClientManager blockingClientManager;

    public PeerGroupService(Config config, PeerConncetionModel peerConncetionModel) {
        this.peerConncetionModel = peerConncetionModel;

        params = Config.baseCurrencyNetworkParameters();
        context = new Context(params);
        PeerGroup.setIgnoreHttpSeeds(true);
        Threading.USER_THREAD = UserThread.getExecutor();
        localBitcoinNode = new LocalBitcoinNode(config);
    }

    public void applySocks5Proxy(Optional<Socks5Proxy> socks5Proxy) {
        int connectTimeoutMillis;
        int disconnectIntervalSec;
        int reconnectIntervalSec;
        Set<PeerAddress> peerAddresses;
        if (localBitcoinNode.shouldBeUsed()) {
            InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress().getHostAddress(), params.getPort());
            peerAddresses = Set.of(new PeerAddress(address));
            connectTimeoutMillis = 1000;
            disconnectIntervalSec = 5;
            reconnectIntervalSec = 5;
            blockingClientManager = new BlockingClientManager();
        } else {
            BtcNodes btcNodes = new BtcNodes();
            List<PeerAddress> peerAddressList = btcNodesToPeerAddress(btcNodes, socks5Proxy);
            peerAddresses = new HashSet<>(peerAddressList);
            connectTimeoutMillis = socks5Proxy.map(s -> 60_000).orElse(10_000);
            disconnectIntervalSec = 2;
            reconnectIntervalSec = 120;
            if (socks5Proxy.isPresent()) {
                InetSocketAddress inetSocketAddress = new InetSocketAddress(socks5Proxy.get().getInetAddress(), socks5Proxy.get().getPort());
                Proxy proxy = new Proxy(Proxy.Type.SOCKS, inetSocketAddress);
                ProxySocketFactory proxySocketFactory = new ProxySocketFactory(proxy);
                blockingClientManager = new BlockingClientManager(proxySocketFactory);
            } else {
                blockingClientManager = new BlockingClientManager();
            }
        }
        log.info("Using peer addresses {}", peerAddresses);
        blockingClientManager.setConnectTimeoutMillis(connectTimeoutMillis);
        peerConncetionModel.fill(peerAddresses);
        Set<PeerConncetionInfo> peerConncetionInfoSet = new HashSet<>(peerConncetionModel.getMap().values());
        peerConnections = peerConncetionInfoSet.stream()
                .map(peerConncetionInfo -> new PeerConnection(context,
                        peerConncetionInfo,
                        blockingClientManager,
                        connectTimeoutMillis,
                        disconnectIntervalSec,
                        reconnectIntervalSec))
                .collect(Collectors.toSet());
    }

    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(() -> {
            log.info("start");
            Context.propagate(context);
            blockingClientManager.startAsync();
            blockingClientManager.awaitRunning();

            peerConnections.forEach(PeerConnection::start);
        }, SingleThreadExecutorUtils.getSingleThreadExecutor("start"));
    }

    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            log.info("Shutdown all peerConnections");
            Context.propagate(context);
            CountDownLatch latch = new CountDownLatch(peerConnections.size());
            peerConnections.forEach(peerConnection -> peerConnection.shutdown()
                    .thenRun(latch::countDown));
            try {
                if (latch.await(3, TimeUnit.SECONDS)) {
                    log.info("All peerConnections shut down.");
                } else {
                    log.info("Shutdown of peerConnections not completed in time.");
                }
                blockingClientManager.stopAsync();
                blockingClientManager.awaitTerminated(Duration.ofSeconds(2));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, SingleThreadExecutorUtils.getSingleThreadExecutor("shutdown"));
    }

    private List<PeerAddress> btcNodesToPeerAddress(BtcNodes btcNodes, Optional<Socks5Proxy> proxy) {
        List<BtcNodes.BtcNode> nodes = btcNodes.getProvidedBtcNodes();
        BtcNodesRepository repository = new BtcNodesRepository(nodes);
        return repository.getPeerAddresses(proxy.orElse(null));
    }
}
