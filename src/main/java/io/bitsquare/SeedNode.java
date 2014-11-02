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

package io.bitsquare;

import io.bitsquare.msg.SeedNodeAddress;

import java.io.IOException;

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

public class SeedNode extends Thread {
    private static final Logger log = LoggerFactory.getLogger(SeedNode.class);

    public static void main(String[] args) {
        Peer peer = null;
        SeedNodeAddress.StaticSeedNodeAddresses seedNodeAddress = SeedNodeAddress.StaticSeedNodeAddresses
                .DIGITAL_OCEAN1;
        try {
            peer = new PeerBuilder(Number160.createHash(seedNodeAddress.getId())).ports(seedNodeAddress.getPort())
                    .start();
            PeerDHT peerDHT = new PeerBuilderDHT(peer).start();
            new PeerBuilderNAT(peer).start();

           /* peerDHT.peer().objectDataReply((sender, request) -> {
                log.trace("received request: ", request.toString());
                return "pong";
            });*/

            log.debug("peer listening at port: {}", seedNodeAddress.getPort());

            peer.peerBean().peerMap().addPeerMapChangeListener(new PeerMapChangeListener() {
                @Override
                public void peerInserted(PeerAddress peerAddress, boolean verified) {
                    log.debug("Peer inserted: peerAddress=" + peerAddress + ", verified=" + verified);
                }

                @Override
                public void peerRemoved(PeerAddress peerAddress, PeerStatistic peerStatistics) {
                    log.debug("Peer removed: peerAddress=" + peerAddress + ", peerStatistics=" + peerStatistics);
                }

                @Override
                public void peerUpdated(PeerAddress peerAddress, PeerStatistic peerStatistics) {
                    // log.debug("Peer updated: peerAddress=" + peerAddress + ", 
                    // peerStatistics=" + peerStatistics);
                }
            });

            final Peer _peer = peer;
            Thread seedNodeThread = new Thread(() -> {
                while (true) {
                    try {
                        for (PeerAddress pa : _peer.peerBean().peerMap().all()) {
                            System.out.println("Peer online:" + pa);
                        }
                        Thread.sleep(5000L);
                    } catch (InterruptedException e) {
                    }
                }
            });
            seedNodeThread.start();

        } catch (IOException e) {
            e.printStackTrace();
            if (peer != null)
                peer.shutdown().awaitUninterruptibly();
        }
    }


}
