package io.bisq.network.p2p;

import io.bisq.common.Payload;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.common.proto.network.NetworkPayload;
import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.common.proto.persistable.PersistenceProtoResolver;
import io.bisq.generated.protobuffer.PB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

@SuppressWarnings("ALL")
public class TestUtils {
    private static final Logger log = LoggerFactory.getLogger(TestUtils.class);

    public static int sleepTime;
    public static final String test_dummy_dir = "test_dummy_dir";

    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        long ts = System.currentTimeMillis();
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("DSA");
        keyPairGenerator.initialize(1024);
        KeyPair keyPair = keyPairGenerator.genKeyPair();
        log.trace("Generate storageSignatureKeyPair needed {} ms", System.currentTimeMillis() - ts);
        return keyPair;
    }

    public static DummySeedNode getAndStartSeedNode(int port, boolean useLocalhostForP2P, Set<NodeAddress> seedNodes) throws InterruptedException {
        DummySeedNode seedNode;

        if (useLocalhostForP2P) {
            seedNodes.add(new NodeAddress("localhost:8001"));
            seedNodes.add(new NodeAddress("localhost:8002"));
            seedNodes.add(new NodeAddress("localhost:8003"));
            sleepTime = 100;
            seedNode = new DummySeedNode(test_dummy_dir);
        } else {
            seedNodes.add(new NodeAddress("3omjuxn7z73pxoee.onion:8001"));
            seedNodes.add(new NodeAddress("j24fxqyghjetgpdx.onion:8002"));
            seedNodes.add(new NodeAddress("45367tl6unwec6kw.onion:8003"));
            sleepTime = 10000;
            seedNode = new DummySeedNode(test_dummy_dir);
        }

        CountDownLatch latch = new CountDownLatch(1);
        seedNode.createAndStartP2PService(new NodeAddress("localhost", port), DummySeedNode.MAX_CONNECTIONS_DEFAULT, useLocalhostForP2P, 2, true,
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
                    public void onRequestCustomBridges() {

                    }
                });
        latch.await();
        Thread.sleep(sleepTime);
        return seedNode;
    }

    /*  public static P2PService getAndAuthenticateP2PService(int port, EncryptionService encryptionService, KeyRing keyRing,
                                                            boolean useLocalhostForP2P, Set<NodeAddress> seedNodes)
              throws InterruptedException {
          CountDownLatch latch = new CountDownLatch(1);
          SeedNodesRepository seedNodesRepository = new SeedNodesRepository();
          if (seedNodes != null && !seedNodes.isEmpty()) {
              if (useLocalhostForP2P)
                  seedNodesRepository.setLocalhostSeedNodeAddresses(seedNodes);
              else
                  seedNodesRepository.setTorSeedNodeAddresses(seedNodes);
          }

          P2PService p2PService = new P2PService(seedNodesRepository, port, new File("seed_node_" + port), useLocalhostForP2P,
                  2, P2PService.MAX_CONNECTIONS_DEFAULT, new File("dummy"), null, null, null,
                  new Clock(), null, encryptionService, keyRing, getNetworkProtoResolver(), getPersistenceProtoResolver());
          p2PService.start(new P2PServiceListener() {
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
          });
          latch.await();
          Thread.sleep(2000);
          return p2PService;
      }
  */
    public static NetworkProtoResolver getNetworkProtoResolver() {
        return new NetworkProtoResolver() {
            @Override
            public Payload fromProto(PB.PaymentAccountPayload proto) {
                return null;
            }

            @Override
            public PersistableEnvelope fromProto(PB.PersistableNetworkPayload persistable) {
                return null;
            }

            @Override
            public NetworkEnvelope fromProto(PB.NetworkEnvelope envelope) {
                return null;
            }

            @Override
            public NetworkPayload fromProto(PB.StoragePayload proto) {
                return null;
            }

            @Override
            public NetworkPayload fromProto(PB.StorageEntryWrapper proto) {
                return null;
            }
        };
    }

    public static PersistenceProtoResolver getPersistenceProtoResolver() {
        return new PersistenceProtoResolver() {
            @Override
            public Payload fromProto(PB.PaymentAccountPayload proto) {
                return null;
            }

            @Override
            public PersistableEnvelope fromProto(PB.PersistableEnvelope persistable) {
                return null;
            }

            @Override
            public PersistableEnvelope fromProto(PB.PersistableNetworkPayload persistable) {
                return null;
            }
        };
    }
}
