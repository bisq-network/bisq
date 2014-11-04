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

import io.bitsquare.msg.SeedNodeAddress;
import io.bitsquare.msg.actor.DHTManager;
import io.bitsquare.msg.actor.command.InitializePeer;
import io.bitsquare.msg.actor.event.PeerInitialized;
import io.bitsquare.util.BitsquareArgumentParser;

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

    private static String appName = "Bitsquare";
    private static String interfaceHint;

    public static void main(String[] args) {
        BitsquareArgumentParser parser = new BitsquareArgumentParser();
        Namespace namespace = parser.parseArgs(args);

        if (namespace.getString(BitsquareArgumentParser.NAME_FLAG) != null) {
            appName = appName + "-" + namespace.getString(BitsquareArgumentParser.NAME_FLAG);
        }

        if (namespace.getString(BitsquareArgumentParser.INFHINT_FLAG) != null) {
            interfaceHint = namespace.getString(BitsquareArgumentParser.INFHINT_FLAG);
        }

        int port = -1;
        if (namespace.getString(BitsquareArgumentParser.PORT_FLAG) != null) {
            port = Integer.valueOf(namespace.getString(BitsquareArgumentParser.PORT_FLAG));
        }

        String seedID = SeedNodeAddress.StaticSeedNodeAddresses.DIGITAL_OCEAN1.getId();
        if (namespace.getString(BitsquareArgumentParser.PEER_ID_FLAG) != null) {
            seedID = namespace.getString(BitsquareArgumentParser.PEER_ID_FLAG);
        }

        ActorSystem actorSystem = ActorSystem.create(appName);

        final Set<PeerAddress> peerAddresses = new HashSet<PeerAddress>();
        final String sid = seedID;
        SeedNodeAddress.StaticSeedNodeAddresses.getAllSeedNodeAddresses().forEach(a -> {
            if (!a.getId().equals(sid)) {
                try {
                    peerAddresses.add(new PeerAddress(Number160.createHash(a.getId()), a.getIp(),
                            a.getPort(), a.getPort()));
                } catch (UnknownHostException uhe) {
                    log.error("Unknown Host [" + a.getIp() + "]: " + uhe.getMessage());
                }
            }
        });

        int serverPort = (port == -1) ? BitsquareArgumentParser.PORT_DEFAULT : port;

        ActorRef seedNode = actorSystem.actorOf(DHTManager.getProps(), DHTManager.SEED_NAME);
        Inbox inbox = Inbox.create(actorSystem);
        inbox.send(seedNode, new InitializePeer(Number160.createHash(sid), serverPort, interfaceHint,
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
