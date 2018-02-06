package io.bisq.core.btc.wallet;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import io.bisq.core.btc.BitcoinNodes.BtcNode;
import org.bitcoinj.core.PeerAddress;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class PeerAddressesRepository {
    private final BtcNodeConverter converter;
    private final List<BtcNode> nodes;

    PeerAddressesRepository(BtcNodeConverter converter, List<BtcNode> nodes) {
        this.converter = converter;
        this.nodes = nodes;
    }

    PeerAddressesRepository(List<BtcNode> nodes) {
        this(new BtcNodeConverter(), nodes);
    }

    List<PeerAddress> getPeerAddresses(@Nullable Socks5Proxy proxy, boolean isUseClearNodesWithProxies) {
        List<PeerAddress> result;
        // We connect to onion nodes only in case we use Tor for BitcoinJ (default) to avoid privacy leaks at
        // exit nodes with bloom filters.
        if (proxy != null) {
            List<PeerAddress> onionHosts = getOnionHosts();
            result = new ArrayList<>(onionHosts);

            if (isUseClearNodesWithProxies) {
                // We also use the clear net nodes (used for monitor)
                List<PeerAddress> torAddresses = getClearNodesBehindProxy(proxy);
                result.addAll(torAddresses);
            }
        } else {
            result = getClearNodes();
        }
        return result;
    }

    private List<PeerAddress> getClearNodes() {
        return nodes.stream()
                .filter(BtcNode::hasClearNetAddress)
                .flatMap(node -> nullableAsStream(converter.convertClearNode(node)))
                .collect(Collectors.toList());
    }

    private List<PeerAddress> getOnionHosts() {
        return nodes.stream()
                .filter(BtcNode::hasOnionAddress)
                .flatMap(node -> nullableAsStream(converter.convertOnionHost(node)))
                .collect(Collectors.toList());
    }

    private List<PeerAddress> getClearNodesBehindProxy(Socks5Proxy proxy) {
        return nodes.stream()
                .filter(BtcNode::hasClearNetAddress)
                .flatMap(node -> nullableAsStream(converter.convertWithTor(node, proxy)))
                .collect(Collectors.toList());
    }

    private static <T> Stream<T> nullableAsStream(@Nullable T item) {
        return Optional.ofNullable(item)
                .map(Stream::of)
                .orElse(Stream.empty());
    }
}
