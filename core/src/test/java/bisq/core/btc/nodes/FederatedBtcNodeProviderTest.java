package bisq.core.btc.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public class FederatedBtcNodeProviderTest {
    @Test
    void onlyHardcodedNodes() {
        var hardcodedNodes = List.of(
                new BtcNodes.BtcNode(null, "alice.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@alice"),
                new BtcNodes.BtcNode(null, "bob.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@bob"),
                new BtcNodes.BtcNode(null, "charlie.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@charlie")
        );

        List<BtcNodes.BtcNode> mutableHardcodedList = new ArrayList<>(hardcodedNodes);
        List<String> filterProvidedBtcNodes = Collections.emptyList();
        List<String> bannedBtcNodes = Collections.emptyList();

        List<BtcNodes.BtcNode> selectedNodes = FederatedBtcNodeProvider
                .getNodes(mutableHardcodedList, filterProvidedBtcNodes, bannedBtcNodes);

        assertIterableEquals(hardcodedNodes, selectedNodes);
    }
}
