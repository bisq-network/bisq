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

package bisq.desktop.util;

import bisq.core.app.BisqEnvironment;
import bisq.core.provider.price.MarketPrice;

import bisq.common.app.DevEnv;
import bisq.common.util.MathUtils;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.MonetaryFormat;

import javax.inject.Inject;

import java.text.DecimalFormat;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BsqFormatter extends BSFormatter {
    @SuppressWarnings("PointlessBooleanExpression")
    private static final boolean useBsqAddressFormat = true || !DevEnv.isDevMode();
    private final String prefix = "B";
    private final DecimalFormat amountFormat = new DecimalFormat("###,###,###.##");
    private final DecimalFormat marketCapFormat = new DecimalFormat("###,###,###");

    @Inject
    private BsqFormatter() {
        super();

        final String baseCurrencyCode = BisqEnvironment.getBaseCurrencyNetwork().getCurrencyCode();
        switch (baseCurrencyCode) {
            case "BTC":
                coinFormat = new MonetaryFormat().shift(6).code(6, "BSQ").minDecimals(2);
                break;
            case "LTC":
                coinFormat = new MonetaryFormat().shift(3).code(3, "BSQ").minDecimals(5);
                break;
            case "DASH":
                // BSQ for DASH not used/supported
                coinFormat = new MonetaryFormat().shift(3).code(3, "???").minDecimals(5);
                break;
            default:
                throw new RuntimeException("baseCurrencyCode not defined. baseCurrencyCode=" + baseCurrencyCode);
        }

        amountFormat.setMinimumFractionDigits(2);
    }

    /**
     * Returns the base-58 encoded String representation of this
     * object, including version and checksum bytes.
     */
    public String getBsqAddressStringFromAddress(Address address) {
        final String addressString = address.toString();
        if (useBsqAddressFormat)
            return prefix + addressString;
        else
            return addressString;

    }

    public Address getAddressFromBsqAddress(String encoded) {
        if (useBsqAddressFormat)
            encoded = encoded.substring(prefix.length(), encoded.length());

        try {
            return Address.fromBase58(BisqEnvironment.getParameters(), encoded);
        } catch (AddressFormatException e) {
            throw new RuntimeException(e);
        }
    }

    public String formatAmountWithGroupSeparatorAndCode(Coin amount) {
        return amountFormat.format(MathUtils.scaleDownByPowerOf10(amount.value, 2)) + " BSQ";
    }

    public String formatMarketCap(MarketPrice bsqPriceMarketPrice, MarketPrice fiatMarketPrice, Coin issuedAmount) {
        if (bsqPriceMarketPrice != null && fiatMarketPrice != null) {
            double marketCap = bsqPriceMarketPrice.getPrice() * fiatMarketPrice.getPrice() * (MathUtils.scaleDownByPowerOf10(issuedAmount.value, 2));
            return marketCapFormat.format(MathUtils.doubleToLong(marketCap)) + " " + fiatMarketPrice.getCurrencyCode();
        } else {
            return "";
        }
    }
}
