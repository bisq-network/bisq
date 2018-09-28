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

package bisq.core.offer;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.provider.fee.FeeService;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Util class for getting  a fee estimation.
 */
@Slf4j
public class TxFeeEstimation {
    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;
    private final Preferences preferences;
    private final User user;
    private final TradeWalletService tradeWalletService;
    private final FeeService feeService;

    private int feeTxSize = 260; // size of typical tx with 1 input
    private int feeTxSizeEstimationRecursionCounter;
    private String offerId;
    private OfferPayload.Direction direction;
    private Coin amount;
    private Coin buyerSecurityDeposit;
    private double marketPriceMargin;
    private boolean marketPriceAvailable;

    public TxFeeEstimation(BtcWalletService btcWalletService,
                           BsqWalletService bsqWalletService,
                           Preferences preferences,
                           User user,
                           TradeWalletService tradeWalletService,
                           FeeService feeService,
                           String offerId,
                           OfferPayload.Direction direction,
                           Coin amount,
                           Coin buyerSecurityDeposit,
                           double marketPriceMargin,
                           boolean marketPriceAvailable,
                           int feeTxSize) {


        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.preferences = preferences;
        this.user = user;
        this.tradeWalletService = tradeWalletService;
        this.feeService = feeService;

        this.offerId = offerId;
        this.direction = direction;
        this.amount = amount;
        this.buyerSecurityDeposit = buyerSecurityDeposit;
        this.marketPriceMargin = marketPriceMargin;
        this.marketPriceAvailable = marketPriceAvailable;
        this.feeTxSize = feeTxSize;
    }

    public Coin getEstimatedFee() {
        Coin sellerSecurityDeposit = Restrictions.getSellerSecurityDeposit();
        Coin txFeeFromFeeService = feeService.getTxFee(feeTxSize);
        Address fundingAddress = btcWalletService.getFreshAddressEntry().getAddress();
        Address reservedForTradeAddress = btcWalletService.getOrCreateAddressEntry(offerId, AddressEntry.Context.RESERVED_FOR_TRADE).getAddress();
        Address changeAddress = btcWalletService.getFreshAddressEntry().getAddress();

        Coin reservedFundsForOffer = OfferUtil.isBuyOffer(direction) ? buyerSecurityDeposit : sellerSecurityDeposit;
        if (!OfferUtil.isBuyOffer(direction))
            reservedFundsForOffer = reservedFundsForOffer.add(amount);

        checkNotNull(user.getAcceptedArbitrators(), "user.getAcceptedArbitrators() must not be null");
        checkArgument(!user.getAcceptedArbitrators().isEmpty(), "user.getAcceptedArbitrators() must not be empty");
        String dummyArbitratorAddress = user.getAcceptedArbitrators().get(0).getBtcAddress();
        try {
            log.info("We create a dummy tx to see if our estimated size is in the accepted range. feeTxSize={}," +
                            " txFee based on feeTxSize: {}, recommended txFee is {} sat/byte",
                    feeTxSize, txFeeFromFeeService.toFriendlyString(), feeService.getTxFeePerByte());
            Transaction tradeFeeTx = tradeWalletService.estimateBtcTradingFeeTxSize(
                    fundingAddress,
                    reservedForTradeAddress,
                    changeAddress,
                    reservedFundsForOffer,
                    true,
                    OfferUtil.getMakerFee(bsqWalletService, preferences, amount, marketPriceAvailable, marketPriceMargin),
                    txFeeFromFeeService,
                    dummyArbitratorAddress);

            final int txSize = tradeFeeTx.bitcoinSerialize().length;
            // use feeTxSizeEstimationRecursionCounter to avoid risk for endless loop
            if (txSize > feeTxSize * 1.2 && feeTxSizeEstimationRecursionCounter < 10) {
                feeTxSizeEstimationRecursionCounter++;
                log.info("txSize is {} bytes but feeTxSize used for txFee calculation was {} bytes. We try again with an " +
                        "adjusted txFee to reach the target tx fee.", txSize, feeTxSize);
                feeTxSize = txSize;
                txFeeFromFeeService = feeService.getTxFee(feeTxSize);
                // lets try again with the adjusted txSize and fee.
                getEstimatedFee();
            } else {
                log.info("feeTxSize {} bytes", feeTxSize);
                log.info("txFee based on estimated size: {}, recommended txFee is {} sat/byte",
                        txFeeFromFeeService.toFriendlyString(), feeService.getTxFeePerByte());
            }
        } catch (InsufficientMoneyException e) {
            // If we need to fund from an external wallet we can assume we only have 1 input (260 bytes).
            log.warn("We cannot do the fee estimation because there are not enough funds in the wallet. This is expected " +
                    "if the user pays from an external wallet. In that case we use an estimated tx size of 260 bytes.");
            feeTxSize = 260;
            txFeeFromFeeService = feeService.getTxFee(feeTxSize);
            log.info("feeTxSize {} bytes", feeTxSize);
            log.info("txFee based on estimated size: {}, recommended txFee is {} sat/byte",
                    txFeeFromFeeService.toFriendlyString(), feeService.getTxFeePerByte());
        }

        return txFeeFromFeeService;
    }
}

