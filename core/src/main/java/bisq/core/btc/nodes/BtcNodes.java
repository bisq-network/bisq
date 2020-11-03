/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.btc.nodes;

import bisq.common.config.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

// Managed here: https://github.com/bisq-network/roles/issues/39
@Slf4j
public class BtcNodes {

    public enum BitcoinNodesOption {
        PROVIDED,
        CUSTOM,
        PUBLIC
    }

    // For other base currencies or testnet we ignore provided nodes
    public List<BtcNode> getProvidedBtcNodes() {
        return useProvidedBtcNodes() ?
                Arrays.asList(
                        // emzy
                        new BtcNode("kirsche.emzy.de", "fz6nsij6jiyuwlsc.onion", "78.47.61.83", BtcNode.DEFAULT_PORT, "@emzy"),
                        new BtcNode("node2.emzy.de", "c6ac4jdfyeiakex2.onion", "62.171.129.32", BtcNode.DEFAULT_PORT, "@emzy"),
                        new BtcNode("node1.emzy.de", "sjyzmwwu6diiit3r.onion", "167.86.90.239", BtcNode.DEFAULT_PORT, "@emzy"),
                        new BtcNode(null, "3xucqntxp5ddoaz5.onion", null, BtcNode.DEFAULT_PORT, "@emzy"), // cannot provide IP because no static IP

                        // ripcurlx
                        new BtcNode("bitcoin.christophatteneder.com", "lgkvbvro67jomosw.onion", "174.138.35.229", BtcNode.DEFAULT_PORT, "@Christoph"),

                        // mrosseel
                        new BtcNode("btc.vante.me", "4jyh6llqj264oggs.onion", "94.23.21.80", BtcNode.DEFAULT_PORT, "@miker"),
                        new BtcNode("btc2.vante.me", "mxdtrjhe2yfsx3pg.onion", "94.23.205.110", BtcNode.DEFAULT_PORT, "@miker"),

                        // sqrrm
                        new BtcNode("btc1.sqrrm.net", "3r44ddzjitznyahw.onion", "185.25.48.184", BtcNode.DEFAULT_PORT, "@sqrrm"),
                        new BtcNode("btc2.sqrrm.net", "i3a5xtzfm4xwtybd.onion", "81.171.22.143", BtcNode.DEFAULT_PORT, "@sqrrm"),

                        // KanoczTomas
                        new BtcNode("btc.ispol.sk", "mbm6ffx6j5ygi2ck.onion", "193.58.196.212", BtcNode.DEFAULT_PORT, "@KanoczTomas"),

                        // Devin Bileck
                        new BtcNode("btc1.bisq.services", "lva54pnbq2nsmjyr.onion", "172.105.21.216", BtcNode.DEFAULT_PORT, "@devinbileck"),
                        new BtcNode("btc2.bisq.services", "qxjrxmhyqp5vy5hj.onion", "173.255.240.205", BtcNode.DEFAULT_PORT, "@devinbileck"),

                        // m52go
                        new BtcNode("btc.bisq.cc", "4nnuyxm5k5tlyjq3.onion", "167.71.168.194", BtcNode.DEFAULT_PORT, "@m52go"),

                        // wiz
                        new BtcNode("node100.hnl.wiz.biz", "m3yqzythryowgedc.onion", "103.99.168.100", BtcNode.DEFAULT_PORT, "@wiz"),
                        new BtcNode("node140.hnl.wiz.biz", "jiuuuislm7ooesic.onion", "103.99.168.140", BtcNode.DEFAULT_PORT, "@wiz"),
                        new BtcNode("node210.fmt.wiz.biz", "orsy2v63ecrmdj55.onion", "103.99.170.210", BtcNode.DEFAULT_PORT, "@wiz"),
                        new BtcNode("node220.fmt.wiz.biz", "z6mbqq7llxlrn4kq.onion", "103.99.170.220", BtcNode.DEFAULT_PORT, "@wiz"),

                        // Rob Kaandorp
                        new BtcNode(null, "2pj2o2mrawj7yotg.onion", null, BtcNode.DEFAULT_PORT, "@robkaandorp") // cannot provide IP because no static IP
                ) :
                new ArrayList<>();
    }

    public boolean useProvidedBtcNodes() {
        return Config.baseCurrencyNetwork().isMainnet();
    }

    public static List<BtcNodes.BtcNode> toBtcNodesList(Collection<String> nodes) {
        return nodes.stream()
                .filter(e -> !e.isEmpty())
                .map(BtcNodes.BtcNode::fromFullAddress)
                .collect(Collectors.toList());
    }

    @EqualsAndHashCode
    @Getter
    public static class BtcNode {
        private static final int DEFAULT_PORT = Config.baseCurrencyNetworkParameters().getPort(); //8333

        @Nullable
        private final String onionAddress;
        @Nullable
        private final String hostName;
        @Nullable
        private final String operator; // null in case the user provides a list of custom btc nodes
        @Nullable
        private final String address; // IPv4 address
        private int port = DEFAULT_PORT;

        /**
         * @param fullAddress [IPv4 address:port or onion:port]
         * @return BtcNode instance
         */
        public static BtcNode fromFullAddress(String fullAddress) {
            String[] parts = fullAddress.split("]");
            checkArgument(parts.length > 0);
            String host = "";
            int port = DEFAULT_PORT;
            if (parts[0].contains("[") && parts[0].contains(":")) {
                // IPv6 address and optional port number
                // address part delimited by square brackets e.g. [2a01:123:456:789::2]:8333
                host = parts[0].replace("[", "").replace("]", "");
                if (parts.length == 2)
                    port = Integer.parseInt(parts[1].replace(":", ""));
            }
            else if (parts[0].contains(":") && !parts[0].contains(".")) {
                // IPv6 address only; not delimited by square brackets
                host = parts[0];
            }
            else if (parts[0].contains(".")) {
                // address and an optional port number
                // e.g. 127.0.0.1:8333 or abcdef123xyz.onion:9999
                parts = fullAddress.split(":");
                checkArgument(parts.length > 0);
                host = parts[0];
                if (parts.length == 2)
                    port = Integer.parseInt(parts[1]);
            }

            checkArgument(host.length()>0, "BtcNode address format not recognised");
            return host.contains(".onion") ? new BtcNode(null, host, null, port, null) : new BtcNode(null, null, host, port, null);
        }

        public BtcNode(@Nullable String hostName, @Nullable String onionAddress, @Nullable String address, int port, @Nullable String operator) {
            this.hostName = hostName;
            this.onionAddress = onionAddress;
            this.address = address;
            this.port = port;
            this.operator = operator;
        }

        public boolean hasOnionAddress() {
            return onionAddress != null;
        }

        public String getHostNameOrAddress() {
            if (hostName != null)
                return hostName;
            else
                return address;
        }

        public boolean hasClearNetAddress() {
            return hostName != null || address != null;
        }

        @Override
        public String toString() {
            return "onionAddress='" + onionAddress + '\'' +
                    ", hostName='" + hostName + '\'' +
                    ", address='" + address + '\'' +
                    ", port='" + port + '\'' +
                    ", operator='" + operator;
        }
    }
}
