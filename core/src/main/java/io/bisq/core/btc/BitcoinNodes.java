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
import lombok.Getter;
import lombok.ToString;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

// Managed here: https://github.com/bisq-network/roles/issues/39
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
                        BtcNode.fromHostNameAndAddress("bitcoin.christophatteneder.com", "174.138.35.229", "https://github.com/ripcurlx"),
                        BtcNode.fromHostNameAndAddress("kirsche.emzy.de", "78.47.61.83", "https://github.com/emzy"),
                        BtcNode.fromHostName("poyvpdt762gllauu.onion", "https://github.com/emzy"),
                        BtcNode.fromHostName("3r44ddzjitznyahw.onion", "https://github.com/sqrrm"),
                        BtcNode.fromHostName("i3a5xtzfm4xwtybd.onion", "https://github.com/sqrrm"),
                        BtcNode.fromHostName("r3dsojfhwcm7x7p6.onion", "https://github.com/alexej996"),
                        BtcNode.fromHostName("vlf5i3grro3wux24.onion", "https://github.com/alexej996"),
                        BtcNode.fromHostNameAndAddress("bcwat.ch", "5.189.166.193", "https://github.com/sgeisler"),
                        BtcNode.fromHostNameAndAddress("btc.jochen-hoenicke.de", "37.221.198.57", "https://github.com/jhoenicke"),
                        BtcNode.fromHostNameAndAddress("btc.vante.me", "138.68.117.247", "https://github.com/mrosseel"),
                        BtcNode.fromHostName("mxdtrjhe2yfsx3pg.onion", "https://github.com/mrosseel"),
                        BtcNode.fromHostName("7sl6havdhtgefwo2.onion", "https://github.com/themighty1"),
                        BtcNode.fromHostNameAndAddress("bitcoin4-fullnode.csg.uzh.ch", "192.41.136.217", "https://github.com/tbocek")
                ) :
                new ArrayList<>();
    }

    public boolean useProvidedBtcNodes() {
        return BisqEnvironment.getBaseCurrencyNetwork().isBitcoin() && BisqEnvironment.getBaseCurrencyNetwork().isMainnet();
    }

    @Getter
    @ToString
    public static class BtcNode {
        @Nullable
        private final String hostName;
        @Nullable
        private final String operator; // null in case the user provides a list of custom btc nodes
        @Nullable
        private final String address; // IPv4 address
        private int port = BisqEnvironment.getParameters().getPort();

        /**
         * @param fullAddress [hostName:port | IPv4 address:port]
         * @return BtcNode instance
         */
        public static BtcNode fromFullAddress(String fullAddress) {
            String[] parts = fullAddress.split(":");
            checkArgument(parts.length > 0);
            if (parts.length == 1) {
                return BtcNode.fromHostName(parts[0], null);
            } else {
                checkArgument(parts.length == 2);
                return BtcNode.fromHostNameAndPort(parts[0], Integer.valueOf(parts[1]), null);
            }
        }

        public static BtcNode fromHostName(String hostName, @Nullable String operator) {
            return new BtcNode(hostName, null, operator);
        }

        public static BtcNode fromAddress(String address, @Nullable String operator) {
            return new BtcNode(null, address, operator);
        }

        public static BtcNode fromHostNameAndPort(String hostName, int port, @Nullable String operator) {
            return new BtcNode(hostName, null, port, operator);
        }

        public static BtcNode fromHostNameAndAddress(String hostName, String address, @Nullable String operator) {
            return new BtcNode(hostName, address, operator);
        }

        private BtcNode(@Nullable String hostName, @Nullable String address, int port, @Nullable String operator) {
            this.hostName = hostName;
            this.address = address;
            this.port = port;
            this.operator = operator;

            if (address == null)
                checkNotNull(hostName, "hostName must not be null if address is null");
            else if (hostName == null)
                checkNotNull(address, "address must not be null if hostName is null");
        }

        private BtcNode(@Nullable String hostName, @Nullable String address, @Nullable String operator) {
            this.hostName = hostName;
            this.address = address;
            this.operator = operator;

            if (address == null)
                checkNotNull(hostName, "hostName must not be null if address is null");
            else if (hostName == null)
                checkNotNull(address, "address must not be null if hostName is null");
        }

        public boolean isHiddenService() {
            return hostName != null && hostName.endsWith("onion");
        }

        public String getHostAddressOrHostName() {
            if (address != null)
                return address;
            else
                return hostName;
        }
    }
}
