package io.bisq.core.btc.wallet;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import io.bisq.core.btc.BitcoinNodes.BtcNode;
import io.bisq.core.btc.wallet.BtcNodeConverter.Facade;
import io.bisq.network.DnsLookupException;
import org.bitcoinj.core.PeerAddress;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BtcNodeConverterTest {
    @Test
    public void testConvertOnionHost() throws UnknownHostException {
        BtcNode node = mock(BtcNode.class);
        when(node.getOnionAddress()).thenReturn("aaa.onion");

        InetAddress inetAddress = mock(InetAddress.class);

        Facade facade = mock(Facade.class);
        when(facade.onionHostToInetAddress(any())).thenReturn(inetAddress);

        PeerAddress peerAddress = new BtcNodeConverter(facade).convertOnionHost(node);
        // noinspection ConstantConditions
        assertEquals(inetAddress, peerAddress.getAddr());
    }

    @Test
    public void testConvertOnionHostOnFailure() throws UnknownHostException {
        BtcNode node = mock(BtcNode.class);
        when(node.getOnionAddress()).thenReturn("aaa.onion");

        Facade facade = mock(Facade.class);
        when(facade.onionHostToInetAddress(any())).thenThrow(UnknownHostException.class);

        PeerAddress peerAddress = new BtcNodeConverter(facade).convertOnionHost(node);
        assertNull(peerAddress);
    }

    @Test
    public void testConvertClearNode() {
        final String ip = "192.168.0.1";

        BtcNode node = mock(BtcNode.class);
        when(node.getHostNameOrAddress()).thenReturn(ip);

        PeerAddress peerAddress = new BtcNodeConverter().convertClearNode(node);
        // noinspection ConstantConditions
        InetAddress inetAddress = peerAddress.getAddr();
        assertEquals(ip, inetAddress.getHostName());
    }

    @Test
    public void testConvertWithTor() throws DnsLookupException {
        InetAddress expected = mock(InetAddress.class);

        Facade facade = mock(Facade.class);
        when(facade.torLookup(any(), anyString())).thenReturn(expected);

        BtcNode node = mock(BtcNode.class);
        when(node.getHostNameOrAddress()).thenReturn("aaa.onion");

        PeerAddress peerAddress = new BtcNodeConverter(facade).convertWithTor(node, mock(Socks5Proxy.class));

        // noinspection ConstantConditions
        assertEquals(expected, peerAddress.getAddr());
    }
}
