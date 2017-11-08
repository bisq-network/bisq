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

    @Getter
    private final List<BtcNode> btcNodeList = Arrays.asList(
            BtcNode.fromAddress(/*"bitcoin.christophatteneder.com", */"174.138.35.229"),
            BtcNode.fromHostName("btc.beams.io"/*, "62.178.187.80"*/),
            BtcNode.fromHostNameAndAddress("kirsche.emzy.de", "78.47.61.83"),
            BtcNode.fromHostName("r3dsojfhwcm7x7p6.onion"),
            BtcNode.fromHostName("vlf5i3grro3wux24.onion")
    );

    @Getter
    @ToString
    public static class BtcNode {
        @Nullable
        private final String hostName;
        @Nullable
        private final String address; // IPv4 address
        private int port = BisqEnvironment.getParameters().getPort();

        /**
         * @param fullAddress [hostName:port | IPv4 address:port]
         * @return
         */
        public static BtcNode fromFullAddress(String fullAddress) {
            String[] parts = fullAddress.split(":");
            checkArgument(parts.length > 0);
            if (parts.length == 1) {
                return BtcNode.fromHostName(parts[0]);
            } else {
                checkArgument(parts.length == 2);
                return BtcNode.fromHostNameAndPort(parts[0], Integer.valueOf(parts[1]));
            }
        }

        public static BtcNode fromHostName(String hostName) {
            return new BtcNode(hostName, null);
        }

        public static BtcNode fromAddress(String address) {
            return new BtcNode(null, address);
        }

        public static BtcNode fromHostNameAndPort(String hostName, int port) {
            return new BtcNode(hostName, null, port);
        }

        public static BtcNode fromHostNameAndAddress(String hostName, String address) {
            return new BtcNode(hostName, address);
        }

        private BtcNode(@Nullable String hostName, @Nullable String address, int port) {
            this.hostName = hostName;
            this.address = address;
            this.port = port;

            if (address == null)
                checkNotNull(hostName, "hostName must not be null if address is null");
            else if (hostName == null)
                checkNotNull(address, "address must not be null if hostName is null");
        }

        private BtcNode(@Nullable String hostName, @Nullable String address) {
            this.hostName = hostName;
            this.address = address;

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
