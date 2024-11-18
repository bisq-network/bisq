package bisq.core.btc.nodes;

import bisq.network.p2p.NodeAddress;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.Nullable;

@Slf4j
public class FederatedBtcNodeProvider {

    static List<BtcNodes.BtcNode> getNodes(List<BtcNodes.BtcNode> hardcodedBtcNodes,
                                           List<String> filterProvidedBtcNodesConfig,
                                           List<String> bannedBtcNodesConfig) {
        Set<BtcNodes.BtcNode> filterProvidedBtcNodes = filterProvidedBtcNodesConfig.stream()
                .filter(n -> !n.isEmpty())
                .map(FederatedBtcNodeProvider::getNodeAddress)
                .filter(Objects::nonNull)
                .map(nodeAddress -> new BtcNodes.BtcNode(null, nodeAddress.getHostName(), null, nodeAddress.getPort(), "Provided by filter"))
                .collect(Collectors.toSet());
        hardcodedBtcNodes.addAll(filterProvidedBtcNodes);

        Set<NodeAddress> bannedBtcNodeHostNames = bannedBtcNodesConfig.stream()
                .filter(n -> !n.isEmpty())
                .map(FederatedBtcNodeProvider::getNodeAddress)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return hardcodedBtcNodes.stream()
                .filter(btcNode -> {
                    String nodeAddress = btcNode.hasOnionAddress() ? btcNode.getOnionAddress() :
                            btcNode.getHostNameOrAddress();
                    Objects.requireNonNull(nodeAddress);

                    int port = btcNode.getPort();

                    for (NodeAddress bannedAddress : bannedBtcNodeHostNames) {
                        boolean isBanned = nodeAddress.equals(bannedAddress.getHostName()) &&
                                port == bannedAddress.getPort();
                        if (isBanned) {
                            return false;
                        }
                    }

                    return true;
                })
                .collect(Collectors.toList());
    }

    @Nullable
    private static NodeAddress getNodeAddress(String address) {
        try {
            return new NodeAddress(address);
        } catch (Throwable t) {
            log.error("exception when filtering banned seednodes", t);
        }
        return null;
    }
}
