/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.network;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.net.discovery.PeerDiscoveryException;
import org.libdohj.params.AbstractLitecoinParams;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;


/**
 * Socks5SeedOnionDiscovery provides a list of known Bitcoin .onion seeds.
 * These are nodes running as hidden services on the Tor network.
 */
public class Socks5SeedOnionDiscovery implements PeerDiscovery {
    private InetSocketAddress[] seedAddrs;

    /**
     * Supports finding peers by hostname over a socks5 proxy.
     *
     * @param proxy  proxy the socks5 proxy to connect over.
     * @param params param to be used for seed and port information.
     */
    public Socks5SeedOnionDiscovery(@SuppressWarnings("UnusedParameters") Socks5Proxy proxy, NetworkParameters params) {
        // We do this because NetworkParameters does not contain any .onion
        // seeds.  Perhaps someday...
        String[] seedAddresses = {};
        switch (params.getId()) {
            case NetworkParameters.ID_MAINNET:
                seedAddresses = mainNetSeeds();
                break;
            case NetworkParameters.ID_TESTNET:
                seedAddresses = testNet3Seeds();
                break;
            case AbstractLitecoinParams.ID_LITE_MAINNET:
                seedAddresses = LitecoinMainNetSeeds();
                break;
            case AbstractLitecoinParams.ID_LITE_TESTNET:
                seedAddresses = LitecoinTestNet4Seeds();
                break;
        }

        this.seedAddrs = convertAddrsString(seedAddresses, params.getPort());
    }

    /**
     * returns .onion nodes available on mainnet
     */
    private String[] mainNetSeeds() {
        // this list copied from bitcoin-core on 2017-01-19
        //   https://github.com/bitcoin/bitcoin/blob/57b34599b2deb179ff1bd97ffeab91ec9f904d85/contrib/seeds/nodes_main.txt

        return new String[]{
                "3ffk7iumtx3cegbi.onion",
                "3nmbbakinewlgdln.onion",
                "4j77gihpokxu2kj4.onion",
                "546esc6botbjfbxb.onion",
                "5at7sq5nm76xijkd.onion",
                "77mx2jsxaoyesz2p.onion",
                "7g7j54btiaxhtsiy.onion",
                "a6obdgzn67l7exu3.onion",
                "ab64h7olpl7qpxci.onion",
                "am2a4rahltfuxz6l.onion",
                "azuxls4ihrr2mep7.onion",
                "bitcoin7bi4op7wb.onion",
                "bitcoinostk4e4re.onion",
                "bk7yp6epnmcllq72.onion",
                "bmutjfrj5btseddb.onion",
                "ceeji4qpfs3ms3zc.onion",
                "clexmzqio7yhdao4.onion",
                "gb5ypqt63du3wfhn.onion",
                "h2vlpudzphzqxutd.onion",
                "ncwk3lutemffcpc4.onion",
                "okdzjarwekbshnof.onion",
                "pjghcivzkoersesd.onion",
                "rw7ocjltix26mefn.onion",
                "uws7itep7o3yinxo.onion",
                "vk3qjdehyy4dwcxw.onion",
                "vqpye2k5rcqvj5mq.onion",
                "wpi7rpvhnndl52ee.onion"
        };
    }

    /**
     * returns .onion nodes available on testnet3
     */
    private String[] testNet3Seeds() {
        // this list copied from bitcoin-core on 2017-01-19
        //   https://github.com/bitcoin/bitcoin/blob/57b34599b2deb179ff1bd97ffeab91ec9f904d85/contrib/seeds/nodes_test.txt
        return new String[]{
                "thfsmmn2jbitcoin.onion",
                "it2pj4f7657g3rhi.onion",
                "nkf5e6b7pl4jfd4a.onion",
                "4zhkir2ofl7orfom.onion",
                "t6xj6wilh4ytvcs7.onion",
                "i6y6ivorwakd7nw3.onion",
                "ubqj4rsu3nqtxmtp.onion"
        };
    }

    /**
     * returns .onion nodes available on mainnet
     */
    private String[] LitecoinMainNetSeeds() {
        return new String[]{
        };
    }

    /**
     * returns .onion nodes available on testnet3
     */
    private String[] LitecoinTestNet4Seeds() {
        return new String[]{
        };
    }

    /**
     * Returns an array containing all the Bitcoin nodes within the list.
     */
    @Override
    public InetSocketAddress[] getPeers(long services, long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {
        if (services != 0)
            throw new PeerDiscoveryException("DNS seeds cannot filter by services: " + services);
        return seedAddrs;
    }

    /**
     * Converts an array of hostnames to array of unresolved InetSocketAddress
     */
    private InetSocketAddress[] convertAddrsString(String[] addrs, int port) {
        InetSocketAddress[] list = new InetSocketAddress[addrs.length];
        for (int i = 0; i < addrs.length; i++) {
            list[i] = InetSocketAddress.createUnresolved(addrs[i], port);
        }
        return list;
    }

    @Override
    public void shutdown() {
        //TODO should we add a DnsLookupTor.shutdown() ?
    }
}
