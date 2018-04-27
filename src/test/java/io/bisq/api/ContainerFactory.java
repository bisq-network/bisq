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
    public static final String CONTAINER_NAME_PREFIX = "bisq-api-";
    public static final String API_IMAGE = "bisq-api";
    public static final String M2_VOLUME_NAME = "m2";
    public static final String M2_VOLUME_CONTAINER_PATH = "/root/.m2";
    public static final String ENV_NODE_PORT_KEY = "NODE_PORT";
    public static final String ENV_BISQ_API_HOST_KEY = "BISQ_API_HOST";
    public static final String ENV_BISQ_API_HOST_VALUE = "0.0.0.0";
    public static final String ENV_USE_DEV_PRIVILEGE_KEYS_KEY = "USE_DEV_PRIVILEGE_KEYS";
    public static final String ENV_USE_DEV_PRIVILEGE_KEYS_VALUE = "true";
    public static final String ENV_USE_LOCALHOST_FOR_P2P_KEY = "USE_LOCALHOST_FOR_P2P";
    public static final String ENV_USE_LOCALHOST_FOR_P2P_VALUE = "true";
    public static final String ENV_BASE_CURRENCY_NETWORK_KEY = "BASE_CURRENCY_NETWORK";
    public static final String ENV_BASE_CURRENCY_NETWORK_VALUE = "BTC_REGTEST";
    public static final String ENV_BITCOIN_REGTEST_HOST_KEY = "BITCOIN_REGTEST_HOST";
    public static final String ENV_BITCOIN_REGTEST_HOST_VALUE = "NONE";
    public static final String ENV_BTC_NODES_KEY = "BTC_NODES";
    public static final String ENV_BTC_NODES_VALUE = "bitcoin:18444";
    public static final String ENV_SEED_NODES_KEY = "SEED_NODES";
    public static final String ENV_SEED_NODES_VALUE = SEED_NODE_ADDRESS;
    public static final String ENV_LOG_LEVEL_KEY = "LOG_LEVEL";
    public static final String ENV_LOG_LEVEL_VALUE = "debug";

    public static ContainerBuilder.ContainerOptionsBuilder createApiContainerBuilder(String nameSuffix, String portBinding, int nodePort, boolean linkToSeedNode, boolean linkToBitcoin) {
        final Await awaitStrategy = new Await();
        awaitStrategy.setStrategy("polling");
        final int sleepPollingTime = 250;
        awaitStrategy.setIterations(60000 / sleepPollingTime);
        awaitStrategy.setSleepPollingTime(sleepPollingTime);
        final ContainerBuilder.ContainerOptionsBuilder containerOptionsBuilder = Container.withContainerName(CONTAINER_NAME_PREFIX + nameSuffix)
                .fromImage(API_IMAGE)
                .withVolume(M2_VOLUME_NAME, M2_VOLUME_CONTAINER_PATH)
                .withPortBinding(portBinding)
                .withEnvironment(ENV_NODE_PORT_KEY, nodePort)
                .withEnvironment(ENV_BISQ_API_HOST_KEY, ENV_BISQ_API_HOST_VALUE)
                .withEnvironment(ENV_USE_DEV_PRIVILEGE_KEYS_KEY, ENV_USE_DEV_PRIVILEGE_KEYS_VALUE)
                .withAwaitStrategy(awaitStrategy);
        if (linkToSeedNode) {
            containerOptionsBuilder.withLink(SEED_NODE_CONTAINER_NAME);
        }
        if (linkToBitcoin) {
            containerOptionsBuilder.withLink(BITCOIN_NODE_CONTAINER_NAME, BITCOIN_NODE_HOST_NAME);
        }
        return withRegtestEnv(containerOptionsBuilder);
    }

    public static Container createApiContainer(String nameSuffix, String portBinding, int nodePort, boolean linkToSeedNode, boolean linkToBitcoin) {
        return createApiContainerBuilder(nameSuffix, portBinding, nodePort, linkToSeedNode, linkToBitcoin).build();
    }

    public static ContainerBuilder.ContainerOptionsBuilder withRegtestEnv(ContainerBuilder.ContainerOptionsBuilder builder) {
        return builder
                .withEnvironment(ENV_USE_LOCALHOST_FOR_P2P_KEY, ENV_USE_LOCALHOST_FOR_P2P_VALUE)
                .withEnvironment(ENV_BASE_CURRENCY_NETWORK_KEY, ENV_BASE_CURRENCY_NETWORK_VALUE)
                .withEnvironment(ENV_BITCOIN_REGTEST_HOST_KEY, ENV_BITCOIN_REGTEST_HOST_VALUE)
                .withEnvironment(ENV_BTC_NODES_KEY, ENV_BTC_NODES_VALUE)
                .withEnvironment(ENV_SEED_NODES_KEY, ENV_SEED_NODES_VALUE)
                .withEnvironment(ENV_LOG_LEVEL_KEY, ENV_LOG_LEVEL_VALUE);
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
        return withRegtestEnv(Container.withContainerName("bisq-seednode").fromImage("bisq/seednode"))
                .withEnvironment("MY_ADDRESS", SEED_NODE_ADDRESS)
                .build();
    }
}
