package bisq.network;

import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.net.discovery.PeerDiscoveryException;

import java.net.InetSocketAddress;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static bisq.network.Socks5MultiDiscovery.SOCKS5_DISCOVER_ADDR;
import static bisq.network.Socks5MultiDiscovery.SOCKS5_DISCOVER_ONION;

/**
 * BitcoinCoreSeedListDiscovery delivers the list of known Bitcoin .onion seeds shipped
 * in Bitcoin Core.
 */
public class BitcoinCoreSeedListDiscovery implements PeerDiscovery {
    private final int discoveryMode;

    public BitcoinCoreSeedListDiscovery(int discoveryMode) {
        this.discoveryMode = discoveryMode;
    }

    @Override
    public InetSocketAddress[] getPeers(long services,
                                        long timeoutValue,
                                        TimeUnit timeoutUnit) throws PeerDiscoveryException {
        try (BufferedReader bufferedReader = openStreamToBitcoinCoreSeedList()) {
            return extractSeedNodes(bufferedReader.lines())
                    .filter(address -> !address.isEmpty())
                    .map(this::mapToInetSocketAddress)
                    .toArray(InetSocketAddress[]::new);

        } catch (IOException e) {
            throw new PeerDiscoveryException(e);
        }
    }

    @Override
    public void shutdown() {
    }

    private BufferedReader openStreamToBitcoinCoreSeedList() {
        ClassLoader classLoader = getClass().getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream("bitcoin_core_nodes_main.txt");
        if (inputStream == null) {
            throw new IllegalStateException("Bitcoin Core seed nodes list missing in resources.");
        }

        InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
        return new BufferedReader(inputStreamReader);

    }

    private Stream<String> extractSeedNodes(Stream<String> lines) {
        return lines.filter(this::isSupportedAddress)
                .map(this::removeComment);
    }

    private InetSocketAddress mapToInetSocketAddress(String address) {
        // '<hostname>:port'
        String[] hostnameAndPort = address.split(":");
        if (hostnameAndPort.length != 2) {
            throw new IllegalStateException("Invalid address: " + address);
        }

        try {
            int port = Integer.parseInt(hostnameAndPort[1]);
            return InetSocketAddress.createUnresolved(hostnameAndPort[0], port);

        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid port number in address: " + address, e);
        }
    }

    private String removeComment(String line) {
        // '<ip>:port # AS<no>'
        return line.contains("#") ? line.split("#")[0].trim() : line;
    }

    private boolean isSupportedAddress(String line) {
        if (!includeIpV4Addresses() && isIpV4Address(line)) {
            return false;
        }

        if (!includeOnionAddresses() && isOnionAddress(line)) {
            return false;
        }

        return !isIpV6Address(line) && !isI2PAddress(line);
    }

    private boolean includeIpV4Addresses() {
        return (discoveryMode & SOCKS5_DISCOVER_ADDR) != 0;
    }

    private boolean includeOnionAddresses() {
        return (discoveryMode & SOCKS5_DISCOVER_ONION) != 0;
    }

    private boolean isIpV4Address(String line) {
        // '123.456.789.012:1234 # AS1234'
        return Pattern.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+.+", line);
    }

    private boolean isIpV6Address(String line) {
        return line.startsWith("[") && line.contains(":");
    }

    private boolean isOnionAddress(String line) {
        return line.endsWith(".onion:8333");
    }

    private boolean isI2PAddress(String line) {
        return line.endsWith(".b32.i2p:0");
    }
}
