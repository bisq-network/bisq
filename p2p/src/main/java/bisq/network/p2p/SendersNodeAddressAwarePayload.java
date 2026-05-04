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

package bisq.network.p2p;

import bisq.common.proto.network.NetworkEnvelope;

import javax.annotation.Nullable;

/**
 * Interface for payloads that include the sender's node address.
 */
public interface SendersNodeAddressAwarePayload {
    NodeAddress getSenderNodeAddress();

    static boolean hasSenderNodeAddress(@Nullable NetworkEnvelope networkEnvelope) {
        return networkEnvelope instanceof SendersNodeAddressAwarePayload;
    }

    @Nullable
    static NodeAddress getSenderNodeAddress(@Nullable NetworkEnvelope networkEnvelope) {
        return hasSenderNodeAddress(networkEnvelope) ?
                ((SendersNodeAddressAwarePayload) networkEnvelope).getSenderNodeAddress() :
                null;
    }

    static boolean isSenderNodeAddressMatching(@Nullable NetworkEnvelope networkEnvelope,
                                               @Nullable NodeAddress expectedSenderNodeAddress) {
        if (!hasSenderNodeAddress(networkEnvelope)) {
            return true;
        }

        NodeAddress payloadSenderNodeAddress = getSenderNodeAddress(networkEnvelope);
        return payloadSenderNodeAddress != null && payloadSenderNodeAddress.equals(expectedSenderNodeAddress);
    }
}
