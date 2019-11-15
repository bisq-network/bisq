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

import bisq.core.btc.TxFeeEstimationService;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.monetary.Price;
import bisq.core.payment.HalCashAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.util.CoinUtil;

import bisq.common.app.Version;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class CreateOfferService {
    private final TxFeeEstimationService txFeeEstimationService;
    private final MakerFeeProvider makerFeeProvider;
    private final BsqWalletService bsqWalletService;
    private final Preferences preferences;
    private final PriceFeedService priceFeedService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public CreateOfferService(TxFeeEstimationService txFeeEstimationService,
                              MakerFeeProvider makerFeeProvider,
                              BsqWalletService bsqWalletService,
                              Preferences preferences,
                              PriceFeedService priceFeedService) {
        this.txFeeEstimationService = txFeeEstimationService;
        this.makerFeeProvider = makerFeeProvider;
        this.bsqWalletService = bsqWalletService;
        this.preferences = preferences;
        this.priceFeedService = priceFeedService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getRandomOfferId() {
        return Utilities.getRandomPrefix(5, 8) + "-" +
                UUID.randomUUID().toString() + "-" +
                Version.VERSION.replace(".", "");
    }

    public double getSellerSecurityDeposit() {
        return Restrictions.getSellerSecurityDepositAsPercent();
    }

    public Tuple2<Coin, Integer> getEstimatedFeeAndTxSize(Coin amount,
                                                          OfferPayload.Direction direction,
                                                          double buyerSecurityDeposit,
                                                          double sellerSecurityDeposit) {
        Coin reservedFundsForOffer = getReservedFundsForOffer(direction, amount, buyerSecurityDeposit, sellerSecurityDeposit);
        return txFeeEstimationService.getEstimatedFeeAndTxSizeForMaker(reservedFundsForOffer, getMakerFee(amount));
    }

    public Coin getReservedFundsForOffer(OfferPayload.Direction direction,
                                         Coin amount,
                                         double buyerSecurityDeposit,
                                         double sellerSecurityDeposit) {

        Coin reservedFundsForOffer = getSecurityDeposit(direction,
                amount,
                buyerSecurityDeposit,
                sellerSecurityDeposit);
        if (!isBuyOffer(direction))
            reservedFundsForOffer = reservedFundsForOffer.add(amount);

        return reservedFundsForOffer;
    }

    public Coin getSecurityDeposit(OfferPayload.Direction direction,
                                   Coin amount,
                                   double buyerSecurityDeposit,
                                   double sellerSecurityDeposit) {
        return isBuyOffer(direction) ?
                getBuyerSecurityDepositAsCoin(amount, buyerSecurityDeposit) :
                getSellerSecurityDepositAsCoin(amount, sellerSecurityDeposit);
    }

    public Coin getMakerFee(Coin amount) {
        return makerFeeProvider.getMakerFee(bsqWalletService, preferences, amount);
    }


    public long getPriceAsLong(Price price,
                               PaymentAccount paymentAccount,
                               boolean useMarketBasedPrice,
                               String currencyCode) {
        boolean useMarketBasedPriceValue = isUseMarketBasedPriceValue(useMarketBasedPrice, currencyCode, paymentAccount);
        return price != null && !useMarketBasedPriceValue ? price.getValue() : 0L;
    }

    public boolean isUseMarketBasedPriceValue(boolean useMarketBasedPrice,
                                              String currencyCode,
                                              PaymentAccount paymentAccount) {
        return useMarketBasedPrice &&
                isMarketPriceAvailable(currencyCode) &&
                !isHalCashAccount(paymentAccount);
    }


    private boolean isHalCashAccount(PaymentAccount paymentAccount) {
        return paymentAccount instanceof HalCashAccount;
    }

    private boolean isMarketPriceAvailable(String currencyCode) {
        MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
        return marketPrice != null && marketPrice.isExternallyProvidedPrice();
    }

    public double marketPriceMarginParam(boolean useMarketBasedPriceValue, double marketPriceMargin) {
        return useMarketBasedPriceValue ? marketPriceMargin : 0;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isBuyOffer(OfferPayload.Direction direction) {
        return OfferUtil.isBuyOffer(direction);
    }

    public Coin getBuyerSecurityDepositAsCoin(Coin amount, double buyerSecurityDeposit) {
        Coin percentOfAmountAsCoin = CoinUtil.getPercentOfAmountAsCoin(buyerSecurityDeposit, amount);
        return getBoundedBuyerSecurityDepositAsCoin(percentOfAmountAsCoin);
    }

    public Coin getSellerSecurityDepositAsCoin(Coin amount, double sellerSecurityDeposit) {
        Coin amountAsCoin = amount;
        if (amountAsCoin == null)
            amountAsCoin = Coin.ZERO;

        Coin percentOfAmountAsCoin = CoinUtil.getPercentOfAmountAsCoin(sellerSecurityDeposit, amountAsCoin);
        return getBoundedSellerSecurityDepositAsCoin(percentOfAmountAsCoin);
    }


    private Coin getBoundedBuyerSecurityDepositAsCoin(Coin value) {
        // We need to ensure that for small amount values we don't get a too low BTC amount. We limit it with using the
        // MinBuyerSecurityDepositAsCoin from Restrictions.
        return Coin.valueOf(Math.max(Restrictions.getMinBuyerSecurityDepositAsCoin().value, value.value));
    }

    private Coin getBoundedSellerSecurityDepositAsCoin(Coin value) {
        // We need to ensure that for small amount values we don't get a too low BTC amount. We limit it with using the
        // MinSellerSecurityDepositAsCoin from Restrictions.
        return Coin.valueOf(Math.max(Restrictions.getMinSellerSecurityDepositAsCoin().value, value.value));
    }

}
