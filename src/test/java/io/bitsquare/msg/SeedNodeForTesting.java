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

package io.bitsquare.msg;

import io.bitsquare.network.Node;

import net.tomp2p.connection.Bindings;
import net.tomp2p.connection.ChannelServerConfiguration;
import net.tomp2p.connection.Ports;
import net.tomp2p.connection.StandardProtocolFamily;
import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.nat.PeerBuilderNAT;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used for testing with {@link TomP2PTests}
 */
public class SeedNodeForTesting {
    private static final Logger log = LoggerFactory.getLogger(SeedNodeForTesting.class);

    public static void main(String[] args) throws Exception {
        Peer peer = null;
        try {
            ChannelServerConfiguration csc = PeerBuilder.createDefaultChannelServerConfiguration();
            csc.ports(new Ports(Node.DEFAULT_PORT, Node.DEFAULT_PORT));
            csc.portsForwarding(new Ports(Node.DEFAULT_PORT, Node.DEFAULT_PORT));
            csc.connectionTimeoutTCPMillis(10 * 1000);
            csc.idleTCPSeconds(10);
            csc.idleUDPSeconds(10);

            Bindings bindings = new Bindings();
            bindings.addProtocol(StandardProtocolFamily.INET);

            peer = new PeerBuilder(Number160.createHash("localhost")).bindings(bindings)
                    .channelServerConfiguration(csc).ports(Node.DEFAULT_PORT).start();

            peer.objectDataReply((sender, request) -> {
                log.trace("received request: ", request.toString());
                return "pong";
            });

            // Needed for DHT support
            new PeerBuilderDHT(peer).start();
            // Needed for NAT support
            new PeerBuilderNAT(peer).start();

            log.debug("SeedNode started.");
            for (; ; ) {
                for (PeerAddress pa : peer.peerBean().peerMap().all()) {
                    log.debug("peer online:" + pa);
                }
                Thread.sleep(2000);
            }
        } catch (Exception e) {
            if (peer != null)
                peer.shutdown().awaitUninterruptibly();
        }
    }
}
