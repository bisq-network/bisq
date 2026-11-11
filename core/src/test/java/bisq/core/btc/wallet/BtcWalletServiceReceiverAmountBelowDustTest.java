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

import bisq.core.btc.exceptions.ReceiverAmountBelowDustException;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Checks that the fee estimation of a withdrawal rejects an amount that leaves the receiver below the dust limit with a
 * ReceiverAmountBelowDustException, for the amount itself and for the amount after the fee, and that with the fee
 * excluded the receiver gets the amount. It uses the real BtcWalletService. The wallet funds each request with one
 * input that leaves the requested fee.
 */
class BtcWalletServiceReceiverAmountBelowDustTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();
    private static final Coin FEE_RATE = Coin.valueOf(10);

    private final Address fromAddress = SegwitAddress.fromKey(PARAMS, new ECKey());
    private final String toAddress = SegwitAddress.fromKey(PARAMS, new ECKey()).toString();
    private BtcWalletService btcWalletService;

    @BeforeEach
    void setUp() throws Exception {
        Context.propagate(new Context(PARAMS));
        Wallet wallet = mock(Wallet.class);
        doAnswer(invocation -> {
            SendRequest sendRequest = invocation.getArgument(0);
            Transaction fundingTx = new Transaction(PARAMS);
            fundingTx.addOutput(sendRequest.tx.getOutputSum().add(sendRequest.fee), fromAddress);
            sendRequest.tx.addInput(fundingTx.getOutput(0));
            return null;
        }).when(wallet).completeTx(any(SendRequest.class));

        AddressEntry addressEntry = mock(AddressEntry.class);
        when(addressEntry.getContext()).thenReturn(AddressEntry.Context.AVAILABLE);
        when(addressEntry.getAddress()).thenReturn(fromAddress);
        when(addressEntry.getAddressString()).thenReturn(fromAddress.toString());
        AddressEntryList addressEntryList = mock(AddressEntryList.class);
        when(addressEntryList.getAddressEntriesAsListImmutable()).thenReturn(ImmutableList.of(addressEntry));

        WalletsSetup walletsSetup = mock(WalletsSetup.class);
        when(walletsSetup.getParams()).thenReturn(PARAMS);
        when(walletsSetup.getBtcWallet()).thenReturn(wallet);
        when(walletsSetup.getChain()).thenReturn(mock(BlockChain.class));
        Preferences preferences = mock(Preferences.class);
        when(preferences.getIgnoreDustThreshold()).thenReturn(546);
        FeeService feeService = mock(FeeService.class);
        when(feeService.getTxFeePerVbyte()).thenReturn(FEE_RATE);

        btcWalletService = new BtcWalletService(walletsSetup, addressEntryList, preferences, feeService);
        ArgumentCaptor<Runnable> setupCompletedHandler = ArgumentCaptor.forClass(Runnable.class);
        verify(walletsSetup).addSetupCompletedHandler(setupCompletedHandler.capture());
        setupCompletedHandler.getValue().run();
    }

    // Callers which do not handle it, such as the API, keep the error they got before
    @Test
    void anAmountBelowDustIsRejected() {
        Coin amount = Restrictions.getMinNonDustOutput().subtract(Coin.SATOSHI);

        IllegalArgumentException exception = assertThrows(ReceiverAmountBelowDustException.class,
                () -> estimateFromAddresses(amount));

        assertEquals("The amount is too low (dust limit).", exception.getMessage());
    }

    // The first round of the estimation has no fee, a later round deducts it from the amount
    @Test
    void anAmountThatTheFeeLeavesBelowDustIsRejected() throws Exception {
        Coin fee = estimateFromAddresses(Coin.valueOf(100_000)).getFee();
        Coin amountLeavingDust = Restrictions.getMinNonDustOutput().add(fee);

        Transaction tx = estimateFromAddresses(amountLeavingDust);
        assertEquals(Restrictions.getMinNonDustOutput(), tx.getOutput(0).getValue());
        assertThrows(ReceiverAmountBelowDustException.class,
                () -> estimateFromAddresses(amountLeavingDust.subtract(Coin.SATOSHI)));
    }

    @Test
    void anAmountThatTheFeeLeavesBelowDustIsRejectedFromOneAddress() throws Exception {
        Coin fee = estimateFromAddress(Coin.valueOf(100_000)).getFee();
        Coin amountLeavingDust = Restrictions.getMinNonDustOutput().add(fee);

        Transaction tx = estimateFromAddress(amountLeavingDust);
        assertEquals(Restrictions.getMinNonDustOutput(), tx.getOutput(0).getValue());
        assertThrows(ReceiverAmountBelowDustException.class,
                () -> estimateFromAddress(amountLeavingDust.subtract(Coin.SATOSHI)));
    }

    // With the fee excluded the receiver gets the amount, which can be lower than the fee
    @Test
    void withTheFeeExcludedTheReceiverGetsTheAmount() throws Exception {
        Coin amount = Restrictions.getMinNonDustOutput();

        Transaction tx = estimateFeeExcluded(amount);

        assertEquals(amount, tx.getOutput(0).getValue());
        assertEquals(FEE_RATE.multiply(tx.getVsize()), tx.getFee());
        assertTrue(tx.getFee().isGreaterThan(amount));
    }

    @Test
    void withTheFeeExcludedAnAmountBelowDustIsRejected() {
        assertThrows(ReceiverAmountBelowDustException.class,
                () -> estimateFeeExcluded(Restrictions.getMinNonDustOutput().subtract(Coin.SATOSHI)));
    }

    private Transaction estimateFromAddresses(Coin amount) throws Exception {
        return btcWalletService.getWithdrawalEstimate(Set.of(fromAddress.toString()),
                toAddress,
                amount,
                false,
                FEE_RATE).getTransaction();
    }

    private Transaction estimateFeeExcluded(Coin amount) throws Exception {
        return btcWalletService.getWithdrawalEstimate(Set.of(fromAddress.toString()),
                toAddress,
                amount,
                true,
                FEE_RATE).getTransaction();
    }

    private Transaction estimateFromAddress(Coin amount) throws Exception {
        return btcWalletService.getFeeEstimationTransaction(fromAddress.toString(),
                toAddress,
                amount,
                AddressEntry.Context.AVAILABLE);
    }
}
