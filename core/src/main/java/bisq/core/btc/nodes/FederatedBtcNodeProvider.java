package bisq.core.btc.nodes;

import bisq.network.p2p.NodeAddress;

import bisq.common.config.Config;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.Nullable;

@Slf4j
public class FederatedBtcNodeProvider {

    static List<BtcNodes.BtcNode> getNodes(List<BtcNodes.BtcNode> hardcodedBtcNodes, Config config) {
        Set<BtcNodes.BtcNode> filterProvidedBtcNodes = config.filterProvidedBtcNodes.stream()
                .filter(n -> !n.isEmpty())
                .map(FederatedBtcNodeProvider::getNodeAddress)
                .filter(Objects::nonNull)
                .map(nodeAddress -> new BtcNodes.BtcNode(null, nodeAddress.getHostName(), null, nodeAddress.getPort(), "Provided by filter"))
                .collect(Collectors.toSet());
        hardcodedBtcNodes.addAll(filterProvidedBtcNodes);

        Set<String> bannedBtcNodeHostNames = config.bannedBtcNodes.stream()
                .filter(n -> !n.isEmpty())
                .map(FederatedBtcNodeProvider::getNodeAddress)
                .filter(Objects::nonNull)
                .map(NodeAddress::getHostName)
                .collect(Collectors.toSet());
        return hardcodedBtcNodes.stream()
                .filter(e -> !bannedBtcNodeHostNames.contains(e.getHostName()))
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
