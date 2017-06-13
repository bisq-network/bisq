/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.util;

import io.bisq.common.app.DevEnv;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.wallet.WalletUtils;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.utils.MonetaryFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public class BsqFormatter extends BSFormatter {
    private static final Logger log = LoggerFactory.getLogger(BsqFormatter.class);
    @SuppressWarnings("PointlessBooleanExpression")
    private static final boolean useBsqAddressFormat = true || !DevEnv.DEV_MODE;
    private final String prefix = "B";

    @Inject
    private BsqFormatter(BisqEnvironment bisqEnvironment) {
        super();

        final String baseCurrencyCode = bisqEnvironment.getBaseCurrencyNetwork().getCurrencyCode();
        switch (baseCurrencyCode) {
            case "BTC":
                coinFormat = new MonetaryFormat().shift(5).code(5, "BSQ").minDecimals(3);
                break;
            case "LTC":
                coinFormat = new MonetaryFormat().shift(3).code(3, "BSQ").minDecimals(5);
                break;
            case "DOGE":
                // TODO check
                coinFormat = new MonetaryFormat().shift(3).code(3, "BSQ").minDecimals(5);
                break;
            default:
                throw new RuntimeException("baseCurrencyCode not defined. baseCurrencyCode=" + baseCurrencyCode);
        }
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
            return Address.fromBase58(WalletUtils.getParameters(), encoded);
        } catch (AddressFormatException e) {
            log.error(e.toString());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
