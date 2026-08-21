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

package bisq.core.trade.protocol;

import bisq.network.p2p.SendersNodeAddressProvidingPayload;
import bisq.network.p2p.UidMessage;

import bisq.common.proto.network.NetworkEnvelope;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import static bisq.core.util.Validator.checkNonBlankString;

/**
 * Base class for trade-related messages exchanged between peers.
 * As the TradeMessage is wrapped into a PrefixedSealedAndSignedMessage we use the SendersNodeAddressProvidingPayload
 * to allow network level validation that the senders node address matches the address from the outer
 * SendersNodeAddressAwareEnvelope.
 */
@EqualsAndHashCode(callSuper = true)
@Getter
@ToString
public abstract class TradeMessage extends NetworkEnvelope implements UidMessage, SendersNodeAddressProvidingPayload {
    protected final String tradeId;
    protected final String uid;

    protected TradeMessage(int messageVersion, String tradeId, String uid) {
        super(messageVersion);
        this.tradeId = checkNonBlankString(tradeId, "tradeId");
        this.uid = checkNonBlankString(uid, "uid");
    }
}
