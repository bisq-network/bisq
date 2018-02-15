package io.bisq.core.network;

import com.google.common.collect.ImmutableSet;
import com.google.inject.name.Named;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.network.NetworkOptionKeys;
import io.bisq.network.p2p.NodeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

class CoreSeedNodeRepositoryFactory {
    private static final Logger log = LoggerFactory.getLogger(CoreSeedNodeRepositoryFactory.class);

    // Addresses are used if the last digit of their port match the network id:
    // - mainnet use port ends in 0
    // - testnet use port ends in 1
    // - regtest use port ends in 2
    private static final Set<NodeAddress> DEFAULT_LOCALHOST_SEED_NODE_ADDRESSES = ImmutableSet.of(
            // BTC
            // mainnet
            new NodeAddress("localhost:2000"),
            new NodeAddress("localhost:3000"),
            new NodeAddress("localhost:4000"),

            // testnet
            new NodeAddress("localhost:2001"),
            new NodeAddress("localhost:3001"),
            new NodeAddress("localhost:4001"),

            // regtest
            new NodeAddress("localhost:2002"),
            new NodeAddress("localhost:3002"),
            /*    new NodeAddress("localhost:4002"),*/

            // LTC
            // mainnet
            new NodeAddress("localhost:2003"),

            // regtest
            new NodeAddress("localhost:2005"),

            // DOGE regtest
            new NodeAddress("localhost:2008"),

            // DASH regtest
            new NodeAddress("localhost:2011")
    );

    // Addresses are used if their port match the network id:
    // - mainnet uses port 8000
    // - testnet uses port 8001
    // - regtest uses port 8002
    private static final Set<NodeAddress> DEFAULT_TOR_SEED_NODE_ADDRESSES = ImmutableSet.of(
            // BTC mainnet
            new NodeAddress("5quyxpxheyvzmb2d.onion:8000"), // @miker
            new NodeAddress("s67qglwhkgkyvr74.onion:8000"), // @emzy
            new NodeAddress("ef5qnzx6znifo3df.onion:8000"), // @manfredkarrer
            new NodeAddress("jhgcy2won7xnslrb.onion:8000"), // @manfredkarrer
            new NodeAddress("3f3cu2yw7u457ztq.onion:8000"), // @manfredkarrer
            new NodeAddress("723ljisnynbtdohi.onion:8000"), // @manfredkarrer
            new NodeAddress("rm7b56wbrcczpjvl.onion:8000"), // @manfredkarrer
            new NodeAddress("fl3mmribyxgrv63c.onion:8000"), // @manfredkarrer

            // local dev
            // new NodeAddress("joehwtpe7ijnz4df.onion:8000"),
            // new NodeAddress("uqxi3zrpobhtoes6.onion:8000"),

            // BTC testnet
            new NodeAddress("nbphlanpgbei4okt.onion:8001"),
            // new NodeAddress("vjkh4ykq7x5skdlt.onion:8001"), // dev test

            // BTC regtest
            // For development you need to change that to your local onion addresses
            // 1. Run a seed node with prog args: --bitcoinNetwork=regtest --nodePort=8002 --myAddress=rxdkppp3vicnbgqt:8002 --appName=bisq_seed_node_rxdkppp3vicnbgqt.onion_8002
            // 2. Find your local onion address in bisq_seed_node_rxdkppp3vicnbgqt.onion_8002/regtest/tor/hiddenservice/hostname
            // 3. Shut down the seed node
            // 4. Rename the directory with your local onion address
            // 5. Edit here your found onion address (new NodeAddress("YOUR_ONION.onion:8002")
            new NodeAddress("rxdkppp3vicnbgqt.onion:8002"),

            // LTC mainnet
            new NodeAddress("acyvotgewx46pebw.onion:8003"),
            new NodeAddress("pklgy3vdfn3obkur.onion:8003"),

            // DASH mainnet
            new NodeAddress("toeu5ikb27ydscxt.onion:8009"),
            new NodeAddress("ae4yvaivhnekkhqf.onion:8009")
    );


    CoreSeedNodesRepository create(BisqEnvironment environment,
                                   @Named(NetworkOptionKeys.USE_LOCALHOST_FOR_P2P) boolean useLocalhostForP2P,
                                   @Named(NetworkOptionKeys.NETWORK_ID) int networkId,
                                   @Nullable @Named(NetworkOptionKeys.MY_ADDRESS) String myAddress,
                                   @Nullable @Named(NetworkOptionKeys.SEED_NODES_KEY) String seedNodes) {
        NodeAddresses nodeAddresses = NodeAddresses.fromString(seedNodes);
        if (nodeAddresses.isEmpty()) {
            Set<NodeAddress> delegate = useLocalhostForP2P
                    ? DEFAULT_LOCALHOST_SEED_NODE_ADDRESSES
                    : DEFAULT_TOR_SEED_NODE_ADDRESSES;
            nodeAddresses = NodeAddresses.createFromSet(delegate, networkId);
        }

        Set<String> bannedHosts = getBannedHosts(environment);
        nodeAddresses = nodeAddresses.excludeByHost(bannedHosts);

        nodeAddresses = nodeAddresses.excludeByFullAddress(myAddress);

        log.debug("We received banned seed nodes={}, seedNodeAddresses={}", bannedHosts, nodeAddresses);
        return new CoreSeedNodesRepository(nodeAddresses.toSet());
    }

    private Set<String> getBannedHosts(BisqEnvironment environment) {
        return Optional.ofNullable(environment.getBannedSeedNodes())
                .map(HashSet::new)
                .map(hosts -> (Set<String>) hosts)
                .orElse(Collections.emptySet());
    }
}
