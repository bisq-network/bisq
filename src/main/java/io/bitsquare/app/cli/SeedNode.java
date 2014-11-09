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
import io.bitsquare.network.BootstrapNodes;
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

import static io.bitsquare.msg.MessageModule.*;

public class SeedNode {
    private static final Logger log = LoggerFactory.getLogger(SeedNode.class);

    private static Peer peer = null;
    private static boolean running = true;

    public static void main(String[] args) throws Exception {
        ArgumentParser parser = new ArgumentParser();
        Namespace namespace = parser.parseArgs(args);

        Node defaultNode = BootstrapNodes.DIGITAL_OCEAN_1;
        String id = defaultNode.getId();
        int port = defaultNode.getPort();

        // Passed program args will override the properties of the default bootstrapNode
        // So you can use the same id but different ports (e.g. running several nodes on one server with 
        // different ports)
        if (namespace.getString(BOOTSTRAP_NODE_ID_KEY) != null)
            id = namespace.getString(BOOTSTRAP_NODE_ID_KEY);

        if (namespace.getString(BOOTSTRAP_NODE_PORT_KEY) != null)
            port = Integer.valueOf(namespace.getString(BOOTSTRAP_NODE_PORT_KEY));

        try {
            Number160 peerId = Number160.createHash(id);
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

            log.debug("SeedNode started.");
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
                            e.printStackTrace();
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
    
    
    /*public static void main(String[] args) throws Exception {
        ArgumentParser parser = new ArgumentParser();
        Namespace namespace = parser.parseArgs(args);

        Node defaultNode = BootstrapNodes.DIGITAL_OCEAN_1;
        String id = defaultNode.getId();
        int port = defaultNode.getPort();

        // Passed program args will override the properties of the default bootstrapNode
        // So you can use the same id but different ports (e.g. running several nodes on one server with 
        // different ports)
        if (namespace.getString(ArgumentParser.SEED_ID_FLAG) != null)
            id = namespace.getString(ArgumentParser.SEED_ID_FLAG);

        if (namespace.getString(ArgumentParser.SEED_PORT_FLAG) != null)
            port = Integer.valueOf(namespace.getString(ArgumentParser.SEED_PORT_FLAG));

        log.info("This node use ID: [" + id + "] and port: [" + port + "]");

        Peer peer = null;
        try {
            // Lets test with different settings
            ChannelServerConfiguration csc = PeerBuilder.createDefaultChannelServerConfiguration();
            csc.ports(new Ports(Node.DEFAULT_PORT, Node.DEFAULT_PORT));
            csc.portsForwarding(new Ports(Node.DEFAULT_PORT, Node.DEFAULT_PORT));
            csc.connectionTimeoutTCPMillis(10 * 1000);
            csc.idleTCPSeconds(10);
            csc.idleUDPSeconds(10);

            Bindings bindings = new Bindings();
            bindings.addProtocol(StandardProtocolFamily.INET);

            peer = new PeerBuilder(Number160.createHash(id)).bindings(bindings)
                    .channelServerConfiguration(csc).ports(port).start();

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
    }*/
}
