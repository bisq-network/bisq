package io.bitsquare.msg;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Random;
import net.tomp2p.connection.Ports;
import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.FuturePut;
import net.tomp2p.dht.FutureRemove;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.futures.FutureDirect;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.rpc.ObjectDataReply;
import net.tomp2p.storage.Data;
import net.tomp2p.utils.Utils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

public class P2PNodeTest
{
    private static final Logger log = LoggerFactory.getLogger(P2PNodeTest.class);

    final private static Random rnd = new Random(42L);

    @Test
    public void testSendData() throws Exception
    {
        PeerDHT[] peers = UtilsDHT2.createNodes(3, rnd, new Ports().tcpPort());
        PeerDHT master = peers[0];
        PeerDHT client = peers[1];
        PeerDHT otherPeer = peers[2];
        UtilsDHT2.perfectRouting(peers);


        for (final PeerDHT peer : peers)
        {
            peer.peer().objectDataReply(new ObjectDataReply()
            {
                @Override
                public Object reply(PeerAddress sender, Object request) throws Exception
                {
                    return true;
                }
            });
        }

        final KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA");
        keyGen.initialize(1024);
        KeyPair keyPairClient = keyGen.genKeyPair();
        KeyPair keyPairOtherPeer = keyGen.genKeyPair();

        P2PNode node;
        Number160 locationKey;
        Object object;
        FutureDirect futureDirect;

        node = new P2PNode(keyPairClient, client);
        object = "clients data";
        futureDirect = node.sendData(otherPeer.peerAddress(), object);
        futureDirect.awaitUninterruptibly();

        assertTrue(futureDirect.isSuccess());
        // we return true from objectDataReply
        assertTrue((Boolean) futureDirect.object());

        master.shutdown();
    }

    @Test
    public void testProtectedPutGet() throws Exception
    {
        PeerDHT[] peers = UtilsDHT2.createNodes(3, rnd, new Ports().tcpPort());
        PeerDHT master = peers[0];
        PeerDHT client = peers[1];
        PeerDHT otherPeer = peers[2];
        UtilsDHT2.perfectRouting(peers);

        final KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA");
        keyGen.initialize(1024);
        KeyPair keyPairClient = keyGen.genKeyPair();
        KeyPair keyPairOtherPeer = keyGen.genKeyPair();

        P2PNode node;
        Number160 locationKey;
        Data data;
        FuturePut futurePut;
        FutureGet futureGet;

        // otherPeer tries to squat clients location store
        // he can do it but as he has not the domain key of the client he cannot do any harm
        // he only can store und that path: locationKey.otherPeerDomainKey.data
        node = new P2PNode(keyPairOtherPeer, otherPeer);
        locationKey = Number160.createHash("clients location");
        data = new Data("otherPeer data");
        futurePut = node.putDomainProtectedData(locationKey, data);
        futurePut.awaitUninterruptibly();
        assertTrue(futurePut.isSuccess());

        futureGet = node.getDomainProtectedData(locationKey, keyPairOtherPeer.getPublic());
        futureGet.awaitUninterruptibly();
        assertTrue(futureGet.isSuccess());
        assertEquals("otherPeer data", futureGet.data().object());

        // client store his data und his domainkey, no problem with previous occupied
        // he only can store und that path: locationKey.clientDomainKey.data
        node = new P2PNode(keyPairClient, client);
        locationKey = Number160.createHash("clients location");
        data = new Data("client data");
        futurePut = node.putDomainProtectedData(locationKey, data);
        futurePut.awaitUninterruptibly();
        assertTrue(futurePut.isSuccess());

        futureGet = node.getDomainProtectedData(locationKey, keyPairClient.getPublic());
        futureGet.awaitUninterruptibly();
        assertTrue(futureGet.isSuccess());
        assertEquals("client data", futureGet.data().object());

        // also other peers can read that data if they know the public key of the client
        node = new P2PNode(keyPairOtherPeer, otherPeer);
        futureGet = node.getDomainProtectedData(locationKey, keyPairClient.getPublic());
        futureGet.awaitUninterruptibly();
        assertTrue(futureGet.isSuccess());
        assertEquals("client data", futureGet.data().object());


        // other peer try to use pub key of other peer as domain key hash.
        // must fail as he don't have the full key pair (private key of client missing)
        locationKey = Number160.createHash("clients location");
        data = new Data("otherPeer data hack");

        data.protectEntry(keyPairOtherPeer);
        // he use the pub key from the client
        final Number160 keyHash = Utils.makeSHAHash(keyPairClient.getPublic().getEncoded());
        futurePut = otherPeer.put(locationKey).data(data).keyPair(keyPairOtherPeer).domainKey(keyHash).protectDomain().start();

        futurePut.awaitUninterruptibly();
        assertFalse(futurePut.isSuccess());

        // he can read his prev. stored data
        node = new P2PNode(keyPairOtherPeer, otherPeer);
        futureGet = node.getDomainProtectedData(locationKey, keyPairOtherPeer.getPublic());
        futureGet.awaitUninterruptibly();
        assertTrue(futureGet.isSuccess());
        assertEquals("otherPeer data", futureGet.data().object());

        // he can read clients data
        futureGet = node.getDomainProtectedData(locationKey, keyPairClient.getPublic());
        futureGet.awaitUninterruptibly();
        assertTrue(futureGet.isSuccess());
        assertEquals("client data", futureGet.data().object());

        master.shutdown();
    }

