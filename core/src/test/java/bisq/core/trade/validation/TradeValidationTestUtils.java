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

import bisq.core.btc.wallet.BtcWalletService;

import org.bitcoinj.params.MainNetParams;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TradeValidationTestUtils {
    static final MainNetParams PARAMS = MainNetParams.get();

    static final int GENESIS_HEIGHT = 102;

    static final String VALID_TRANSACTION_ID =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";


    static BtcWalletService btcWalletService() {
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        when(btcWalletService.getParams()).thenReturn(PARAMS);
        return btcWalletService;
    }
}
