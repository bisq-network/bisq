package io.bisq.core.network;

import com.google.common.collect.ImmutableSet;
import io.bisq.network.p2p.NodeAddress;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class NodeAddresses {
    private final Set<NodeAddress> delegate;

    static NodeAddresses createFromSeedNodes(@Nullable String seedNodes) {
        Set<NodeAddress> addresses = Optional.ofNullable(seedNodes)
                .map(StringUtils::deleteWhitespace)
                .map(nodes -> nodes.split(","))
                .map(Arrays::stream)
                .orElse(Stream.empty())
                .map(NodeAddress::new)
                .collect(Collectors.toSet());
        return new NodeAddresses(addresses);
    }

    static NodeAddresses createFromSet(Set<NodeAddress> addresses, int networkId) {
        Set<NodeAddress> delegate = addresses.stream()
                .filter(address -> isAddressFromNetwork(address, networkId))
                .collect(Collectors.toSet());
        return new NodeAddresses(delegate);
    }

    private NodeAddresses(Set<NodeAddress> delegate) {
        this.delegate = delegate;
    }

    private static boolean isAddressFromNetwork(NodeAddress address, int networkId) {
        String suffix = "0" + networkId;
        int port = address.getPort();
        String portAsString = String.valueOf(port);
        return portAsString.endsWith(suffix);
    }

    NodeAddresses excludeByHost(Set<String> hosts) {
        Set<NodeAddress> addresses = new HashSet<>(delegate);
        addresses.removeIf(address -> {
            String hostName = address.getHostName();
            return !hosts.contains(hostName);
        });
        return new NodeAddresses(addresses);
    }

    NodeAddresses excludeByFullAddress(String fullAddress) {
        Set<NodeAddress> addresses = new HashSet<>(delegate);
        addresses.removeIf(address -> fullAddress.equals(address.getFullAddress()));
        return new NodeAddresses(delegate);
    }

    boolean isEmpty() {
        return delegate.isEmpty();
    }

    Set<NodeAddress> toSet() {
        return ImmutableSet.copyOf(delegate);
    }
}
