package bisq.network;

import org.bitcoinj.net.discovery.PeerDiscoveryException;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BitcoinCoreSeedListDiscoveryTest {
    @Test
    void allTest() throws PeerDiscoveryException {
        int mode = Socks5MultiDiscovery.SOCKS5_DISCOVER_ALL;
        BitcoinCoreSeedListDiscovery bitcoinCoreSeedListDiscovery = new BitcoinCoreSeedListDiscovery(mode);
        InetSocketAddress[] peers = bitcoinCoreSeedListDiscovery
                .getPeers(0L, 0L, TimeUnit.MILLISECONDS);
        assertTrue(peers.length > 0);
    }

    @Test
    void onlyIpV4Test() throws PeerDiscoveryException {
        int mode = Socks5MultiDiscovery.SOCKS5_DISCOVER_ADDR;
        BitcoinCoreSeedListDiscovery bitcoinCoreSeedListDiscovery = new BitcoinCoreSeedListDiscovery(mode);
        InetSocketAddress[] peers = bitcoinCoreSeedListDiscovery
                .getPeers(0L, 0L, TimeUnit.MILLISECONDS);

        assertTrue(peers.length > 0);
        assertTrue(
                Arrays.stream(peers)
                        .allMatch(this::isIpV4Address)
        );
    }

    @Test
    void onlyOnionTest() throws PeerDiscoveryException {
        int mode = Socks5MultiDiscovery.SOCKS5_DISCOVER_ONION;
        BitcoinCoreSeedListDiscovery bitcoinCoreSeedListDiscovery = new BitcoinCoreSeedListDiscovery(mode);
        InetSocketAddress[] peers = bitcoinCoreSeedListDiscovery
                .getPeers(0L, 0L, TimeUnit.MILLISECONDS);

        assertTrue(peers.length > 0);
        assertTrue(
                Arrays.stream(peers)
                        .allMatch(socketAddress -> socketAddress.getHostName().endsWith(".onion"))
        );
    }

    private boolean isIpV4Address(InetSocketAddress socketAddress) {
        try {
            return InetAddress.getByName(socketAddress.getHostName()) instanceof Inet4Address;
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
}
