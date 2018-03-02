package io.bisq.api;

import org.arquillian.cube.docker.impl.client.config.Await;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.ContainerBuilder;

public final class ContainerFactory {

    private static final String BITCOIN_NODE_CONTAINER_NAME = "bisq-api-bitcoin-node";
    private static final String BITCOIN_NODE_HOST_NAME = "bitcoin";
    private static final String SEED_NODE_CONTAINER_NAME = "bisq-seednode";
    private static final String SEED_NODE_HOST_NAME = SEED_NODE_CONTAINER_NAME;
    private static final String SEED_NODE_ADDRESS = SEED_NODE_HOST_NAME + ":8000";

    public static Container createApiContainer(String nameSuffix, String portBinding, int nodePort, boolean linkToSeedNode, boolean linkToBitcoin) {
        final ContainerBuilder.ContainerOptionsBuilder containerOptionsBuilder = withRegtestEnv(Container.withContainerName("bisq-api-" + nameSuffix).fromImage("bisq-api").withVolume("m2", "/root/.m2").withPortBinding(portBinding))
                .withEnvironment("NODE_PORT", nodePort)
                .withEnvironment("USE_DEV_PRIVILEGE_KEYS", true);
        if (linkToSeedNode) {
            containerOptionsBuilder.withLink(SEED_NODE_CONTAINER_NAME);
        }
        if (linkToBitcoin) {
            containerOptionsBuilder.withLink(BITCOIN_NODE_CONTAINER_NAME, BITCOIN_NODE_HOST_NAME);
        }
        return containerOptionsBuilder.build();
    }

    public static ContainerBuilder.ContainerOptionsBuilder withRegtestEnv(ContainerBuilder.ContainerOptionsBuilder builder) {
        return builder
                .withEnvironment("USE_LOCALHOST_FOR_P2P", "true")
                .withEnvironment("BASE_CURRENCY_NETWORK", "BTC_REGTEST")
                .withEnvironment("BTC_NODES", "bitcoin:18444")
                .withEnvironment("SEED_NODES", SEED_NODE_ADDRESS)
                .withEnvironment("LOG_LEVEL", "debug");
    }

    public static Container createBitcoinContainer() {
        /* it takes a moment for bitcoind to initiate and become ready to receive commands */
        final Await awaitStrategy = new Await();
        awaitStrategy.setStrategy("sleeping");
        awaitStrategy.setSleepTime("2s");

        return Container.withContainerName(BITCOIN_NODE_CONTAINER_NAME)
                .fromImage("kylemanna/bitcoind:1.0.0")
                .withCommand("bitcoind -printtoconsole -rpcallowip=::/0 -regtest")
                .withPortBinding("8332/tcp")
                .withAwaitStrategy(awaitStrategy)
                .build();
    }

    public static Container createSeedNodeContainer() {
        return withRegtestEnv(Container.withContainerName("bisq-seednode").fromImage("bisq-seednode").withVolume("m2", "/root/.m2"))
                .withEnvironment("MY_ADDRESS", SEED_NODE_ADDRESS)
                .build();
    }
}
