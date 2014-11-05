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

import net.tomp2p.connection.Bindings;
import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.nat.PeerBuilderNAT;
import net.tomp2p.nat.PeerNAT;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.peers.PeerMapChangeListener;
import net.tomp2p.peers.PeerStatistic;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

public class DHTManager extends AbstractActor {

    public static final String MY_NODE = "myNodeDhtManager";
    public static final String SEED_NODE = "seedNodeDhtManager";

    private final LoggingAdapter log = Logging.getLogger(context().system(), this);

    // TODO move into app setup
    // timeout in ms
    private final Long bootstrapTimeout = 10000L;

    public static Props getProps() {
        return Props.create(DHTManager.class);
    }

    private Bindings bindings;
    private Peer peer;
    private PeerDHT peerDHT;
    private PeerNAT peerNAT;

    public DHTManager() {
        receive(ReceiveBuilder
                        .match(InitializePeer.class, initializePeer -> doInitializePeer(initializePeer))
                        .matchAny(o -> log.info("received unknown message")).build()
        );
    }

    private void doInitializePeer(InitializePeer initializePeer) {
        log.debug("Received message: {}", initializePeer);

        try {
            bindings = new Bindings();

            // TODO: @Steve: Is that needed that we restrict to IP4?
            // bindings.addProtocol(StandardProtocolFamily.INET); 

            if (initializePeer.getInterfaceHint() != null) {
                bindings.addInterface(initializePeer.getInterfaceHint());
            }

            peer = new PeerBuilder(initializePeer.getPeerId()).ports(initializePeer.getPort()).bindings(bindings)
                    .start();

            // For the moment we want not to bootstrap to other seed nodes to keep test scenarios
            // simple
            /* if (ip.getBootstrapPeers() != null && ip.getBootstrapPeers().size() > 0) {
                peer.bootstrap().bootstrapTo(ip.getBootstrapPeers()).start();
            }*/
            peerDHT = new PeerBuilderDHT(peer).start();
            peerNAT = new PeerBuilderNAT(peer).start();

            peer.peerBean().peerMap().addPeerMapChangeListener(new PeerMapChangeListener() {
                @Override
                public void peerInserted(PeerAddress peerAddress, boolean verified) {
                    log.debug("Peer inserted: peerAddress=" + peerAddress + ", " +
                            "verified=" + verified);
                }

                @Override
                public void peerRemoved(PeerAddress peerAddress, PeerStatistic peerStatistics) {
                    log.debug("Peer removed: peerAddress=" + peerAddress + ", " +
                            "peerStatistics=" + peerStatistics);
                }

                @Override
                public void peerUpdated(PeerAddress peerAddress, PeerStatistic peerStatistics) {
                    // log.debug("Peer updated: peerAddress=" + peerAddress + ",
                    // peerStatistics=" + peerStatistics);
                }
            });

            sender().tell(new PeerInitialized(peer.peerID(), initializePeer.getPort()), self());
        } catch (Throwable t) {
            log.info("The second instance has been started. If that happens at the first instance" +
                    " we are in trouble... " + t.getMessage());
            sender().tell(new PeerInitialized(null, null), self());
        }
    }


    @Override
    public void postStop() throws Exception {
        log.debug("postStop");
        if (peerDHT != null)
            peerDHT.shutdown();

        if (peerNAT != null)
            peerNAT.natUtils().shutdown();

        super.postStop();
    }
}

