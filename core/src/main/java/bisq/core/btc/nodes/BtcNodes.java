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
                        // Devin Bileck
                        new BtcNode("btc1.bisq.services", "devinbtctu7uctl7hly2juu3thbgeivfnvw3ckj3phy6nyvpnx66yeyd.onion", "172.105.21.216", BtcNode.DEFAULT_PORT, "@devinbileck"),
                        new BtcNode(null, "devinbtcmwkuitvxl3tfi5of4zau46ymeannkjv6fpnylkgf3q5fa3id.onion", null, BtcNode.DEFAULT_PORT, "@devinbileck"),
                        new BtcNode(null, "devinbtcyk643iruzfpaxw3on2jket7rbjmwygm42dmdyub3ietrbmid.onion", null, BtcNode.DEFAULT_PORT, "@devinbileck"),

                        // jester4042
                        new BtcNode(null, "uxu2kwzqakspsgojbzwazzmrmewwtgdle5uahb6oaonecamezc35vqyd.onion", null, BtcNode.DEFAULT_PORT, "@jester4042"),
                        new BtcNode(null, "l65ab4jecc62mjihifr7m3mx7ljehwoy2iutz2lue4ra5goor3f4xeqd.onion", null, BtcNode.DEFAULT_PORT, "@jester4042"),
                        new BtcNode(null, "oe6lfkjeuqkcvjw62dadzqfsiyd7xpeiznqvy4ugvboks6tcf3bgmjyd.onion", null, BtcNode.DEFAULT_PORT, "@jester4042"),

                        // node_op_324
                        new BtcNode(null, "qs535l32ne43rxr5iqexbhu4r6zifrfez653pm7j3rpi7c7omaz7xcqd.onion", null, BtcNode.DEFAULT_PORT, "@node_op_324"),
                        new BtcNode(null, "oxbj7g4fybthmfm42bnjtelf3zfxnhnpvbixqp3abvnxh4sjatpfdpqd.onion", null, BtcNode.DEFAULT_PORT, "@node_op_324"),
                        new BtcNode(null, "a2vr3buoei2dfnocjl4ilwjwruxqofv6kzagmn7dfdtoilsxlx45flad.onion", null, BtcNode.DEFAULT_PORT, "@node_op_324"),

                        // suddenwhipvapor
                        new BtcNode(null, "2oalsctcn76axnrnaqjddiiu5qhrc7hv3raik2lyfxb7eoktk4vw6sad.onion", null, BtcNode.DEFAULT_PORT, "@suddenwhipvapor"),
                        new BtcNode(null, "ybryiy2k4p4pery4qseap4iu2rxput2akuvpvczwvg4eyfafcdsvyqid.onion", null, BtcNode.DEFAULT_PORT, "@suddenwhipvapor"),

                        // runbtc
                        new BtcNode(null, "runbtcnd22qxdwlmhzsrw6zyfmkivuy5nuqbhasaztekildcxc7lseyd.onion", null, BtcNode.DEFAULT_PORT, "@runbtc")
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
            } else if (parts[0].contains(":") && !parts[0].contains(".")) {
                // IPv6 address only; not delimited by square brackets
                host = parts[0];
            } else if (parts[0].contains(".")) {
                // address and an optional port number
                // e.g. 127.0.0.1:8333 or abcdef123xyz.onion:9999
                parts = fullAddress.split(":");
                checkArgument(parts.length > 0);
                host = parts[0];
                if (parts.length == 2)
                    port = Integer.parseInt(parts[1]);
            }

            checkArgument(host.length() > 0, "BtcNode address format not recognised");
            return host.contains(".onion") ? new BtcNode(null, host, null, port, null) : new BtcNode(null, null, host, port, null);
        }

        public BtcNode(@Nullable String hostName,
                       @Nullable String onionAddress,
                       @Nullable String address,
                       int port,
                       @Nullable String operator) {
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

        public String getId() {
            String address = this.address == null ? "" : this.address + ", ";
            String onionAddress = this.onionAddress == null ? "" : this.onionAddress;
            return operator + ": [" + address + onionAddress + "]";
        }
    }
}
