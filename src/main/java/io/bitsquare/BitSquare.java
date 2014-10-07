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

import io.bitsquare.msg.actor.DHTManager;
import io.bitsquare.msg.actor.command.InitializePeer;
import io.bitsquare.msg.actor.event.PeerInitialized;
import io.bitsquare.util.BitsquareArgumentParser;

import java.util.concurrent.TimeoutException;

import javafx.application.Application;

import net.tomp2p.peers.Number160;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Inbox;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

public class BitSquare {

    private static String appName = "Bitsquare";

    public static String getAppName() {
        return appName;
    }

    public static void main(String[] args) {

        BitsquareArgumentParser parser = new BitsquareArgumentParser();
        Namespace namespace = null;
        try {
            namespace = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
        if (namespace != null) {

            if (namespace.getString(BitsquareArgumentParser.NAME_FLAG) != null) {
                appName = appName + "-" + namespace.getString(BitsquareArgumentParser.NAME_FLAG);
            }

            Integer port = BitsquareArgumentParser.PORT_DEFAULT;
            if (namespace.getString(BitsquareArgumentParser.PORT_FLAG) != null) {
                port = Integer.valueOf(namespace.getString(BitsquareArgumentParser.PORT_FLAG));
            }
            if (namespace.getBoolean(BitsquareArgumentParser.SEED_FLAG) == true) {
                ActorSystem actorSystem = ActorSystem.create(getAppName());

                ActorRef seedNode = actorSystem.actorOf(DHTManager.getProps(), DHTManager.SEED_NAME);
                Inbox inbox = Inbox.create(actorSystem);
                inbox.send(seedNode, new InitializePeer(Number160.createHash("localhost"), port, null));

                Thread seedNodeThread = new Thread(() -> {
                    Boolean quit = false;
                    while (!quit) {
                        try {
                            Object m = inbox.receive(FiniteDuration.create(5L, "seconds"));
                            if (m instanceof PeerInitialized) {
                                System.out.println("Seed Peer Initialized on port " + ((PeerInitialized) m).getPort
                                        ());
                            }
                        } catch (Exception e) {
                            if (!(e instanceof TimeoutException)) {
                                quit = true;
                            }
                        }
                    }
                    actorSystem.shutdown();
                    try {
                        actorSystem.awaitTermination(Duration.create(5L, "seconds"));
                    } catch (Exception ex) {
                        if (ex instanceof TimeoutException)
                            System.out.println("ActorSystem did not shutdown properly.");
                        else
                            System.out.println(ex.getMessage());
                    }
                });
                seedNodeThread.start();
            }
            else {
                Application.launch(BitSquareUI.class, args);
            }
        }
    }
}
