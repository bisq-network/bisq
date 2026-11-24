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

package bisq.desktop.main.funds.withdrawal;

import bisq.core.btc.wallet.Restrictions;
import bisq.core.locale.Res;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.coin.ImmutableCoinFormatter;
import bisq.core.util.validation.BtcAddressValidator;

import bisq.common.config.Config;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.SegwitAddress;

import java.math.BigInteger;

import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WithdrawalViewInputErrorTest {
    private static final Coin AVAILABLE = Coin.valueOf(100_000);
    private static final Coin DUST_LIMIT = Restrictions.getMinNonDustOutput();
    private static final String INVALID_ADDRESS = "not an address";

    private final BtcAddressValidator btcAddressValidator = new BtcAddressValidator();
    private final CoinFormatter formatter =
            new ImmutableCoinFormatter(Config.baseCurrencyNetworkParameters().getMonetaryFormat());
    private final String validAddress = SegwitAddress.fromKey(Config.baseCurrencyNetworkParameters(),
            ECKey.fromPrivate(BigInteger.valueOf(1_000_001))).toString();

    @BeforeAll
    static void setUpRes() {
        Res.setup();
    }

    @Test
    void anAmountAtTheDustLimitToAValidAddressPasses() {
        assertEquals(Optional.empty(), getInputError(AVAILABLE, DUST_LIMIT, validAddress));
    }

    @Test
    void aMissingSourceAddressIsReportedFirst() {
        assertEquals(Optional.of(Res.get("funds.withdrawal.warn.noSourceAddressSelected")),
                getInputError(Coin.ZERO, DUST_LIMIT.subtract(Coin.SATOSHI), INVALID_ADDRESS));
    }

    @Test
    void anAmountBelowTheDustLimitIsReportedBeforeTheAddress() {
        assertEquals(Optional.of(Res.get("validation.amountBelowDust", formatter.formatCoinWithCode(DUST_LIMIT))),
                getInputError(AVAILABLE, DUST_LIMIT.subtract(Coin.SATOSHI), INVALID_ADDRESS));
    }

    @Test
    void anInvalidAddressIsReported() {
        assertEquals(Optional.of(Res.get("validation.btc.invalidAddress")),
                getInputError(AVAILABLE, DUST_LIMIT, INVALID_ADDRESS));
    }

    private Optional<String> getInputError(Coin available, Coin amount, String address) {
        return WithdrawalView.getInputError(available, amount, address, btcAddressValidator, formatter);
    }
}
