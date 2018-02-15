package io.bisq.core.network;

import io.bisq.network.p2p.NodeAddress;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

class NodeAddresses extends ImmutableSetDecorator<NodeAddress> {
    static NodeAddresses fromString(String seedNodes) {
        String trimmed = StringUtils.deleteWhitespace(seedNodes);
        String[] nodes = trimmed.split(",");
        return Arrays.stream(nodes)
                .map(NodeAddress::new)
                .collect(collector());
    }

    NodeAddresses(Set<NodeAddress> delegate) {
        super(delegate);
    }

    NodeAddresses excludeByHost(Set<String> hosts) {
        Set<NodeAddress> copy = new HashSet<>(this);
        copy.removeIf(address -> {
            String hostName = address.getHostName();
            return hosts.contains(hostName);
        });
        return new NodeAddresses(copy);
    }

    NodeAddresses excludeByFullAddress(String fullAddress) {
        Set<NodeAddress> copy = new HashSet<>(this);
        copy.removeIf(address -> fullAddress.equals(address.getFullAddress()));
        return new NodeAddresses(copy);
    }

    static Collector<NodeAddress, ?, NodeAddresses> collector() {
        return Collectors.collectingAndThen(Collectors.toSet(), NodeAddresses::new);
    }
}
