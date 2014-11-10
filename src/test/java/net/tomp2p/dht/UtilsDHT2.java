/*
 * Copyright 2012 Thomas Bocek
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

package net.tomp2p.dht;

import java.io.File;
import java.io.IOException;

import java.util.Random;

import net.tomp2p.connection.Bindings;
import net.tomp2p.p2p.AutomaticFuture;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerMap;
import net.tomp2p.peers.PeerMapConfiguration;

public class UtilsDHT2 {

    public static PeerDHT[] createNodes(int nrOfPeers, Random rnd, int port) throws Exception {
        return createNodes(nrOfPeers, rnd, port, null);
    }

    public static PeerDHT[] createNodes(int nrOfPeers, Random rnd, int port, AutomaticFuture automaticFuture)
            throws Exception {
        return createNodes(nrOfPeers, rnd, port, automaticFuture, false);
    }

    /**
     * Creates peers for testing. The first peer (peer[0]) will be used as the master. This means that shutting down
     * peer[0] will shut down all other peers
     *
     * @param nrOfPeers The number of peers to create including the master
     * @param rnd       The random object to create random peer IDs
     * @param port      The port where the master peer will listen to
     * @return All the peers, with the master peer at position 0 -> peer[0]
     * @throws Exception If the creation of nodes fail.
     */
    public static PeerDHT[] createNodes(int nrOfPeers, Random rnd, int port, AutomaticFuture automaticFuture,
                                        boolean maintenance) throws Exception {
        if (nrOfPeers < 1) {
            throw new IllegalArgumentException("Cannot create less than 1 peer");
        }
        Bindings bindings = new Bindings().addInterface("lo");
        PeerDHT[] peers = new PeerDHT[nrOfPeers];
        final Peer master;
        if (automaticFuture != null) {
            Number160 peerId = new Number160(rnd);
            PeerMap peerMap = new PeerMap(new PeerMapConfiguration(peerId));
            master = new PeerBuilder(peerId)
                    .ports(port).enableMaintenance(maintenance)
                    .bindings(bindings).peerMap(peerMap).start().addAutomaticFuture(automaticFuture);
            peers[0] = new PeerBuilderDHT(master).start();

        }
        else {
            Number160 peerId = new Number160(rnd);
            PeerMap peerMap = new PeerMap(new PeerMapConfiguration(peerId));
            master = new PeerBuilder(peerId).enableMaintenance(maintenance).bindings(bindings)
                    .peerMap(peerMap).ports(port).start();
            peers[0] = new PeerBuilderDHT(master).start();
        }

        for (int i = 1; i < nrOfPeers; i++) {
            if (automaticFuture != null) {
                Number160 peerId = new Number160(rnd);
                PeerMap peerMap = new PeerMap(new PeerMapConfiguration(peerId));
                Peer peer = new PeerBuilder(peerId)
                        .masterPeer(master)
                        .enableMaintenance(maintenance).enableMaintenance(maintenance).peerMap(peerMap).bindings
                                (bindings).start().addAutomaticFuture(automaticFuture);
                peers[i] = new PeerBuilderDHT(peer).start();
            }
            else {
                Number160 peerId = new Number160(rnd);
                PeerMap peerMap = new PeerMap(new PeerMapConfiguration(peerId).peerNoVerification());
                Peer peer = new PeerBuilder(peerId).enableMaintenance(maintenance)
                        .bindings(bindings).peerMap(peerMap).masterPeer(master)
                        .start();
                peers[i] = new PeerBuilderDHT(peer).start();
            }
        }
        System.err.println("peers created.");
        return peers;
    }

    /**
     * Perfect routing, where each neighbor has contacted each other. This means that for small number of peers, every
     * peer knows every other peer.
     *
     * @param peers The peers taking part in the p2p network.
     */
    public static void perfectRouting(PeerDHT... peers) {
        for (int i = 0; i < peers.length; i++) {
            for (int j = 0; j < peers.length; j++)
                peers[i].peer().peerBean().peerMap().peerFound(peers[j].peer().peerAddress(), null, null);
        }
        System.err.println("perfect routing done.");
    }

    public static void main(String[] args) throws IOException {
        createTempDirectory();
    }

    private static final int TEMP_DIR_ATTEMPTS = 10000;

    public static File createTempDirectory() throws IOException {
        File baseDir = new File(System.getProperty("java.io.tmpdir"));
        String baseName = System.currentTimeMillis() + "-";

        for (int counter = 0; counter < TEMP_DIR_ATTEMPTS; counter++) {
            File tempDir = new File(baseDir, baseName + counter);
            if (tempDir.mkdir()) {
                return tempDir;
            }
        }
        throw new IllegalStateException("Failed to create directory within " + TEMP_DIR_ATTEMPTS
                + " attempts (tried " + baseName + "0 to " + baseName + (TEMP_DIR_ATTEMPTS - 1) + ')');
    }
}
