package bisq.core.btc.nodes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {"123.456.890.123", "2001:db8:85a3:8d3:1319:8a2e:370"})
    void fromFullAddressIpNoPort(String address) {
        BtcNodes.BtcNode btcNode = BtcNodes.BtcNode.fromFullAddress(address);
        assertThat(btcNode.getAddress(), equalTo(address));
        assertThat(btcNode.getPort(), equalTo(BtcNodes.BtcNode.DEFAULT_PORT));
    }

    @Test
    void fromFullAddressIpV4() {
        String address = "123.456.890.123";
        int port = 4567;
        BtcNodes.BtcNode btcNode = BtcNodes.BtcNode.fromFullAddress(address + ":" + port);

        assertThat(btcNode.getAddress(), equalTo(address));
        assertThat(btcNode.getPort(), equalTo(port));
    }

    @Test
    void fromFullAddressIpV6() {
        String address = "2001:db8:85a3:8d3:1319:8a2e:370";
        int port = 7348;
        String fullAddress = "[" + address + "]:" + port;
        BtcNodes.BtcNode btcNode = BtcNodes.BtcNode.fromFullAddress(fullAddress);

        assertThat(btcNode.getAddress(), equalTo(address));
        assertThat(btcNode.getPort(), equalTo(port));
    }

    @Test
    void fromFullAddressHostNameNoPort() {
        String hostname = "btc-node.bisq.network";
        BtcNodes.BtcNode btcNode = BtcNodes.BtcNode.fromFullAddress(hostname);
        assertThat(btcNode.getHostName(), equalTo(hostname));
        assertThat(btcNode.getPort(), equalTo(BtcNodes.BtcNode.DEFAULT_PORT));
    }

    @Test
    void fromFullAddressHostName() {
        String hostname = "btc-node.bisq.network";
        int port = 4567;
        BtcNodes.BtcNode btcNode = BtcNodes.BtcNode.fromFullAddress(hostname + ":" + port);

        assertThat(btcNode.getHostName(), equalTo(hostname));
        assertThat(btcNode.getPort(), equalTo(port));
    }

    @Test
    void fromFullAddressOnionNoPort() {
        String onionAddress = "alice.onion";
        BtcNodes.BtcNode btcNode = BtcNodes.BtcNode.fromFullAddress(onionAddress);
        assertThat(btcNode.getOnionAddress(), equalTo(onionAddress));
        assertThat(btcNode.getPort(), equalTo(BtcNodes.BtcNode.DEFAULT_PORT));
    }

    @Test
    void fromFullAddressOnion() {
        String onionAddress = "alice.onion";
        int port = 4567;
        BtcNodes.BtcNode btcNode = BtcNodes.BtcNode.fromFullAddress(onionAddress + ":" + port);

        assertThat(btcNode.getOnionAddress(), equalTo(onionAddress));
        assertThat(btcNode.getPort(), equalTo(port));
    }
}