    @Test
    public void testAddToListGetList() throws Exception
    {
        PeerDHT[] peers = UtilsDHT2.createNodes(3, rnd, new Ports().tcpPort());
        PeerDHT master = peers[0];
        PeerDHT client = peers[1];
        PeerDHT otherPeer = peers[2];
        UtilsDHT2.perfectRouting(peers);

        P2PNode node;
        final KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA");
        keyGen.initialize(1024);
        KeyPair keyPairClient = keyGen.genKeyPair();
        KeyPair keyPairOtherPeer = keyGen.genKeyPair();

        Number160 locationKey;
        Data data;
        FuturePut futurePut;
        FutureGet futureGet;

        // client add a value
        node = new P2PNode(keyPairClient, client);
        locationKey = Number160.createHash("add to list clients location");
        data = new Data("add to list client data1");
        Data data_1 = data;
        futurePut = node.addProtectedData(locationKey, data);
        futurePut.awaitUninterruptibly();
        assertTrue(futurePut.isSuccess());

        data = new Data("add to list client data2");
        Data data_2 = data;
        futurePut = node.addProtectedData(locationKey, data);
        futurePut.awaitUninterruptibly();
        assertTrue(futurePut.isSuccess());

        futureGet = node.getDataMap(locationKey);
        futureGet.awaitUninterruptibly();
        assertTrue(futureGet.isSuccess());
        boolean foundData1 = futureGet.dataMap().values().stream().anyMatch(data1 -> {
            try
            {
                return data1.object().equals("add to list client data1");
            } catch (ClassNotFoundException | IOException e)
            {
                e.printStackTrace();
            }
            return false;
        });
        boolean foundData2 = futureGet.dataMap().values().stream().anyMatch(data1 -> {
            try
            {
                return data1.object().equals("add to list client data2");
            } catch (ClassNotFoundException | IOException e)
            {
                e.printStackTrace();
            }
            return false;
        });
        assertTrue(foundData1);
        assertTrue(foundData2);
        assertEquals(2, futureGet.dataMap().values().size());


        // other peer tried to overwrite that entry
        // but will not succeed, instead he will add a new entry.
        // TODO investigate why it is not possible to overwrite the entry with that method
        // The protection entry with the key does not make any difference as also the client himself cannot overwrite any entry
        // http://tomp2p.net/doc/P2P-with-TomP2P-1.pdf
        // "add(location_key, value) is translated to put(location_key, hash(value), value)"

        // fake content key with content key from previous clients entry
        Number160 contentKey = Number160.createHash("add to list client data1");

        data = new Data("add to list other peer data HACK!");
        data.protectEntry(keyPairOtherPeer);     // also with client key it does not work...
        futurePut = otherPeer.put(locationKey).data(contentKey, data).keyPair(keyPairOtherPeer).start();
        futurePut.awaitUninterruptibly();
        assertTrue(futurePut.isSuccess());

        node = new P2PNode(keyPairOtherPeer, otherPeer);
        futureGet = node.getDataMap(locationKey);
        futureGet.awaitUninterruptibly();
        assertTrue(futureGet.isSuccess());

        foundData1 = futureGet.dataMap().values().stream().anyMatch(data1 -> {
            try
            {
                return data1.object().equals("add to list client data1");
            } catch (ClassNotFoundException | IOException e)
            {
                e.printStackTrace();
            }
            return false;
        });
        foundData2 = futureGet.dataMap().values().stream().anyMatch(data1 -> {
            try
            {
                return data1.object().equals("add to list client data2");
            } catch (ClassNotFoundException | IOException e)
            {
                e.printStackTrace();
            }
            return false;
        });
        boolean foundData3 = futureGet.dataMap().values().stream().anyMatch(data1 -> {
            try
            {
                return data1.object().equals("add to list other peer data HACK!");
            } catch (ClassNotFoundException | IOException e)
            {
                e.printStackTrace();
            }
            return false;
        });
        assertTrue(foundData1);
        assertTrue(foundData2);
        assertTrue(foundData3);
        assertEquals(3, futureGet.dataMap().values().size());


        // client removes his entry -> OK
        node = new P2PNode(keyPairClient, client);
        FutureRemove futureRemove = node.removeFromDataMap(locationKey, data_1);
        futureRemove.awaitUninterruptibly();
        assertTrue(futureRemove.isSuccess());

        futureGet = node.getDataMap(locationKey);
        futureGet.awaitUninterruptibly();
        assertTrue(futureGet.isSuccess());

        foundData1 = futureGet.dataMap().values().stream().anyMatch(data1 -> {
            try
            {
                return data1.object().equals("add to list client data1");
            } catch (ClassNotFoundException | IOException e)
            {
                e.printStackTrace();
            }
            return false;
        });
        foundData2 = futureGet.dataMap().values().stream().anyMatch(data1 -> {
            try
            {
                return data1.object().equals("add to list client data2");
            } catch (ClassNotFoundException | IOException e)
            {
                e.printStackTrace();
            }
            return false;
        });
        foundData3 = futureGet.dataMap().values().stream().anyMatch(data1 -> {
            try
            {
                return data1.object().equals("add to list other peer data HACK!");
            } catch (ClassNotFoundException | IOException e)
            {
                e.printStackTrace();
            }
            return false;
        });

        assertFalse(foundData1);
        assertTrue(foundData2);
        assertTrue(foundData3);
        assertEquals(2, futureGet.dataMap().values().size());


        // otherPeer tries to removes client entry -> FAIL
        node = new P2PNode(keyPairOtherPeer, otherPeer);
        futureRemove = node.removeFromDataMap(locationKey, data_2);
        futureRemove.awaitUninterruptibly();
        assertFalse(futureRemove.isSuccess());

        futureGet = node.getDataMap(locationKey);
        futureGet.awaitUninterruptibly();
        assertTrue(futureGet.isSuccess());

        foundData1 = futureGet.dataMap().values().stream().anyMatch(data1 -> {
            try
            {
                return data1.object().equals("add to list client data1");
            } catch (ClassNotFoundException | IOException e)
            {
                e.printStackTrace();
            }
            return false;
        });
        foundData2 = futureGet.dataMap().values().stream().anyMatch(data1 -> {
            try
            {
                return data1.object().equals("add to list client data2");
            } catch (ClassNotFoundException | IOException e)
            {
                e.printStackTrace();
            }
            return false;
        });
        foundData3 = futureGet.dataMap().values().stream().anyMatch(data1 -> {
            try
            {
                return data1.object().equals("add to list other peer data HACK!");
            } catch (ClassNotFoundException | IOException e)
            {
                e.printStackTrace();
            }
            return false;
        });

        assertFalse(foundData1);
        assertTrue(foundData2);
        assertTrue(foundData3);
        assertEquals(2, futureGet.dataMap().values().size());

        master.shutdown();
    }


}
