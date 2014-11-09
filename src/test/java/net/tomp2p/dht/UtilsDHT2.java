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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import java.net.InetAddress;
import java.net.UnknownHostException;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.Random;
import java.util.TreeSet;

import net.tomp2p.connection.Bindings;
import net.tomp2p.futures.FutureBootstrap;
import net.tomp2p.futures.FutureDiscover;
import net.tomp2p.message.Message;
import net.tomp2p.message.Message.Type;
import net.tomp2p.p2p.AutomaticFuture;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.peers.PeerMap;
import net.tomp2p.peers.PeerMapConfiguration;
import net.tomp2p.peers.PeerSocketAddress;

public class UtilsDHT2 {
    /**
     * Used to make the testcases predictable. Used as an input for {@link Random}.
     */
    public static final long THE_ANSWER = 42L;

    /**
     * Having two peers in a network, the seed needs to be different, otherwise we create a peer with the same id twice.
     */
    public static final long THE_ANSWER2 = 43L;

    public static Message createDummyMessage() throws UnknownHostException {
        return createDummyMessage(false, false);
    }

    public static Message createDummyMessage(boolean firewallUDP, boolean firewallTCP)
            throws UnknownHostException {
        return createDummyMessage(new Number160("0x4321"), "127.0.0.1", 8001, 8002, new Number160("0x1234"),
                "127.0.0.1", 8003, 8004, (byte) 0, Type.REQUEST_1, firewallUDP, firewallTCP);
    }

    public static PeerAddress createAddress(Number160 id) throws UnknownHostException {
        return createAddress(id, "127.0.0.1", 8005, 8006, false, false);
    }

    public static PeerAddress createAddress() throws UnknownHostException {
        return createAddress(new Number160("0x5678"), "127.0.0.1", 8005, 8006, false, false);
    }

    public static PeerAddress createAddress(int id) throws UnknownHostException {
        return createAddress(new Number160(id), "127.0.0.1", 8005, 8006, false, false);
    }

    public static PeerAddress createAddress(String id) throws UnknownHostException {
        return createAddress(new Number160(id), "127.0.0.1", 8005, 8006, false, false);
    }

    public static PeerAddress createAddress(Number160 idSender, String inetSender, int tcpPortSender,
                                            int udpPortSender, boolean firewallUDP,
                                            boolean firewallTCP) throws UnknownHostException {
        InetAddress inetSend = InetAddress.getByName(inetSender);
        PeerSocketAddress peerSocketAddress = new PeerSocketAddress(inetSend, tcpPortSender, udpPortSender);
        PeerAddress n1 = new PeerAddress(idSender, peerSocketAddress, firewallTCP, firewallUDP, false, false, false,
                PeerAddress.EMPTY_PEER_SOCKET_ADDRESSES);
        return n1;
    }

    public static Message createDummyMessage(Number160 idSender, String inetSender, int tcpPortSendor,
                                             int udpPortSender, Number160 idRecipien, String inetRecipient,
                                             int tcpPortRecipient,
                                             int udpPortRecipient, byte command, Type type, boolean firewallUDP,
                                             boolean firewallTCP)
            throws UnknownHostException {
        Message message = new Message();
        PeerAddress n1 = createAddress(idSender, inetSender, tcpPortSendor, udpPortSender, firewallUDP,
                firewallTCP);
        message.sender(n1);
        //
        PeerAddress n2 = createAddress(idRecipien, inetRecipient, tcpPortRecipient, udpPortRecipient,
                firewallUDP, firewallTCP);
        message.recipient(n2);
        message.type(type);
        message.command(command);
        return message;
    }

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

    public static Peer[] createRealNodes(int nrOfPeers, Random rnd, int startPort,
                                         AutomaticFuture automaticFuture) throws Exception {
        if (nrOfPeers < 1) {
            throw new IllegalArgumentException("Cannot create less than 1 peer");
        }
        Peer[] peers = new Peer[nrOfPeers];
        for (int i = 0; i < nrOfPeers; i++) {
            peers[i] = new PeerBuilder(new Number160(rnd))
                    .ports(startPort + i).start().addAutomaticFuture(automaticFuture);
        }
        System.err.println("real peers created.");
        return peers;
    }

