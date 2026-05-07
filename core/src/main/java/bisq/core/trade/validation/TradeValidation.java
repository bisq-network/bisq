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

import bisq.core.trade.protocol.TradeMessage;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Sig;
import bisq.common.util.Base64;

import java.security.PublicKey;

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.util.Validator.checkNonBlankString;
import static bisq.core.util.Validator.checkNonEmptyBytes;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public final class TradeValidation {
    // We have to account for clock drift. We use mostly 2 hours as max drift, but we prefer to be more tolerant here
    public static final long MAX_DATE_DEVIATION = TimeUnit.HOURS.toMillis(4);
    public static final int MAX_LOCKTIME_BLOCK_DEVIATION = 3; // Peers latest block height must inside a +/- 3 blocks tolerance to ours.
    public static final double MAX_TRADE_PRICE_DEVIATION = 1.5;
    public static final double MAX_FEE_DEVIATION_FACTOR = 2; // Max change by factor 2 (expected / 2 or expected * 2)
    public static final double MAX_MAKER_FEE_DEVIATION_FACTOR = 2; // Max change by factor 2 (expected / 2 or expected * 2)
    public static final double MAX_TAKER_FEE_DEVIATION_FACTOR = 1.5; // Max change by factor 1.5 (expected / 1.5 or expected * 1.5)

    private TradeValidation() {
    }

    public static String checkTradeId(String tradeId, TradeMessage tradeMessage) {
        checkArgument(isTradeIdValid(tradeId, tradeMessage), "TradeId %s is not valid", tradeId);
        return tradeId;
    }

    public static boolean isTradeIdValid(String tradeId, TradeMessage tradeMessage) {
        checkNonBlankString(tradeId, "tradeId");
        checkNotNull(tradeMessage, "tradeMessage must not be null");
        return tradeId.equals(tradeMessage.getTradeId());
    }

    public static long checkPeersDate(long currentDate) {
        long now = System.currentTimeMillis();
        checkArgument(Math.abs(now - currentDate) <= MAX_DATE_DEVIATION, "currentDate is outside of allowed range.");
        return currentDate;
    }

    public static String checkBase64DSASignature(String dsaSignatureBase64) {
        checkNonBlankString(dsaSignatureBase64, "dsaSignatureBase64");
        fromBase64DSASignature(dsaSignatureBase64);
        return dsaSignatureBase64;
    }

    public static byte[] fromBase64DSASignature(String dsaSignatureBase64) {
        checkNonBlankString(dsaSignatureBase64, "dsaSignatureBase64");
        return Base64.decode(dsaSignatureBase64);
    }

    public static byte[] checkDSASignature(byte[] dsaSignature,
                                           byte[] message,
                                           PublicKey signaturePubKey) {
        checkNonEmptyBytes(dsaSignature, "dsaSignature");
        checkNonEmptyBytes(message, "message");
        checkNotNull(signaturePubKey, "signaturePubKey must not be null");

        try {
            checkArgument(Sig.verify(signaturePubKey, message, dsaSignature), "Invalid dsaSignature");
            return dsaSignature;
        } catch (CryptoException e) {
            throw new IllegalArgumentException("Invalid dsaSignature", e);
        }
    }
}
