package bisq.httpapi;

import org.arquillian.cube.docker.impl.client.config.Await;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.Container;
import org.arquillian.cube.docker.impl.client.containerobject.dsl.ContainerBuilder;

@SuppressWarnings("WeakerAccess")
public final class ContainerFactory {

    public static final String BITCOIN_NODE_CONTAINER_NAME = "bisq-http-api-bitcoin-node";
    public static final String BITCOIN_NODE_HOST_NAME = "bitcoin";
    public static final String SEED_NODE_CONTAINER_NAME = "bisq-seednode";
    public static final String SEED_NODE_HOST_NAME = SEED_NODE_CONTAINER_NAME;
    public static final String SEED_NODE_ADDRESS = SEED_NODE_HOST_NAME + ":8000";
    public static final String CONTAINER_NAME_PREFIX = "bisq-http-api-";
    public static final String API_IMAGE = "bisq/http-api";
    public static final String ENV_NODE_PORT_KEY = "NODE_PORT";
    public static final String ENV_ENABLE_HTTP_API_EXPERIMENTAL_FEATURES_KEY = "ENABLE_HTTP_API_EXPERIMENTAL_FEATURES";
    public static final String ENV_HTTP_API_HOST_KEY = "HTTP_API_HOST";
    public static final String ENV_HTTP_API_HOST_VALUE = "0.0.0.0";
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

    @SuppressWarnings("WeakerAccess")
    public static ContainerBuilder.ContainerOptionsBuilder createApiContainerBuilder(String nameSuffix, String portBinding, int nodePort, boolean linkToSeedNode, boolean linkToBitcoin, boolean enableExperimentalFeatures) {
        ContainerBuilder.ContainerOptionsBuilder containerOptionsBuilder = Container.withContainerName(CONTAINER_NAME_PREFIX + nameSuffix)
                .fromImage(API_IMAGE)
                .withPortBinding(portBinding)
                .withEnvironment(ENV_NODE_PORT_KEY, nodePort)
                .withEnvironment(ENV_HTTP_API_HOST_KEY, ENV_HTTP_API_HOST_VALUE)
                .withEnvironment(ENV_ENABLE_HTTP_API_EXPERIMENTAL_FEATURES_KEY, enableExperimentalFeatures)
                .withEnvironment(ENV_USE_DEV_PRIVILEGE_KEYS_KEY, ENV_USE_DEV_PRIVILEGE_KEYS_VALUE)
                .withAwaitStrategy(getAwaitStrategy());
        if (linkToSeedNode) {
            containerOptionsBuilder.withLink(SEED_NODE_CONTAINER_NAME);
        }
        if (linkToBitcoin) {
            containerOptionsBuilder.withLink(BITCOIN_NODE_CONTAINER_NAME, BITCOIN_NODE_HOST_NAME);
        }
        return withRegtestEnv(containerOptionsBuilder);
    }

    public static Await getAwaitStrategy() {
        Await awaitStrategy = new Await();
        awaitStrategy.setStrategy("polling");
        int sleepPollingTime = 250;
        awaitStrategy.setIterations(60000 / sleepPollingTime);
        awaitStrategy.setSleepPollingTime(sleepPollingTime);
        return awaitStrategy;
    }

    public static Container createApiContainer(String nameSuffix, String portBinding, int nodePort, boolean linkToSeedNode, boolean linkToBitcoin, boolean enableExperimentalFeatures) {
        Container container = createApiContainerBuilder(nameSuffix, portBinding, nodePort, linkToSeedNode, linkToBitcoin, enableExperimentalFeatures).build();
        container.getCubeContainer().setKillContainer(true);
        return container;
    }

    public static Container createApiContainer(String nameSuffix, String portBinding, int nodePort, boolean linkToSeedNode, boolean linkToBitcoin) {
        return createApiContainer(nameSuffix, portBinding, nodePort, linkToSeedNode, linkToBitcoin, true);
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

}
