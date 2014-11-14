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

package io.bitsquare.app.cli;

import io.bitsquare.network.Node;

import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.nat.PeerBuilderNAT;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.peers.PeerMapChangeListener;
import net.tomp2p.peers.PeerStatistic;
import net.tomp2p.replication.IndirectReplication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.env.Environment;

public class BootstrapNode {
    private static final Logger log = LoggerFactory.getLogger(BootstrapNode.class);

    private static Peer peer = null;
    private static boolean running = true;

    private final Environment env;

    public BootstrapNode(Environment env) {
        this.env = env;
    }

    public void start() {
        String name = env.getRequiredProperty(Node.NAME_KEY);
        int port = env.getProperty(Node.PORT_KEY, Integer.class, Node.DEFAULT_PORT);

        try {
            Number160 peerId = Number160.createHash(name);
            peer = new PeerBuilder(peerId).ports(port).start();
            peer.objectDataReply((sender, request) -> {
                log.trace("received request: " + request.toString());
                return "pong";
            });

            PeerDHT peerDHT = new PeerBuilderDHT(peer).start();
            new PeerBuilderNAT(peer).start();
            new IndirectReplication(peerDHT).start();

            peer.peerBean().peerMap().addPeerMapChangeListener(new PeerMapChangeListener() {
                @Override
                public void peerInserted(PeerAddress peerAddress, boolean verified) {
                    log.debug("peerInserted: " + peerAddress);
                }

                @Override
                public void peerRemoved(PeerAddress peerAddress, PeerStatistic storedPeerAddress) {
                    log.debug("peerRemoved: " + peerAddress);
                }

                @Override
                public void peerUpdated(PeerAddress peerAddress, PeerStatistic storedPeerAddress) {
                }
            });

            log.debug("Bootstrap node started with name " + name + " and port " + port);
            new Thread(() -> {
                while (running) {
                    for (PeerAddress peerAddress : peer.peerBean().peerMap().all()) {
                        log.debug("Peer online: " + peerAddress);
                    }
                    try {
                        Thread.sleep(60000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }).start();

        } catch (Exception e) {
            if (peer != null)
                peer.shutdown().awaitUninterruptibly();
        }
    }
}
