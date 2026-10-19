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
import bisq.core.btc.model.WithdrawalEstimate;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.provider.fee.FeeService;
import bisq.core.user.Preferences;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletTransaction;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;

import java.math.BigInteger;

import java.util.Set;
import java.util.stream.Collectors;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Estimates withdrawals with the real BtcWalletService and a real bitcoinj wallet, and checks that the amounts of the
 * estimate are what the send of these amounts pays. The keys are fixed, so the signatures and the transaction sizes
 * are the same in every run. One P2WPKH input and a P2WPKH receiver give 110 vbytes, 141 vbytes with a change output.
 */
class BtcWalletServiceWithdrawalEstimateTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();
    private static final Coin FEE_RATE = Coin.valueOf(50);

    private final ECKey fromKey = ECKey.fromPrivate(BigInteger.valueOf(1_000_001));
    private final Address fromAddress = SegwitAddress.fromKey(PARAMS, fromKey);
    private final Address changeAddress = SegwitAddress.fromKey(PARAMS, ECKey.fromPrivate(BigInteger.valueOf(1_000_002)));
    private final String toAddress = SegwitAddress.fromKey(PARAMS, ECKey.fromPrivate(BigInteger.valueOf(1_000_003)))
            .toString();
    private final Set<String> fromAddresses = Set.of(fromAddress.toString());
    private final FeeService feeService = mock(FeeService.class);
    private SendStoppingWallet wallet;
    private BtcWalletService btcWalletService;
    private int fundingTxs;

    @BeforeEach
    void setUp() {
        Context.propagate(new Context(PARAMS));
        wallet = new SendStoppingWallet();
        wallet.importKey(fromKey);

        ImmutableList<AddressEntry> availableEntries = ImmutableList.of(availableEntry(fromAddress),
                availableEntry(changeAddress));
        AddressEntryList addressEntryList = mock(AddressEntryList.class);
        when(addressEntryList.getAddressEntriesAsListImmutable()).thenReturn(availableEntries);

        WalletsSetup walletsSetup = mock(WalletsSetup.class);
        when(walletsSetup.getParams()).thenReturn(PARAMS);
        when(walletsSetup.getBtcWallet()).thenReturn(wallet);
        when(walletsSetup.getChain()).thenReturn(mock(BlockChain.class));
        when(walletsSetup.getAddressesFromAddressEntries(any())).thenAnswer(invocation -> {
            Set<AddressEntry> addressEntries = invocation.getArgument(0);
            return addressEntries.stream().map(AddressEntry::getAddress).collect(Collectors.toSet());
        });
        Preferences preferences = mock(Preferences.class);
        when(preferences.getIgnoreDustThreshold()).thenReturn(546);
        when(feeService.getTxFeePerVbyte()).thenReturn(FEE_RATE);

        btcWalletService = new BtcWalletService(walletsSetup, addressEntryList, preferences, feeService);
        ArgumentCaptor<Runnable> setupCompletedHandler = ArgumentCaptor.forClass(Runnable.class);
        verify(walletsSetup).addSetupCompletedHandler(setupCompletedHandler.capture());
        setupCompletedHandler.getValue().run();
    }

    // The 300 satoshi left of the inputs are below bitcoinj's change limit, so bitcoinj adds them to the fee
    @Test
    void withTheFeeIncludedTheSenderSpendsARemainderThatBitcoinjAddsToTheFee() throws Exception {
        fund(8000);

        WithdrawalEstimate estimate = estimate(Coin.valueOf(7700), false);

        assertAmounts(estimate, 2200, 5800, 8000);
        assertEquals(Coin.ZERO, estimate.getDust());
        assertSent(send(estimate), 2200, 5800, 1);
    }

    // The 540 satoshi left of the inputs are above bitcoinj's change limit of 537 but below our dust limit
    @Test
    void withTheFeeIncludedTheSenderSpendsAChangeOutputBelowOurDustLimit() throws Exception {
        fund(8240);

        WithdrawalEstimate estimate = estimate(Coin.valueOf(7700), false);

        assertAmounts(estimate, 650, 7590, 8240);
        assertEquals(Coin.valueOf(540), estimate.getDust());
        assertSent(send(estimate), 650, 7590, 1);
    }

    @Test
    void withTheFeeExcludedTheReceiverGetsTheAmountAndTheChangeIsReturned() throws Exception {
        fund(10_000);

        WithdrawalEstimate estimate = estimate(Coin.valueOf(1000), true);

        assertAmounts(estimate, 1000, 7050, 8050);
        assertSent(send(estimate), 1000, 7050, 2);
    }

    @Test
    void anAmountWellBelowTheInputsLeavesAChangeOutput() throws Exception {
        fund(100_000);

        WithdrawalEstimate estimate = estimate(Coin.valueOf(50_000), false);

        assertAmounts(estimate, 42_950, 7050, 50_000);
        assertSent(send(estimate), 42_950, 7050, 2);
    }

    // The desktop and the API pass a rate that can differ from the rate of the preferences
    @Test
    void theEstimateIsCheckedWithTheRateItWasBuiltWith() throws Exception {
        fund(100_000);
        when(feeService.getTxFeePerVbyte()).thenReturn(Coin.valueOf(10));

        WithdrawalEstimate estimate = estimate(Coin.valueOf(50_000), false);

        assertAmounts(estimate, 42_950, 7050, 50_000);
        // The first round has no fee, the second one has the fee for the size of the first one
        assertEquals(2, wallet.completedTxs);
    }

    private WithdrawalEstimate estimate(Coin amount, boolean feeExcluded) throws Exception {
        return btcWalletService.getWithdrawalEstimate(fromAddresses, toAddress, amount, feeExcluded, FEE_RATE);
    }

    // As the desktop sends: the sender's amount and the fee of the estimate
    private Transaction send(WithdrawalEstimate estimate) {
        assertThrows(SendStoppedException.class, () -> btcWalletService.sendFundsForMultipleAddresses(fromAddresses,
                toAddress,
                estimate.getSendersAmount(),
                estimate.getFee(),
                null,
                null,
                null,
                noCallback()));
        assertNotNull(wallet.sentTx);
        return wallet.sentTx;
    }

    private static void assertAmounts(WithdrawalEstimate estimate, long receiverAmount, long fee, long sendersAmount) {
        assertEquals(Coin.valueOf(receiverAmount), estimate.getReceiverAmount());
        assertEquals(Coin.valueOf(fee), estimate.getFee());
        assertEquals(Coin.valueOf(sendersAmount), estimate.getSendersAmount());
    }

    private void assertSent(Transaction sentTx, long receiverAmount, long fee, int numOutputs) {
        assertEquals(Coin.valueOf(receiverAmount), sentTx.getOutput(0).getValue());
        assertEquals(toAddress, sentTx.getOutput(0).getScriptPubKey().getToAddress(PARAMS).toString());
        assertEquals(Coin.valueOf(fee), sentTx.getFee());
        assertEquals(numOutputs, sentTx.getOutputs().size());
    }

    private void fund(long... values) {
        for (long value : values) {
            Transaction fundingTx = new Transaction(PARAMS);
            fundingTx.addInput(Sha256Hash.of(new byte[]{(byte) ++fundingTxs}), 0, new Script(new byte[0]));
            fundingTx.addOutput(Coin.valueOf(value), fromAddress);
            TransactionConfidence confidence = fundingTx.getConfidence();
            confidence.setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING);
            confidence.setAppearedAtChainHeight(1);
            confidence.setDepthInBlocks(1);
            wallet.addWalletTransaction(new WalletTransaction(WalletTransaction.Pool.UNSPENT, fundingTx));
        }
    }

    private static AddressEntry availableEntry(Address address) {
        AddressEntry addressEntry = mock(AddressEntry.class);
        when(addressEntry.getContext()).thenReturn(AddressEntry.Context.AVAILABLE);
        when(addressEntry.getAddress()).thenReturn(address);
        when(addressEntry.getAddressString()).thenReturn(address.toString());
        return addressEntry;
    }

    private static FutureCallback<Transaction> noCallback() {
        return new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable Transaction result) {
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
            }
        };
    }

    private static class SendStoppedException extends RuntimeException {
    }

    // A send completes the transaction as a real send does, then stops before the transaction is committed or
    // broadcast
    private static class SendStoppingWallet extends Wallet {
        // The rounds of the fee estimation
        private int completedTxs;
        @Nullable
        private Transaction sentTx;

        private SendStoppingWallet() {
            super(PARAMS, KeyChainGroup.createBasic(PARAMS));
        }

        @Override
        public void completeTx(SendRequest request) throws InsufficientMoneyException {
            completedTxs++;
            super.completeTx(request);
        }

        @Override
        public SendResult sendCoins(SendRequest request) throws InsufficientMoneyException {
            super.completeTx(request);
            sentTx = request.tx;
            throw new SendStoppedException();
        }
    }
}
