/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.app.bootstrap;

import io.bitsquare.app.Logging;
import io.bitsquare.app.Version;
import io.bitsquare.p2p.BootstrapNodes;
import io.bitsquare.p2p.Node;

import java.util.List;
import java.util.stream.Collectors;

import net.tomp2p.connection.ChannelClientConfiguration;
import net.tomp2p.connection.ChannelServerConfiguration;
import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.nat.PeerBuilderNAT;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.peers.PeerMapChangeListener;
import net.tomp2p.peers.PeerStatistic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.DefaultEventExecutorGroup;
import org.springframework.core.env.Environment;

public class BootstrapNode {
    private static final Logger log = LoggerFactory.getLogger(BootstrapNode.class);

    private static Peer peer = null;

    private final Environment env;
    private boolean noPeersInfoPrinted;

    public BootstrapNode(Environment env) {
        this.env = env;
    }

    public void start() {
        BootstrapNodes bootstrapNodes = new BootstrapNodes();
        int p2pId = env.getProperty(Node.P2P_ID_KEY, Integer.class, Node.REG_TEST_P2P_ID); // use regtest as default
        bootstrapNodes.initWithNetworkId(p2pId);
        String name = env.getProperty(Node.NAME_KEY, bootstrapNodes.getLocalhostNode().getName());
        int port = env.getProperty(Node.PORT_KEY, Integer.class, bootstrapNodes.getLocalhostNode().getPort());

        Logging.setup(name + "_" + port);

        try {
            Number160 peerId = Number160.createHash(name);

            DefaultEventExecutorGroup eventExecutorGroup = new DefaultEventExecutorGroup(50);
            ChannelClientConfiguration clientConf = PeerBuilder.createDefaultChannelClientConfiguration();
            clientConf.pipelineFilter(new PeerBuilder.EventExecutorGroupFilter(eventExecutorGroup));

            ChannelServerConfiguration serverConf = PeerBuilder.createDefaultChannelServerConfiguration();
            serverConf.pipelineFilter(new PeerBuilder.EventExecutorGroupFilter(eventExecutorGroup));
            serverConf.connectionTimeoutTCPMillis(5000);

            peer = new PeerBuilder(peerId)
                    .ports(port)
                    .p2pId(p2pId)
                    .channelClientConfiguration(clientConf)
                    .channelServerConfiguration(serverConf)
                    .start();
            
            /*peer.objectDataReply((sender, request) -> {
                log.trace("received request: " + request.toString());
                return "pong";
            });*/

            new PeerBuilderDHT(peer).start();
            new PeerBuilderNAT(peer).start();

            final int _port = port;
            if (!name.equals(bootstrapNodes.getLocalhostNode().getName())) {
                List<Node> bootstrapNodesExcludingMyself = bootstrapNodes.getBootstrapNodes().stream().filter(e -> !e.getName().equals
                        (name)).collect(Collectors.toList());
                log.info("Bootstrapping to bootstrapNodes " + bootstrapNodesExcludingMyself);
                long ts = System.currentTimeMillis();
                List<PeerAddress> bootstrapAddressesExcludingMyself = bootstrapNodesExcludingMyself.stream()
                        .map(e -> e.toPeerAddressWithPort(_port)).collect(Collectors.toList());
                peer.bootstrap().bootstrapTo(bootstrapAddressesExcludingMyself).start().awaitUninterruptibly();
                log.info("Bootstrapping done after {} msec", System.currentTimeMillis() - ts);
            }
            else {
                log.info("When using localhost we do not bootstrap to other nodes");
            }
            peer.peerBean().peerMap().addPeerMapChangeListener(new PeerMapChangeListener() {
                @Override
                public void peerInserted(PeerAddress peerAddress, boolean verified) {
                    log.info("Peer inserted: peerAddress=" + peerAddress + ", verified=" + verified);
                }

                @Override
                public void peerRemoved(PeerAddress peerAddress, PeerStatistic peerStatistics) {
                    log.info("Peer removed: peerAddress=" + peerAddress + ", peerStatistics=" + peerStatistics);
                }

                @Override
                public void peerUpdated(PeerAddress peerAddress, PeerStatistic peerStatistics) {
                    //log.info("Peer updated: peerAddress=" + peerAddress + ", peerStatistics=" + peerStatistics);
                }
            });

            log.info("Bootstrap node started with name=" + name + " ,p2pId=" + p2pId + " ,port=" + port +
                    " and network protocol version=" + Version.NETWORK_PROTOCOL_VERSION);
            new Thread(() -> {
                while (true) {
                    if (peer.peerBean().peerMap().all().size() > 0) {
                        noPeersInfoPrinted = false;
                        int relayed = 0;
                        for (PeerAddress peerAddress : peer.peerBean().peerMap().all()) {
                            log.info("Peer: " + peerAddress.toString());
                            if (peerAddress.isRelayed())
                                relayed++;
                        }
                        log.info("Number of peers online = " + peer.peerBean().peerMap().all().size());
                        log.info("Relayed peers = " + relayed);
                    }
                    else if (noPeersInfoPrinted) {
                        log.info("No peers online");
                        noPeersInfoPrinted = true;
                    }
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return;
                    }
                }
            }).start();

        } catch (Throwable t) {
            log.error("Fatal exception " + t.getMessage());
            if (peer != null)
                peer.shutdown().awaitUninterruptibly();
        }
    }
}
