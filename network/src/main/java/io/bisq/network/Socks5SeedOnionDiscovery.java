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
        // List copied from bitcoin-core on 2017-11-03
        // https://raw.githubusercontent.com/bitcoin/bitcoin/master/contrib/seeds/nodes_main.txt

        return new String[]{
            "226eupdnaouu4h2v.onion",
            "2frgxpe5mheghyom.onion",
            "3ihjnsvwc3x6dp2o.onion",
            "3w77hrilg6q64opl.onion",
            "4ls4o6iszcd7mkfw.onion",
            "4p3abjxqppzxi7qi.onion",
            "546esc6botbjfbxb.onion",
            "5msftytzlsskr4ut.onion",
            "5ty6rxpgrkmdnk4a.onion",
            "akmqyuidrf56ip26.onion",
            "alhlegtjkdmbqsvt.onion",
            "bafk5ioatlgt7dgl.onion",
            "bup5n5e3kurvjzf3.onion",
            "cjygd7pu5lqkky5j.onion",
            "cyvpgt25274i5b7c.onion",
            "dekj4wacywpqsad3.onion",
            "dqpxwlpnv3z3hznl.onion",
            "drarzpycbtxwbcld.onion",
            "drp4pvejybx2ejdr.onion",
            "dxkmtmwiq7ddtgul.onion",
            "e6j57zkyibu2smad.onion",
            "ejcqevujcqltqn7d.onion",
            "eqgbt2ghfvsshbvo.onion",
            "fgizgkdnndilo6ka.onion",
            "fqxup4oev33eeidg.onion",
            "gb5ypqt63du3wfhn.onion",
            "ggdy2pb2avlbtjwq.onion",
            "hahhloezyfqh3hci.onion",
            "ihdv6bzz2gx72fs7.onion",
            "in7r5ieo7ogkxbne.onion",
            "kvd44sw7skb5folw.onion",
            "mn744hbioayn3ojs.onion",
            "ms4ntrrisfxzpvmy.onion",
            "n5lqwpjabqg62ihx.onion",
            "o4sl5na6jeqgi3l6.onion",
            "omjv24nbck4k5ud6.onion",
            "po3j2hfkmf7sh36o.onion",
            "psco6bxjewljrczx.onion",
            "qi5yr6lvlckzffor.onion",
            "qlv6py6hdtzipntn.onion",
            "qynmpgdbp23xyfnj.onion",
            "rhtawcs7xak4gi3t.onion",
            "rjacws757ai66lre.onion",
            "rjlnp3hwvrsmap6e.onion",
            "rkdvqcrtfv6yt4oy.onion",
            "rlafimkctvz63llg.onion",
            "rlj4ppok4dnsdu5n.onion",
            "seoskudzk6vn6mqz.onion",
            "tayqi57tfiy7x3vk.onion",
            "tchop676j6yppwwm.onion",
            "trrtyp3sirmwttyu.onion",
            "tx4zd7d5exonnblh.onion",
            "u4ecb7txxi6l3gix.onion",
            "umcxcmfycvejw264.onion",
            "v7xqd42ocemalurd.onion",
            "vb267mt3lnwfdmdb.onion",
            "vev3n5fxfrtqj6e5.onion",
            "visevrizz3quyagj.onion",
            "vpg6p5e5ln33dqtp.onion",
            "vr2ruasinafoy3fl.onion",
            "x6pthvd5x6abyfo7.onion",
            "xbwbzrspvav7u5ie.onion",
            "xfcevnql2chnawko.onion",
            "ycivnom44dmxx4ob.onion",
            "yzdvlv5daitafekn.onion"
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
