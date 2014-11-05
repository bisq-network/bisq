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
import io.bitsquare.msg.actor.DHTManager;
import io.bitsquare.msg.actor.command.InitializePeer;
import io.bitsquare.msg.actor.event.PeerInitialized;
import io.bitsquare.network.BootstrapNode;
import io.bitsquare.network.Node;

import java.net.UnknownHostException;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Inbox;
import net.sourceforge.argparse4j.inf.Namespace;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

public class SeedNode {
    private static final Logger log = LoggerFactory.getLogger(SeedNode.class);

    private static String interfaceHint;

    public static void main(String[] args) {
        ArgumentParser parser = new ArgumentParser();
        Namespace namespace = parser.parseArgs(args);

        if (namespace.getString(ArgumentParser.INFHINT_FLAG) != null) {
            interfaceHint = namespace.getString(ArgumentParser.INFHINT_FLAG);
        }

        int serverPort = Integer.valueOf(namespace.getString(ArgumentParser.PORT_FLAG));

        String seedID = BootstrapNode.LOCAL_HOST.getId();
        if (namespace.getString(ArgumentParser.PEER_ID_FLAG) != null) {
            seedID = namespace.getString(ArgumentParser.PEER_ID_FLAG);
        }

        final Set<PeerAddress> peerAddresses = new HashSet<>();
        for (Node node : BootstrapNode.values()) {
            if (!node.getId().equals(seedID)) {
                try {
                    peerAddresses.add(new PeerAddress(Number160.createHash(node.getId()), node.getIp(),
                            node.getPort(), node.getPort()));
                } catch (UnknownHostException uhe) {
                    log.error("Unknown Host [" + node.getIp() + "]: " + uhe.getMessage());
                }
            }
        }

        ActorSystem actorSystem = ActorSystem.create("BitsquareSeedNode");
        Inbox inbox = Inbox.create(actorSystem);
        ActorRef seedNode = actorSystem.actorOf(DHTManager.getProps(), DHTManager.SEED_NODE);
        inbox.send(seedNode, new InitializePeer(Number160.createHash(seedID), serverPort, interfaceHint,
                peerAddresses));

        Thread seedNodeThread = new Thread(() -> {
            Boolean quit = false;
            while (!quit) {
                try {
                    Object m = inbox.receive(FiniteDuration.create(5L, "seconds"));
                    if (m instanceof PeerInitialized) {
                        log.debug("Seed Peer Initialized on port " + ((PeerInitialized) m).getPort
                                ());
                    }
                } catch (Exception e) {
                    if (!(e instanceof TimeoutException)) {
                        quit = true;
                        log.error(e.getMessage());
                    }
                }
            }
            actorSystem.shutdown();
            try {
                actorSystem.awaitTermination(Duration.create(5L, "seconds"));
            } catch (Exception ex) {
                if (ex instanceof TimeoutException)
                    log.error("ActorSystem did not shutdown properly.");
                else
                    log.error(ex.getMessage());
            }
        });
        seedNodeThread.start();
    }
}
