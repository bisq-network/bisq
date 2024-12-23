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

    @Test
    void bannedIpV4Node() {
        String bannedAddress = "123.456.890.123";
        int port = 4567;

        var hardcodedNodes = List.of(
                new BtcNodes.BtcNode(null, "alice.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@alice"),
                new BtcNodes.BtcNode(null, null, bannedAddress, port, "@bob"),
                new BtcNodes.BtcNode(null, "charlie.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@charlie")
        );

        List<BtcNodes.BtcNode> mutableHardcodedList = new ArrayList<>(hardcodedNodes);
        List<String> filterProvidedBtcNodes = Collections.emptyList();
        String bannedFullAddress = bannedAddress + ":" + port;
        List<String> bannedBtcNodes = List.of(bannedFullAddress);

        List<BtcNodes.BtcNode> selectedNodes = FederatedBtcNodeProvider
                .getNodes(mutableHardcodedList, filterProvidedBtcNodes, bannedBtcNodes);

        var expected = List.of(
                new BtcNodes.BtcNode(null, "alice.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@alice"),
                new BtcNodes.BtcNode(null, "charlie.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@charlie")
        );
        assertIterableEquals(expected, selectedNodes);
    }

    @Test
    void bannedIpV4NodeWrongPort() {
        String bannedAddress = "123.456.890.123";

        var hardcodedNodes = List.of(
                new BtcNodes.BtcNode(null, "alice.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@alice"),
                new BtcNodes.BtcNode(null, null, bannedAddress, 4567, "@bob"),
                new BtcNodes.BtcNode(null, "charlie.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@charlie")
        );

        List<BtcNodes.BtcNode> mutableHardcodedList = new ArrayList<>(hardcodedNodes);
        List<String> filterProvidedBtcNodes = Collections.emptyList();
        String bannedFullAddress = bannedAddress + ":" + 1234;
        List<String> bannedBtcNodes = List.of(bannedFullAddress);

        List<BtcNodes.BtcNode> selectedNodes = FederatedBtcNodeProvider
                .getNodes(mutableHardcodedList, filterProvidedBtcNodes, bannedBtcNodes);

        assertIterableEquals(hardcodedNodes, selectedNodes);
    }

    @Test
    void bannedIpV6Node() {
        String bannedAddress = "2001:db8:85a3:8d3:1319:8a2e:370";
        int port = 7348;

        var hardcodedNodes = List.of(
                new BtcNodes.BtcNode(null, "alice.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@alice"),
                new BtcNodes.BtcNode(null, null, bannedAddress, port, "@bob"),
                new BtcNodes.BtcNode(null, "charlie.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@charlie")
        );

        List<BtcNodes.BtcNode> mutableHardcodedList = new ArrayList<>(hardcodedNodes);
        List<String> filterProvidedBtcNodes = Collections.emptyList();
        String bannedFullAddress = "[" + bannedAddress + "]" + ":" + port;
        List<String> bannedBtcNodes = List.of(bannedFullAddress);

        List<BtcNodes.BtcNode> selectedNodes = FederatedBtcNodeProvider
                .getNodes(mutableHardcodedList, filterProvidedBtcNodes, bannedBtcNodes);

        var expected = List.of(
                new BtcNodes.BtcNode(null, "alice.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@alice"),
                new BtcNodes.BtcNode(null, "charlie.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@charlie")
        );
        assertIterableEquals(expected, selectedNodes);
    }

    @Test
    void bannedIpV6NodeWrongPort() {
        String bannedAddress = "2001:db8:85a3:8d3:1319:8a2e:370";

        var hardcodedNodes = List.of(
                new BtcNodes.BtcNode(null, "alice.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@alice"),
                new BtcNodes.BtcNode(null, null, bannedAddress, 7348, "@bob"),
                new BtcNodes.BtcNode(null, "charlie.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@charlie")
        );

        List<BtcNodes.BtcNode> mutableHardcodedList = new ArrayList<>(hardcodedNodes);
        List<String> filterProvidedBtcNodes = Collections.emptyList();
        String bannedFullAddress = "[" + bannedAddress + "]" + ":" + 1234;
        List<String> bannedBtcNodes = List.of(bannedFullAddress);

        List<BtcNodes.BtcNode> selectedNodes = FederatedBtcNodeProvider
                .getNodes(mutableHardcodedList, filterProvidedBtcNodes, bannedBtcNodes);

        assertIterableEquals(hardcodedNodes, selectedNodes);
    }

    @Test
    void bannedHostNameNode() {
        String bannedHostName = "btc1.dnsalias.net";
        int port = 5678;

        var hardcodedNodes = List.of(
                new BtcNodes.BtcNode(null, "alice.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@alice"),
                new BtcNodes.BtcNode(null, bannedHostName, null, port, "@bob"),
                new BtcNodes.BtcNode(null, "charlie.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@charlie")
        );

        List<BtcNodes.BtcNode> mutableHardcodedList = new ArrayList<>(hardcodedNodes);
        List<String> filterProvidedBtcNodes = Collections.emptyList();
        List<String> bannedBtcNodes = List.of(bannedHostName + ":" + port);

        List<BtcNodes.BtcNode> selectedNodes = FederatedBtcNodeProvider
                .getNodes(mutableHardcodedList, filterProvidedBtcNodes, bannedBtcNodes);

        var expected = List.of(
                new BtcNodes.BtcNode(null, "alice.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@alice"),
                new BtcNodes.BtcNode(null, "charlie.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@charlie")
        );
        assertIterableEquals(expected, selectedNodes);
    }

    @Test
    void bannedHostNameNodeWrongPort() {
        String bannedHostName = "btc1.dnsalias.net";

        var hardcodedNodes = List.of(
                new BtcNodes.BtcNode(null, "alice.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@alice"),
                new BtcNodes.BtcNode(null, bannedHostName, null, 5678, "@bob"),
                new BtcNodes.BtcNode(null, "charlie.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@charlie")
        );

        List<BtcNodes.BtcNode> mutableHardcodedList = new ArrayList<>(hardcodedNodes);
        List<String> filterProvidedBtcNodes = Collections.emptyList();
        List<String> bannedBtcNodes = List.of(bannedHostName + ":" + 1234);

        List<BtcNodes.BtcNode> selectedNodes = FederatedBtcNodeProvider
                .getNodes(mutableHardcodedList, filterProvidedBtcNodes, bannedBtcNodes);

        assertIterableEquals(hardcodedNodes, selectedNodes);
    }

    @Test
    void bannedOnionNode() {
        String bannedOnionAddress = "bob.onion";

        var hardcodedNodes = List.of(
                new BtcNodes.BtcNode(null, "alice.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@alice"),
                new BtcNodes.BtcNode(null, bannedOnionAddress, null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@bob"),
                new BtcNodes.BtcNode(null, "charlie.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@charlie")
        );

        List<BtcNodes.BtcNode> mutableHardcodedList = new ArrayList<>(hardcodedNodes);
        List<String> filterProvidedBtcNodes = Collections.emptyList();
        List<String> bannedBtcNodes = List.of(bannedOnionAddress + ":" + BtcNodes.BtcNode.DEFAULT_PORT);

        List<BtcNodes.BtcNode> selectedNodes = FederatedBtcNodeProvider
                .getNodes(mutableHardcodedList, filterProvidedBtcNodes, bannedBtcNodes);

        var expected = List.of(
                new BtcNodes.BtcNode(null, "alice.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@alice"),
                new BtcNodes.BtcNode(null, "charlie.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@charlie")
        );
        assertIterableEquals(expected, selectedNodes);
    }

    @Test
    void bannedOnionNodeWrongPort() {
        String bannedOnionAddress = "bob.onion";

        var hardcodedNodes = List.of(
                new BtcNodes.BtcNode(null, "alice.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@alice"),
                new BtcNodes.BtcNode(null, bannedOnionAddress, null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@bob"),
                new BtcNodes.BtcNode(null, "charlie.onion", null,
                        BtcNodes.BtcNode.DEFAULT_PORT, "@charlie")
        );

        List<BtcNodes.BtcNode> mutableHardcodedList = new ArrayList<>(hardcodedNodes);
        List<String> filterProvidedBtcNodes = Collections.emptyList();
        List<String> bannedBtcNodes = List.of(bannedOnionAddress + ":" + 1234);

        List<BtcNodes.BtcNode> selectedNodes = FederatedBtcNodeProvider
                .getNodes(mutableHardcodedList, filterProvidedBtcNodes, bannedBtcNodes);

        assertIterableEquals(hardcodedNodes, selectedNodes);
    }
}
