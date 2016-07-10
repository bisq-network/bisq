package io.bitsquare.p2p;

import io.bitsquare.common.Clock;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.crypto.EncryptionService;
import io.bitsquare.p2p.seed.SeedNode;
import io.bitsquare.p2p.seed.SeedNodesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.*;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class TestUtils {
    private static final Logger log = LoggerFactory.getLogger(TestUtils.class);

    public static int sleepTime;
    public static String test_dummy_dir = "test_dummy_dir";

    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        long ts = System.currentTimeMillis();
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("DSA");
        keyPairGenerator.initialize(1024);
        KeyPair keyPair = keyPairGenerator.genKeyPair();
        log.trace("Generate storageSignatureKeyPair needed {} ms", System.currentTimeMillis() - ts);
        return keyPair;
    }


    public static byte[] sign(PrivateKey privateKey, Serializable data)
            throws SignatureException, NoSuchAlgorithmException, InvalidKeyException {
        Signature sig = Signature.getInstance("SHA1withDSA");
        sig.initSign(privateKey);
        sig.update(objectToByteArray(data));
        return sig.sign();
    }

    public static byte[] objectToByteArray(Object object) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = null;
        byte[] result = null;
        try {
            out = new ObjectOutputStream(bos);
            out.writeObject(object);
            result = bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException ex) {
                // ignore close exception
            }
            try {
                bos.close();
            } catch (IOException ex) {
                // ignore close exception
            }
        }
        return result;
    }

    public static SeedNode getAndStartSeedNode(int port, boolean useLocalhost, Set<NodeAddress> seedNodes) throws InterruptedException {
        SeedNode seedNode;

        if (useLocalhost) {
            seedNodes.add(new NodeAddress("localhost:8001"));
            seedNodes.add(new NodeAddress("localhost:8002"));
            seedNodes.add(new NodeAddress("localhost:8003"));
            sleepTime = 100;
            seedNode = new SeedNode(test_dummy_dir);
        } else {
            seedNodes.add(new NodeAddress("3omjuxn7z73pxoee.onion:8001"));
            seedNodes.add(new NodeAddress("j24fxqyghjetgpdx.onion:8002"));
            seedNodes.add(new NodeAddress("45367tl6unwec6kw.onion:8003"));
            sleepTime = 10000;
            seedNode = new SeedNode(test_dummy_dir);
        }

        CountDownLatch latch = new CountDownLatch(1);
        seedNode.createAndStartP2PService(new NodeAddress("localhost", port), SeedNode.MAX_CONNECTIONS_DEFAULT, useLocalhost, 2, true,
                seedNodes, new P2PServiceListener() {
                    @Override
                    public void onRequestingDataCompleted() {
                    }

                    @Override
                    public void onNoSeedNodeAvailable() {
                    }

                    @Override
                    public void onNoPeersAvailable() {
                    }

                    @Override
                    public void onBootstrapComplete() {
                    }

                    @Override
                    public void onTorNodeReady() {
                    }

                    @Override
                    public void onHiddenServicePublished() {
                        latch.countDown();
                    }

                    @Override
                    public void onSetupFailed(Throwable throwable) {
                    }

                    @Override
                    public void onUseDefaultBridges() {
                    }

                    @Override
                    public void onRequestCustomBridges(Runnable resultHandler) {
                    }
                });
        latch.await();
        Thread.sleep(sleepTime);
        return seedNode;
    }

    public static P2PService getAndAuthenticateP2PService(int port, EncryptionService encryptionService, KeyRing keyRing,
                                                          boolean useLocalhost, Set<NodeAddress> seedNodes)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        SeedNodesRepository seedNodesRepository = new SeedNodesRepository();
        if (seedNodes != null && !seedNodes.isEmpty()) {
            if (useLocalhost)
                seedNodesRepository.setLocalhostSeedNodeAddresses(seedNodes);
            else
                seedNodesRepository.setTorSeedNodeAddresses(seedNodes);
        }

        P2PService p2PService = new P2PService(seedNodesRepository, port, new File("seed_node_" + port), useLocalhost,
                2, P2PService.MAX_CONNECTIONS_DEFAULT, new File("dummy"), null, new Clock(), encryptionService, keyRing);
        p2PService.start(false, new P2PServiceListener() {
            @Override
            public void onRequestingDataCompleted() {
            }

            @Override
            public void onNoSeedNodeAvailable() {
            }

            @Override
            public void onNoPeersAvailable() {
            }

            @Override
            public void onTorNodeReady() {
            }

            @Override
            public void onBootstrapComplete() {
                latch.countDown();
            }

            @Override
            public void onHiddenServicePublished() {
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
            }

            @Override
            public void onUseDefaultBridges() {
            }

            @Override
            public void onRequestCustomBridges(Runnable resultHandler) {
            }
        });
        latch.await();
        Thread.sleep(2000);
        return p2PService;
    }
}
