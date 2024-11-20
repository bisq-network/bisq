package bisq.core.btc.nodes;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class BtcNodeTest {
    @Test
    void hardcodedAndFilterProvidedNodeShouldBeEqual() {
        var aliceHardcodedBtcNode = new BtcNodes.BtcNode(null,
                "alice_btc_node.onion", null,
                BtcNodes.BtcNode.DEFAULT_PORT, "@Alice");

        var aliceNodeFromFilter = new BtcNodes.BtcNode(null,
                "alice_btc_node.onion", null,
                BtcNodes.BtcNode.DEFAULT_PORT, "Provided by filter");

        assertThat(aliceHardcodedBtcNode, equalTo(aliceNodeFromFilter));
    }
}
