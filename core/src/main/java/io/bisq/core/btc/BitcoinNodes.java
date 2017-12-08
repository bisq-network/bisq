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
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

// Managed here: https://github.com/bisq-network/roles/issues/39
@Slf4j
public class BitcoinNodes {

    private final List<String> bannedNodes;

    public enum BitcoinNodesOption {
        PROVIDED,
        CUSTOM,
        PUBLIC
    }

    @Inject
    public BitcoinNodes(BisqEnvironment bisqEnvironment) {
        bannedNodes = bisqEnvironment.getBannedBtcNodes();
    }

    // For other base currencies or testnet we ignore provided nodes
    public Set<BtcNode> getProvidedBtcNodes() {
        Set<BtcNode> btcNodes = useProvidedBtcNodes() ?
                new HashSet<>(Arrays.asList(
                        BtcNode.fromHostNameAddressAndPort("kirsche.emzy.de", "78.47.61.83", BtcNode.DEFAULT_PORT, "https://github.com/emzy"),
                        BtcNode.fromHostName("poyvpdt762gllauu.onion", BtcNode.DEFAULT_PORT, "https://github.com/emzy"),
                        BtcNode.fromHostNameAndAddress("bitcoin.christophatteneder.com", "174.138.35.229", "https://github.com/ripcurlx"),
                        BtcNode.fromHostName("r3dsojfhwcm7x7p6.onion", BtcNode.DEFAULT_PORT, "https://github.com/emzy"),
                        BtcNode.fromHostName("vlf5i3grro3wux24.onion", BtcNode.DEFAULT_PORT, "https://github.com/alexej996"),
                        BtcNode.fromHostName("3r44ddzjitznyahw.onion", BtcNode.DEFAULT_PORT, "https://github.com/sqrrm"),
                        BtcNode.fromHostName("i3a5xtzfm4xwtybd.onion", BtcNode.DEFAULT_PORT, "https://github.com/sqrrm"),
                        BtcNode.fromHostNameAddressAndPort("bitcoin4-fullnode.csg.uzh.ch", "192.41.136.217", BtcNode.DEFAULT_PORT, "https://github.com/tbocek"),
                        BtcNode.fromHostNameAddressAndPort("bcwat.ch", "5.189.166.193", BtcNode.DEFAULT_PORT, "https://github.com/sgeisler"),
                        BtcNode.fromHostNameAddressAndPort("btc.jochen-hoenicke.de", "37.221.198.57", BtcNode.DEFAULT_PORT, "https://github.com/jhoenicke"),  //
                        BtcNode.fromHostNameAddressAndPort("btc.vante.me", "138.68.117.247", BtcNode.DEFAULT_PORT, "https://github.com/mrosseel"),
                        BtcNode.fromHostName("mxdtrjhe2yfsx3pg.onion", BtcNode.DEFAULT_PORT, "https://github.com/mrosseel"),
                        BtcNode.fromAddressAndPort("62.75.210.81", BtcNode.DEFAULT_PORT, "https://github.com/emzy"),
                        BtcNode.fromAddressAndPort("163.172.171.119", BtcNode.DEFAULT_PORT, "https://github.com/emzy")
                )) :
                new HashSet<>();

        btcNodes = btcNodes.stream()
                .filter(e -> bannedNodes == null || !bannedNodes.contains(e.getAddressOrHostWithPort()))
                .collect(Collectors.toSet());

        if (bannedNodes == null)
            log.info("btcNodes={}", btcNodes);
        else
            log.warn("We received banned btc nodes={}, btcNodes={}", bannedNodes, btcNodes);

        return btcNodes;
    }

    public boolean useProvidedBtcNodes() {
        return BisqEnvironment.getBaseCurrencyNetwork().isBitcoin() && BisqEnvironment.getBaseCurrencyNetwork().isMainnet();
    }

    @Getter
    @ToString
    public static class BtcNode {
        private static final int DEFAULT_PORT = BisqEnvironment.getParameters().getPort(); //8333

        @Nullable
        private final String hostName;
        @Nullable
        private final String operator; // null in case the user provides a list of custom btc nodes
        @Nullable
        private final String address; // IPv4 address
        private int port = DEFAULT_PORT;

        /**
         * @param fullAddress [hostName:port | IPv4 address:port]
         * @return BtcNode instance
         */
        public static BtcNode fromFullAddress(String fullAddress) {
            String[] parts = fullAddress.split(":");
            checkArgument(parts.length > 0);
            if (parts.length == 1) {
                return BtcNode.fromHostName(parts[0], DEFAULT_PORT, null);
            } else {
                checkArgument(parts.length == 2);
                return BtcNode.fromHostNameAndPort(parts[0], Integer.valueOf(parts[1]), null);
            }
        }

        public static BtcNode fromHostName(String hostName, int port, @Nullable String operator) {
            return new BtcNode(hostName, null, port, operator);
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

        public static BtcNode fromHostNameAddressAndPort(String hostName, String address, int port, @Nullable String operator) {
            return new BtcNode(hostName, address, port, operator);
        }

        public static BtcNode fromAddressAndPort(String address, int port, @Nullable String operator) {
            return new BtcNode(null, address, port, operator);
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

        public String getAddressOrHostWithPort() {
            log.error(getHostAddressOrHostName() + ":" + port);
            return getHostAddressOrHostName() + ":" + port;
        }
    }
}
