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

/*
 * Copyright 2011 Thomas Bocek
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package net.tomp2p.examples;

import java.net.InetAddress;

import java.util.Random;

import net.tomp2p.connection.Bindings;
import net.tomp2p.connection.StandardProtocolFamily;
import net.tomp2p.futures.FutureDiscover;
import net.tomp2p.nat.FutureNAT;
import net.tomp2p.nat.FutureRelayNAT;
import net.tomp2p.nat.PeerBuilderNAT;
import net.tomp2p.nat.PeerNAT;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;

public class BSExampleNAT {
    // "188.226.179.109", 5000
    private final static String IP_SERVER = "188.226.179.109";
    //private final static String IP_SERVER = "128.199.251.106"; // steves
    private final static int PORT_SERVER = 5000;
    private final static int PORT_CLIENT = 6500;
    /*
    public static void startServer() throws Exception {
		Random r = new Random(42L);
		Peer peer = new PeerBuilder(new Number160(r)).ports(PORT_SERVER).start();
		System.out.println("peer started.");
		for (;;) {
			for (PeerAddress pa : peer.peerBean().peerMap().all()) {
					System.out.println("peer online (TCP):" + pa);
			}
			Thread.sleep(2000);
		}
	}*/

    public static void main(String[] args) throws Exception {
        startClientNAT();
    }

    public static void startClientNAT() throws Exception {
        Random r = new Random(43L);
        Bindings bindings = new Bindings();
        bindings.addProtocol(StandardProtocolFamily.INET);
        PeerBuilder peerBuilder = new PeerBuilder(new Number160(r)).ports(PORT_CLIENT).portsExternal(PORT_CLIENT)
                .behindFirewall().bindings(bindings);
        Peer peer = peerBuilder.start();
        //Peer peer = new PeerBuilder(new Number160(r)).ports(PORT_CLIENT).behindFirewall().start();
        PeerNAT peerNAT = new PeerBuilderNAT(peer).start();
        PeerAddress pa = new PeerAddress(Number160.ZERO, InetAddress.getByName(IP_SERVER), PORT_SERVER, PORT_SERVER);

        FutureDiscover fd = peer.discover().peerAddress(pa).start();
        FutureNAT fn = peerNAT.startSetupPortforwarding(fd);
        FutureRelayNAT frn = peerNAT.startRelay(fd, fn);

        frn.awaitUninterruptibly();
        if (fd.isSuccess()) {
            System.out.println("found that my outside address is " + fd.peerAddress());
        }
        else {
            System.out.println("failed " + fd.failedReason());
        }

        // peer.shutdown();
    }
}
