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

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.model.AddressEntryList;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.provider.fee.FeeService;
import bisq.core.user.Preferences;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import com.google.common.collect.ImmutableList;

import java.util.Set;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A fee estimation round whose transaction has a change output and pays more than 1000 satoshi above the fee for its
 * size is followed by another round. Only the remainder of a transaction without a change output cannot be lowered.
 */
class BtcWalletServiceFeeEstimationRoundsTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();
    private static final Coin FEE_RATE = Coin.valueOf(10);

    private final Address fromAddress = SegwitAddress.fromKey(PARAMS, new ECKey());
    private final Address changeAddress = SegwitAddress.fromKey(PARAMS, new ECKey());
    private int rounds;

    @Test
    void anOverpaidTransactionWithAChangeOutputIsEstimatedAgain() throws Exception {
        BtcWalletService btcWalletService = btcWalletService(sendRequest -> {
            rounds++;
            // The second round pays 1500 satoshi more than it was asked for and returns a change output
            long extra = rounds == 2 ? 1500 : 0;
            sendRequest.tx.addOutput(Coin.valueOf(10_000), changeAddress);
            Transaction fundingTx = new Transaction(PARAMS);
            fundingTx.addOutput(sendRequest.tx.getOutputSum().add(sendRequest.fee).add(Coin.valueOf(extra)),
                    fromAddress);
            sendRequest.tx.addInput(fundingTx.getOutput(0));
        });

        Transaction tx = btcWalletService.getWithdrawalEstimate(Set.of(fromAddress.toString()),
                SegwitAddress.fromKey(PARAMS, new ECKey()).toString(),
                Coin.valueOf(100_000),
                false,
                FEE_RATE).getTransaction();

        assertEquals(3, rounds);
        assertEquals(FEE_RATE.multiply(tx.getVsize()), tx.getFee());
    }

    private interface CompleteTx {
        void complete(SendRequest sendRequest);
    }

    private BtcWalletService btcWalletService(CompleteTx completeTx) throws Exception {
        Context.propagate(new Context(PARAMS));
        Wallet wallet = mock(Wallet.class);
        doAnswer(invocation -> {
            completeTx.complete(invocation.getArgument(0));
            return null;
        }).when(wallet).completeTx(any(SendRequest.class));

        ImmutableList<AddressEntry> addressEntries = ImmutableList.of(availableEntry(fromAddress),
                availableEntry(changeAddress));
        AddressEntryList addressEntryList = mock(AddressEntryList.class);
        when(addressEntryList.getAddressEntriesAsListImmutable()).thenReturn(addressEntries);

        WalletsSetup walletsSetup = mock(WalletsSetup.class);
        when(walletsSetup.getParams()).thenReturn(PARAMS);
        when(walletsSetup.getBtcWallet()).thenReturn(wallet);
        when(walletsSetup.getChain()).thenReturn(mock(BlockChain.class));
        Preferences preferences = mock(Preferences.class);
        when(preferences.getIgnoreDustThreshold()).thenReturn(546);
        FeeService feeService = mock(FeeService.class);
        when(feeService.getTxFeePerVbyte()).thenReturn(FEE_RATE);

        BtcWalletService btcWalletService = new BtcWalletService(walletsSetup, addressEntryList, preferences,
                feeService);
        ArgumentCaptor<Runnable> setupCompletedHandler = ArgumentCaptor.forClass(Runnable.class);
        verify(walletsSetup).addSetupCompletedHandler(setupCompletedHandler.capture());
        setupCompletedHandler.getValue().run();
        return btcWalletService;
    }

    private static AddressEntry availableEntry(Address address) {
        AddressEntry addressEntry = mock(AddressEntry.class);
        when(addressEntry.getContext()).thenReturn(AddressEntry.Context.AVAILABLE);
        when(addressEntry.getAddress()).thenReturn(address);
        when(addressEntry.getAddressString()).thenReturn(address.toString());
        return addressEntry;
    }
}
