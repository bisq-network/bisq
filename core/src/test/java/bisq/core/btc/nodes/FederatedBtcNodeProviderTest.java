package bisq.core.btc.nodes;

import java.util.List;
import java.util.stream.Stream;

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

        Stream<String> filterProvidedBtcNodes = Stream.empty();
        Stream<String> bannedBtcNodes = Stream.empty();

        List<BtcNodes.BtcNode> selectedNodes = FederatedBtcNodeProvider
                .getNodes(hardcodedNodes.stream(), filterProvidedBtcNodes, bannedBtcNodes);

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

        Stream<String> filterProvidedBtcNodes = Stream.empty();
        String bannedFullAddress = bannedAddress + ":" + port;
        Stream<String> bannedBtcNodes = Stream.of(bannedFullAddress);

        List<BtcNodes.BtcNode> selectedNodes = FederatedBtcNodeProvider
                .getNodes(hardcodedNodes.stream(), filterProvidedBtcNodes, bannedBtcNodes);

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

        Stream<String> filterProvidedBtcNodes = Stream.empty();
        String bannedFullAddress = bannedAddress + ":" + 1234;
        Stream<String> bannedBtcNodes = Stream.of(bannedFullAddress);

        List<BtcNodes.BtcNode> selectedNodes = FederatedBtcNodeProvider
                .getNodes(hardcodedNodes.stream(), filterProvidedBtcNodes, bannedBtcNodes);

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

        Stream<String> filterProvidedBtcNodes = Stream.empty();
        String bannedFullAddress = "[" + bannedAddress + "]" + ":" + port;
        Stream<String> bannedBtcNodes = Stream.of(bannedFullAddress);

        List<BtcNodes.BtcNode> selectedNodes = FederatedBtcNodeProvider
                .getNodes(hardcodedNodes.stream(), filterProvidedBtcNodes, bannedBtcNodes);

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

        Stream<String> filterProvidedBtcNodes = Stream.empty();
        String bannedFullAddress = "[" + bannedAddress + "]" + ":" + 1234;
        Stream<String> bannedBtcNodes = Stream.of(bannedFullAddress);

        List<BtcNodes.BtcNode> selectedNodes = FederatedBtcNodeProvider
                .getNodes(hardcodedNodes.stream(), filterProvidedBtcNodes, bannedBtcNodes);

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

        Stream<String> filterProvidedBtcNodes = Stream.empty();
        Stream<String> bannedBtcNodes = Stream.of(bannedHostName + ":" + port);

        List<BtcNodes.BtcNode> selectedNodes = FederatedBtcNodeProvider
                .getNodes(hardcodedNodes.stream(), filterProvidedBtcNodes, bannedBtcNodes);

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

        Stream<String> filterProvidedBtcNodes = Stream.empty();
        Stream<String> bannedBtcNodes = Stream.of(bannedHostName + ":" + 1234);

        List<BtcNodes.BtcNode> selectedNodes = FederatedBtcNodeProvider
                .getNodes(hardcodedNodes.stream(), filterProvidedBtcNodes, bannedBtcNodes);

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

        Stream<String> filterProvidedBtcNodes = Stream.empty();
        Stream<String> bannedBtcNodes = Stream.of(bannedOnionAddress + ":" + BtcNodes.BtcNode.DEFAULT_PORT);

        List<BtcNodes.BtcNode> selectedNodes = FederatedBtcNodeProvider
                .getNodes(hardcodedNodes.stream(), filterProvidedBtcNodes, bannedBtcNodes);

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

        Stream<String> filterProvidedBtcNodes = Stream.empty();
        Stream<String> bannedBtcNodes = Stream.of(bannedOnionAddress + ":" + 1234);

        List<BtcNodes.BtcNode> selectedNodes = FederatedBtcNodeProvider
                .getNodes(hardcodedNodes.stream(), filterProvidedBtcNodes, bannedBtcNodes);

        assertIterableEquals(hardcodedNodes, selectedNodes);
    }
}
