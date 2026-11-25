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

package bisq.core.btc.wallet;

import bisq.core.btc.model.AddressEntryList;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.provider.fee.FeeService;
import bisq.core.user.Preferences;

import bisq.common.config.Config;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.wallet.Wallet;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WalletServiceAddressTransactionsTest {
    @Test
    void returnsBothPaymentsWhenTheLatestConfidenceBelongsToAnotherPayment() {
        NetworkParameters params = Config.baseCurrencyNetworkParameters();
        Context.propagate(new Context(params));
        Address address = SegwitAddress.fromKey(params, new ECKey());
        Transaction payout = new Transaction(params);
        payout.addOutput(Coin.COIN, address);
        payout.getConfidence().setAppearedAtChainHeight(100);
        Transaction laterPayment = new Transaction(params);
        laterPayment.addOutput(Coin.CENT, address);
        laterPayment.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.PENDING);
        BtcWalletService service = new BtcWalletService(mock(WalletsSetup.class), mock(AddressEntryList.class),
                mock(Preferences.class), mock(FeeService.class));
        service.wallet = mock(Wallet.class);
        when(service.wallet.getTransactions(false)).thenReturn(Set.of(laterPayment, payout));

        assertSame(laterPayment.getConfidence(), service.getConfidenceForAddress(address));
        assertEquals(Set.of(laterPayment, payout), service.getTransactionsForAddress(address));
    }
}
