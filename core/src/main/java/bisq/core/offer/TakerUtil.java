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

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.provider.fee.FeeService;
import bisq.core.user.Preferences;
import bisq.core.util.CoinUtil;

import org.bitcoinj.core.Coin;

import javax.annotation.Nullable;

public class TakerUtil {
    public static Coin getFundsNeededForTakeOffer(Coin tradeAmount, Coin txFeeForDepositTx, Coin txFeeForPayoutTx, Offer offer) {
        boolean buyOffer = OfferUtil.isBuyOffer(offer.getDirection());
        Coin needed = buyOffer ? offer.getSellerSecurityDeposit() : offer.getBuyerSecurityDeposit();

        if (buyOffer)
            needed = needed.add(tradeAmount);

        needed = needed.add(txFeeForDepositTx).add(txFeeForPayoutTx);

        return needed;
    }

    @Nullable
    public static Coin getTakerFee(Coin amount, Preferences preferences, BsqWalletService bsqWalletService) {
        boolean currencyForTakerFeeBtc = isCurrencyForTakerFeeBtc(amount, preferences, bsqWalletService);
        return getTakerFee(currencyForTakerFeeBtc, amount);
    }

    @Nullable
    public static Coin getTakerFee(boolean isCurrencyForTakerFeeBtc, Coin amount) {
        if (amount != null) {
            // TODO write unit test for that
            Coin feePerBtc = CoinUtil.getFeePerBtc(FeeService.getTakerFeePerBtc(isCurrencyForTakerFeeBtc), amount);
            return CoinUtil.maxCoin(feePerBtc, FeeService.getMinTakerFee(isCurrencyForTakerFeeBtc));
        } else {
            return null;
        }
    }

    public static boolean isCurrencyForTakerFeeBtc(Coin amount, Preferences preferences, BsqWalletService bsqWalletService) {
        return preferences.isPayFeeInBtc() || !isBsqForFeeAvailable(amount, bsqWalletService);
    }

    public static boolean isBsqForFeeAvailable(Coin amount, BsqWalletService bsqWalletService) {
        Coin takerFee = getTakerFee(false, amount);
        return BisqEnvironment.isBaseCurrencySupportingBsq() &&
                takerFee != null &&
                bsqWalletService.getAvailableBalance() != null &&
                !bsqWalletService.getAvailableBalance().subtract(takerFee).isNegative();
    }
}
