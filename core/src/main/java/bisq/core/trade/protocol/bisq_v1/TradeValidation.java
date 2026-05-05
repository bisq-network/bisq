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

package bisq.core.trade.protocol.bisq_v1;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradePeerTxInputValidator;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class TradeValidation {

    public static Coin checkTradeAmount(Coin tradeAmount, Coin offerMinAmount, Coin offerMaxAmount) {
        checkArgument(!tradeAmount.isLessThan(offerMinAmount),
                "Trade amount must not be less than minimum offer amount. tradeAmount=%s, offerMinAmount=%s",
                tradeAmount.toFriendlyString(), offerMinAmount.toFriendlyString());
        checkArgument(!tradeAmount.isGreaterThan(offerMaxAmount),
                "Trade amount must not be higher than maximum offer amount. tradeAmount=%s, offerMaxAmount=%s",
                tradeAmount.toFriendlyString(), offerMaxAmount.toFriendlyString());
        return tradeAmount;
    }

    public static byte[] checkMultiSigPubKey(byte[] multiSigPubKey) {
        checkNotNull(multiSigPubKey, "multiSigPubKey must not be null");
        checkArgument(multiSigPubKey.length == 33, "multiSigPubKey must be compressed");

        // Check that the taker multisig key decompresses to a valid curve point:
        ECKey.fromPublicOnly(multiSigPubKey);
        return multiSigPubKey;
    }

    public static List<RawTransactionInput> checkTakersRawTransactionInputs(List<RawTransactionInput> takerRawTransactionInputs,
                                                                            BtcWalletService btcWalletService,
                                                                            Trade trade,
                                                                            Coin tradeAmount
    ) {
        checkNotNull(takerRawTransactionInputs);
        Offer offer = trade.getOffer();

        // Taker pays the miner fee for deposit tx and payout tx
        Coin takersDoubleMinerFee = trade.getTradeTxFee().multiply(2);
        Coin expectedTakersInputAmount;
        if (offer.isBuyOffer()) {
            // Taker is the seller.
            expectedTakersInputAmount = offer.getSellerSecurityDeposit()
                    .add(tradeAmount)
                    .add(takersDoubleMinerFee);
        } else {
            // Taker is buyer
            expectedTakersInputAmount = offer.getBuyerSecurityDeposit()
                    .add(takersDoubleMinerFee);
        }

        TradePeerTxInputValidator.validatePeersInputs(takerRawTransactionInputs,
                expectedTakersInputAmount,
                btcWalletService,
                "Taker");
        return takerRawTransactionInputs;
    }


    public static List<RawTransactionInput> checkMakersRawTransactionInputs(List<RawTransactionInput> makerRawTransactionInputs,
                                                                            BtcWalletService btcWalletService,
                                                                            Offer offer) {
        checkNotNull(makerRawTransactionInputs);

        Coin expectedMakersInputAmount;
        if (offer.isBuyOffer()) {
            // maker is the buyer.
            expectedMakersInputAmount = offer.getBuyerSecurityDeposit();
        } else {
            // maker is seller
            // We use the offer amount not the trade amount as we compare with the inputs which come from the
            // makers fee tx which has the reserved funds for the max. offer amount.
            expectedMakersInputAmount = offer.getSellerSecurityDeposit()
                    .add(offer.getAmount());
        }

        TradePeerTxInputValidator.validatePeersInputs(makerRawTransactionInputs,
                expectedMakersInputAmount,
                btcWalletService,
                "Maker");
        return makerRawTransactionInputs;
    }

}
