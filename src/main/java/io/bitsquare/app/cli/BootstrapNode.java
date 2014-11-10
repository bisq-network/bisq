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

import io.bitsquare.app.ArgumentParser;
import io.bitsquare.network.Node;

import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.nat.PeerBuilderNAT;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.peers.PeerMap;
import net.tomp2p.peers.PeerMapConfiguration;
import net.tomp2p.rpc.ObjectDataReply;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.argparse4j.inf.Namespace;

public class BootstrapNode {
    private static final Logger log = LoggerFactory.getLogger(BootstrapNode.class);

    private static Peer peer = null;
    private static boolean running = true;

    public static void main(String[] args) throws Exception {
        ArgumentParser parser = new ArgumentParser();
        Namespace namespace = parser.parseArgs(args);

        String name = namespace.getString(Node.NAME_KEY);
        if (name == null)
            throw new IllegalArgumentException(String.format("--%s option is required", Node.NAME_KEY));

        String portValue = namespace.getString(Node.PORT_KEY);
        int port = portValue != null ? Integer.valueOf(portValue) : Node.DEFAULT_PORT;

        try {
            Number160 peerId = Number160.createHash(name);
            PeerMapConfiguration pmc = new PeerMapConfiguration(peerId).peerNoVerification();
            PeerMap pm = new PeerMap(pmc);
            peer = new PeerBuilder(peerId).ports(port).peerMap(pm).start();
            peer.objectDataReply(new ObjectDataReply() {
                @Override
                public Object reply(PeerAddress sender, Object request) throws Exception {
                    log.trace("received request: ", request.toString());
                    return "pong";
                }
            });

            new PeerBuilderDHT(peer).start();
            new PeerBuilderNAT(peer).start();

            log.debug("started");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (running) {
                        for (PeerAddress pa : peer.peerBean().peerMap().all()) {
                            log.debug("peer online:" + pa);
                        }
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }
            }).start();

        } catch (Exception e) {
            if (peer != null)
                peer.shutdown().awaitUninterruptibly();
        }
    }

    public static void stop() {
        running = false;
        if (peer != null) {
            peer.shutdown().awaitUninterruptibly();
        }
        peer = null;
    }
}
