/*
 * This file is part of bisq.
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

package io.bisq.core.btc;

import io.bisq.core.app.BisqEnvironment;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

// Managed here: https://github.com/bisq-network/roles/issues/39
@Slf4j
public class BitcoinNodes {

    public enum BitcoinNodesOption {
        PROVIDED,
        CUSTOM,
        PUBLIC
    }

    // For other base currencies or testnet we ignore provided nodes
    public List<BtcNode> getProvidedBtcNodes() {
        return useProvidedBtcNodes() ?
                Arrays.asList(
                        // ManfredKarrer
                        new BtcNode("btc1.0-2-1.net", "r3dsojfhwcm7x7p6.onion", "159.89.16.222", BtcNode.DEFAULT_PORT, "@manfredkarrer"),
                        new BtcNode("btc2.0-2-1.net", "vlf5i3grro3wux24.onion", "165.227.34.56", BtcNode.DEFAULT_PORT, "@manfredkarrer"),
                        new BtcNode("btc3.0-2-1.net", "i3a5xtzfm4xwtybd.onion", "165.227.44.202", BtcNode.DEFAULT_PORT, "@manfredkarrer"),

                        // emzy
                        new BtcNode("kirsche.emzy.de", "fz6nsij6jiyuwlsc.onion", "78.47.61.83", BtcNode.DEFAULT_PORT, "@emzy"),
                        new BtcNode("node2.emzy.de", "c6ac4jdfyeiakex2.onion", "62.75.210.81", BtcNode.DEFAULT_PORT, "@emzy"),
                        new BtcNode("node1.emzy.de", "sjyzmwwu6diiit3r.onion", "163.172.171.119", BtcNode.DEFAULT_PORT, "@emzy"),
                        new BtcNode(null, "poyvpdt762gllauu.onion", null, BtcNode.DEFAULT_PORT, "@emzy"), // cannot provide IP because no static IP

                        // ripcurlx
                        new BtcNode("bitcoin.christophatteneder.com", "lgkvbvro67jomosw.onion", "174.138.35.229", BtcNode.DEFAULT_PORT, "@Christoph"),

                        // mrosseel
                        new BtcNode("btc.vante.me", "4jyh6llqj264oggs.onion", "138.68.117.247", BtcNode.DEFAULT_PORT, "@miker"),
                        new BtcNode("btc2.vante.me", "mxdtrjhe2yfsx3pg.onion", "67.207.75.7", BtcNode.DEFAULT_PORT, "@miker"),

                        // sqrrm
                        new BtcNode("btc4.0-2-1.net", "3r44ddzjitznyahw.onion", "185.25.48.184", BtcNode.DEFAULT_PORT, "@sqrrm"),

                        // sgeisler
                        new BtcNode("bcwat.ch", "z33nukt7ngik3cpe.onion", "5.189.166.193", BtcNode.DEFAULT_PORT, "@sgeisler"),

                        // others
                        new BtcNode("btc.jochen-hoenicke.de", null, "88.198.39.205", BtcNode.DEFAULT_PORT, "@jhoenicke"), // requested onion
                        new BtcNode("bitcoin4-fullnode.csg.uzh.ch", null, "192.41.136.217", BtcNode.DEFAULT_PORT, "@tbocek") // requested onion
                ) :
                new ArrayList<>();
    }

    public boolean useProvidedBtcNodes() {
        return BisqEnvironment.getBaseCurrencyNetwork().isBitcoin() && BisqEnvironment.getBaseCurrencyNetwork().isMainnet();
    }

    @EqualsAndHashCode
    @Getter
    public static class BtcNode {
        private static final int DEFAULT_PORT = BisqEnvironment.getParameters().getPort(); //8333

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
            String[] parts = fullAddress.split(":");
            checkArgument(parts.length > 0);
            final String host = parts[0];
            int port = DEFAULT_PORT;
            if (parts.length == 2)
                port = Integer.valueOf(parts[1]);

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
