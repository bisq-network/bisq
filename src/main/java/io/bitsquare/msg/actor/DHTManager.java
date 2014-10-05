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

package io.bitsquare.msg.actor;

import io.bitsquare.msg.actor.command.InitializePeer;
import io.bitsquare.msg.actor.event.PeerInitialized;

import net.tomp2p.connection.Ports;
import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.futures.FutureBootstrap;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

public class DHTManager extends AbstractActor {

    public static final String PEER_NAME = "peerDhtManager";
    public static final String SEED_NAME = "seedDhtManager";

    private final LoggingAdapter log = Logging.getLogger(context().system(), this);

    // TODO move into app setup
    // timeout in ms
    private final Long bootstrapTimeout = 10000L;

    public static Props getProps() {
        return Props.create(DHTManager.class);
    }

    private Peer peer;
    private PeerDHT peerDHT;

    public DHTManager() {

        receive(ReceiveBuilder
                        .match(InitializePeer.class, ip -> {
                            log.debug("Received message: {}", ip);

                            peer = new PeerBuilder(ip.getPeerId())
                                    .ports(ip.getPort() != null ? ip.getPort() : new Ports().tcpPort()).start();
                            peerDHT = new PeerBuilderDHT(peer).start();

                            // TODO add code to discover non-local peers
                            // FutureDiscover futureDiscover = peer.discover().peerAddress(bootstrapPeers.).start();
                            // futureDiscover.awaitUninterruptibly();

                            if (ip.getBootstrapPeers() != null) {
                                FutureBootstrap futureBootstrap = peer.bootstrap()
                                        .bootstrapTo(ip.getBootstrapPeers()).start();
                                futureBootstrap.awaitUninterruptibly(bootstrapTimeout);
                            }
                            sender().tell(new PeerInitialized(peer.peerID()), self());
                        })
                        .matchAny(o -> log.info("received unknown message")).build()
        );
    }

    @Override
    public void postStop() throws Exception {
        log.debug("postStop");
        peerDHT.shutdown();
        super.postStop();
    }
}

