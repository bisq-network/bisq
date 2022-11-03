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

package bisq.core.support.dispute;

import bisq.core.util.validation.RegexValidatorFactory;

import bisq.network.p2p.NodeAddress;

import bisq.common.config.Config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DisputeValidation {

    public static void validateNodeAddress(Dispute dispute, NodeAddress nodeAddress, Config config)
            throws NodeAddressException {
        if (!config.useLocalhostForP2P && !RegexValidatorFactory.onionAddressRegexValidator().validate(nodeAddress.getFullAddress()).isValid) {
            String msg = "Node address " + nodeAddress.getFullAddress() + " at dispute with trade ID " +
                    dispute.getShortTradeId() + " is not a valid address";
            log.error(msg);
            throw new NodeAddressException(dispute, msg);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Exceptions
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static class ValidationException extends Exception {
        @Getter
        private final Dispute dispute;

        public ValidationException(Dispute dispute, String msg) {
            super(msg);
            this.dispute = dispute;
        }
    }

    public static class NodeAddressException extends ValidationException {
        public NodeAddressException(Dispute dispute, String msg) {
            super(dispute, msg);
        }
    }

    public static class AddressException extends ValidationException {
        public AddressException(Dispute dispute, String msg) {
            super(dispute, msg);
        }
    }

    public static class DisputeReplayException extends ValidationException {
        public DisputeReplayException(Dispute dispute, String msg) {
            super(dispute, msg);
        }
    }
}
