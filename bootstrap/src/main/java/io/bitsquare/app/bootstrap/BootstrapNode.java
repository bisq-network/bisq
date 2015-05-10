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
import io.bitsquare.p2p.BootstrapNodes;
import io.bitsquare.p2p.Node;

import java.util.Collection;
import java.util.stream.Collectors;

import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.nat.PeerBuilderNAT;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.peers.PeerMapChangeListener;
import net.tomp2p.peers.PeerStatistic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.env.Environment;

public class BootstrapNode {
    private static final Logger log = LoggerFactory.getLogger(BootstrapNode.class);

    private static final String VERSION = "0.1.3";

    public static final String P2P_ID = "node.p2pId";
    public static int DEFAULT_P2P_ID = 1; // 0 | 1 | 2 for mainnet/testnet/regtest 

    private static Peer peer = null;

    private final Environment env;
    private boolean noPeersInfoPrinted;

    public BootstrapNode(Environment env) {
        this.env = env;
    }


    public void start() {
        int p2pId = env.getProperty(P2P_ID, Integer.class, DEFAULT_P2P_ID);
        int port = env.getProperty(Node.PORT_KEY, Integer.class, BootstrapNodes.BASE_PORT + p2pId);
        String name = env.getRequiredProperty(Node.NAME_KEY);
        Logging.setup(name + "_" + port);

        try {
            Number160 peerId = Number160.createHash(name);
/*
            DefaultEventExecutorGroup eventExecutorGroup = new DefaultEventExecutorGroup(50);
            ChannelClientConfiguration clientConf = PeerBuilder.createDefaultChannelClientConfiguration();
            clientConf.pipelineFilter(new PeerBuilder.EventExecutorGroupFilter(eventExecutorGroup));

            ChannelServerConfiguration serverConf = PeerBuilder.createDefaultChannelServerConfiguration();
            serverConf.pipelineFilter(new PeerBuilder.EventExecutorGroupFilter(eventExecutorGroup));
            serverConf.connectionTimeoutTCPMillis(5000);*/

            peer = new PeerBuilder(peerId)
                    .ports(port)
                    .p2pId(p2pId)
                  /*  .channelClientConfiguration(clientConf)
                    .channelServerConfiguration(serverConf)*/
                    .start();
            
            /*peer.objectDataReply((sender, request) -> {
                log.trace("received request: " + request.toString());
                return "pong";
            });*/

            PeerDHT peerDHT = new PeerBuilderDHT(peer).start();
            new PeerBuilderNAT(peer).start();

            if (!name.equals(BootstrapNodes.LOCALHOST.getName())) {
                Collection<PeerAddress> bootstrapNodes = BootstrapNodes.getAllBootstrapNodes().stream().filter(e -> !e.getName().equals(name))
                        .map(e -> e.toPeerAddressWithPort(port)).collect(Collectors.toList());

                log.info("Bootstrapping to " + bootstrapNodes.size() + " bootstrapNode(s)");
                log.info("Bootstrapping bootstrapNodes " + bootstrapNodes);
                peer.bootstrap().bootstrapTo(bootstrapNodes).start().awaitUninterruptibly();
            }
            else {
                log.info("Localhost, no bootstrap");
            }
            peer.peerBean().peerMap().addPeerMapChangeListener(new PeerMapChangeListener() {
                @Override
                public void peerInserted(PeerAddress peerAddress, boolean verified) {
                    try {
                        log.info("Peer inserted: peerAddress=" + peerAddress + ", verified=" + verified);
                    } catch (Throwable t) {
                        log.error("Exception at peerInserted " + t.getMessage());
                    }
                }

                @Override
                public void peerRemoved(PeerAddress peerAddress, PeerStatistic peerStatistics) {
                    try {
                        log.info("Peer removed: peerAddress=" + peerAddress + ", peerStatistics=" + peerStatistics);
                    } catch (Throwable t) {
                        log.error("Exception at peerRemoved " + t.getMessage());
                    }
                }

                @Override
                public void peerUpdated(PeerAddress peerAddress, PeerStatistic peerStatistics) {
                    try {
                        log.info("Peer updated: peerAddress=" + peerAddress + ", peerStatistics=" + peerStatistics);
                    } catch (Throwable t) {
                        log.error("Exception at peerUpdated " + t.getMessage());
                    }
                }
            });

            log.info("Bootstrap node started with name " + name + " ,port " + port + " and version " + VERSION);
            new Thread(() -> {
                while (true) {
                    if (peer.peerBean().peerMap().all().size() > 0) {
                        noPeersInfoPrinted = false;
                        try {
                            log.info("Number of peers online = " + peer.peerBean().peerMap().all().size());
                            for (PeerAddress peerAddress : peer.peerBean().peerMap().all()) {
                                log.info("Peer: " + peerAddress.toString());
                            }
                        } catch (Throwable t) {
                            log.error("Exception at run loop " + t.getMessage());
                        }
                    }
                    else if (noPeersInfoPrinted) {
                        log.info("No peers online");
                        noPeersInfoPrinted = true;
                    }
                    try {
                        Thread.sleep(2000);
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