    public static Peer[] createNonMaintenanceNodes(int nrOfPeers, Random rnd, int port) throws IOException {
        if (nrOfPeers < 1) {
            throw new IllegalArgumentException("Cannot create less than 1 peer");
        }
        Peer[] peers = new Peer[nrOfPeers];
        peers[0] = new PeerBuilder(new Number160(rnd)).enableMaintenance(false).ports(port).start();
        for (int i = 1; i < nrOfPeers; i++) {
            peers[i] = new PeerBuilder(new Number160(rnd)).enableMaintenance(false).masterPeer(peers[0])
                    .start();
        }
        System.err.println("non-maintenance peers created.");
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

    public static void perfectRoutingIndirect(PeerDHT... peers) {
        for (int i = 0; i < peers.length; i++) {
            for (int j = 0; j < peers.length; j++)
                peers[i].peerBean().peerMap().peerFound(peers[j].peerAddress(), peers[j].peerAddress(), null);
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

    public static Peer[] createAndAttachNodes(int nr, int port, Random rnd) throws Exception {
        Peer[] peers = new Peer[nr];
        for (int i = 0; i < nr; i++) {
            if (i == 0) {
                peers[0] = new PeerBuilder(new Number160(rnd)).ports(port).start();
            }
            else {
                peers[i] = new PeerBuilder(new Number160(rnd)).masterPeer(peers[0]).start();
            }
        }
        return peers;
    }

    public static void bootstrap(Peer[] peers) {
        List<FutureBootstrap> futures1 = new ArrayList<FutureBootstrap>();
        List<FutureDiscover> futures2 = new ArrayList<FutureDiscover>();
        for (int i = 1; i < peers.length; i++) {
            FutureDiscover tmp = peers[i].discover().peerAddress(peers[0].peerAddress()).start();
            futures2.add(tmp);
        }
        for (FutureDiscover future : futures2) {
            future.awaitUninterruptibly();
        }
        for (int i = 1; i < peers.length; i++) {
            FutureBootstrap tmp = peers[i].bootstrap().peerAddress(peers[0].peerAddress()).start();
            futures1.add(tmp);
        }
        for (int i = 1; i < peers.length; i++) {
            FutureBootstrap tmp = peers[0].bootstrap().peerAddress(peers[i].peerAddress()).start();
            futures1.add(tmp);
        }
        for (FutureBootstrap future : futures1)
            future.awaitUninterruptibly();
    }

    public static void routing(Number160 key, Peer[] peers, int start) {
        System.out.println("routing: searching for key " + key);
        NavigableSet<PeerAddress> pa1 = new TreeSet<PeerAddress>(PeerMap.createComparator(key));
        NavigableSet<PeerAddress> queried = new TreeSet<PeerAddress>(PeerMap.createComparator(key));
        Number160 result = Number160.ZERO;
        Number160 resultPeer = new Number160("0xd75d1a3d57841fbc9e2a3d175d6a35dc2e15b9f");
        int round = 0;
        while (!resultPeer.equals(result)) {
            System.out.println("round " + round);
            round++;
            pa1.addAll(peers[start].peerBean().peerMap().all());
            queried.add(peers[start].peerAddress());
            System.out.println("closest so far: " + queried.first());
            PeerAddress next = pa1.pollFirst();
            while (queried.contains(next)) {
                next = pa1.pollFirst();
            }
            result = next.peerId();
            start = findNr(next.peerId().toString(), peers);
        }
    }

    public static void findInMap(PeerAddress key, Peer[] peers) {
        for (int i = 0; i < peers.length; i++) {
            if (peers[i].peerBean().peerMap().contains(key)) {
                System.out.println("Peer " + i + " with the id " + peers[i].peerID() + " knows the peer "
                        + key);
            }
        }
    }

    public static int findNr(String string, Peer[] peers) {
        for (int i = 0; i < peers.length; i++) {
            if (peers[i].peerID().equals(new Number160(string))) {
                System.out.println("we found the number " + i + " for peer with id " + string);
                return i;
            }
        }
        return -1;
    }

    public static Peer find(String string, Peer[] peers) {
        for (int i = 0; i < peers.length; i++) {
            if (peers[i].peerID().equals(new Number160(string))) {
                System.out.println("!!we found the number " + i + " for peer with id " + string);
                return peers[i];
            }
        }
        return null;
    }

    public static void exec(String cmd) throws Exception {
        Process p = Runtime.getRuntime().exec(cmd);
        p.waitFor();

        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line = null;
        while ((line = br.readLine()) != null) {
            System.out.println(line);
        }
        br.close();
    }

    public static PeerAddress createAddressIP(String inet) throws UnknownHostException {
        return createAddress(Number160.createHash(inet), inet, 8005, 8006, false, false);
    }

    public static PeerAddress[] createDummyAddress(int size, int portTCP, int portUDP) throws UnknownHostException {
        PeerAddress[] pa = new PeerAddress[size];
        for (int i = 0; i < size; i++) {
            pa[i] = createAddress(i + 1, portTCP, portUDP);
        }
        return pa;
    }

    public static PeerAddress createAddress(int iid, int portTCP, int portUDP) throws UnknownHostException {
        Number160 id = new Number160(iid);
        InetAddress address = InetAddress.getByName("127.0.0.1");
        return new PeerAddress(id, address, portTCP, portUDP);
    }

}
