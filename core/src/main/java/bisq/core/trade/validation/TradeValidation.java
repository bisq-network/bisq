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

package bisq.core.trade.validation;

import bisq.core.support.dispute.mediation.mediator.Mediator;
import bisq.core.support.dispute.refund.refundagent.RefundAgent;
import bisq.core.support.dispute.refund.refundagent.RefundAgentManager;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.PubKeyRing;
import bisq.common.util.Hex;
import bisq.common.util.Utilities;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static bisq.core.util.Validator.checkNonBlankString;
import static bisq.core.util.Validator.checkNonEmptyBytes;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class TradeValidation {
    // We have to account for clock drift. We use mostly 2 hours as max drift, but we prefer to be more tolerant here
    public static final long MAX_DATE_DEVIATION = TimeUnit.HOURS.toMillis(4);

    private TradeValidation() {
    }


    /* --------------------------------------------------------------------- */
    // Trade identity
    /* --------------------------------------------------------------------- */

    public static String checkTradeId(String tradeId, TradeMessage tradeMessage) {
        checkNotNull(tradeMessage, "tradeMessage must not be null");
        checkNonBlankString(tradeId, "tradeId");
        String tradeIdFromMessage = checkNonBlankString(tradeMessage.getTradeId(), "tradeMessage.tradeId");
        checkArgument(tradeId.equals(tradeIdFromMessage), "TradeId %s is not matching " +
                "tradeId from message %s", tradeId, tradeIdFromMessage);
        return tradeId;
    }

    public static boolean isTradeIdValid(String tradeId, TradeMessage tradeMessage) {
        return isTradeIdValid(tradeMessage, tradeId);
    }

    public static boolean isTradeIdValid(TradeMessage tradeMessage, String expectedTradeId) {
        try {
            checkNotNull(tradeMessage, "tradeMessage must not be null");
            checkNonBlankString(expectedTradeId, "expectedTradeId");
            String tradeId = checkNonBlankString(tradeMessage.getTradeId(), "tradeMessage.tradeId");
            return tradeId.equals(expectedTradeId);
        } catch (RuntimeException e) {
            return false;
        }
    }


    /* --------------------------------------------------------------------- */
    // Peer date
    /* --------------------------------------------------------------------- */

    public static long checkPeersDate(long currentDate) {
        long now = System.currentTimeMillis();
        checkArgument(Math.abs(now - currentDate) <= MAX_DATE_DEVIATION, "currentDate is outside of allowed range.");
        return currentDate;
    }


    /* --------------------------------------------------------------------- */
    // Contract hash
    /* --------------------------------------------------------------------- */

    public static byte[] checkHashFromContract(byte[] current, byte[] expected, String label) {
        checkNonEmptyBytes(current, label + " (current)");
        checkNonEmptyBytes(expected, label + " (expected)");
        checkArgument(Arrays.equals(current, expected),
                "%s mismatch. current=%s, expected=%s",
                label,
                Utilities.toTruncatedString(Hex.encode(current), 8),
                Utilities.toTruncatedString(Hex.encode(expected), 8));
        return current;
    }


    /* --------------------------------------------------------------------- */
    // Mediator
    /* --------------------------------------------------------------------- */

    public static PubKeyRing getCheckedMediatorPubKeyRing(NodeAddress mediatorNodeAddress, User user) {
        checkNotNull(mediatorNodeAddress, "mediatorNodeAddress must not be null");
        checkNotNull(user, "user must not be null");
        Mediator mediator = checkNotNull(user.getAcceptedMediatorByAddress(mediatorNodeAddress),
                "user.getAcceptedMediatorByAddress(mediatorNodeAddress) must not be null");
        return checkNotNull(mediator.getPubKeyRing(),
                "mediator.getPubKeyRing() must not be null");
    }

    public static PubKeyRing getCheckedRefundAgentPubKeyRing(NodeAddress refundAgentNodeAddress,
                                                             RefundAgentManager refundAgentManager) {
        checkNotNull(refundAgentNodeAddress, "refundAgentNodeAddress must not be null");
        checkNotNull(refundAgentManager, "refundAgentManager must not be null");
        RefundAgent refundAgent = checkNotNull(refundAgentManager.getDisputeAgentByNodeAddress(refundAgentNodeAddress)
                        .orElse(null),
                "refundAgentManager.getDisputeAgentByNodeAddress(refundAgentNodeAddress) must not be null");
        return checkNotNull(refundAgent.getPubKeyRing(),
                "refundAgent.getPubKeyRing() must not be null");
    }


    /* --------------------------------------------------------------------- */
    // Tolerance
    /* --------------------------------------------------------------------- */

    static long checkValueInTolerance(long actualValue, long expectedValue, double factor) {
        checkArgument(expectedValue > 0, "expectedValue must be > 0");
        checkArgument(factor >= 1.0, "factor must be >= 1");

        double min = expectedValue / factor;
        double max = expectedValue * factor;

        checkArgument(actualValue >= min && actualValue <= max,
                "actualValue is outside of allowed tolerance. " +
                        "actualValue=%s, expectedValue=%s, min=%s, max=%s, factor=%s",
                actualValue,
                expectedValue,
                min,
                max,
                factor);

        return actualValue;
    }
}
