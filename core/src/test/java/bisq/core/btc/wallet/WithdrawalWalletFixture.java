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

import java.math.BigInteger;

import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;

import org.mockito.ArgumentCaptor;

import javax.annotation.Nullable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The real BtcWalletService with a real bitcoinj wallet for withdrawal tests. A send completes the transaction as a
 * real send does, then stops with a SendStoppedException before the transaction is committed or broadcast. The keys
 * are fixed, so the signatures and the transaction sizes are the same in every run. With the amounts of the tests,
 * one P2WPKH input and a P2WPKH receiver give 110 vbytes, 141 vbytes with a change output.
 */
public class WithdrawalWalletFixture {
    public static final NetworkParameters PARAMS = MainNetParams.get();

    private final ECKey fromKey = ECKey.fromPrivate(BigInteger.valueOf(1_000_001));
    private final Address fromAddress = SegwitAddress.fromKey(PARAMS, fromKey);
    private final Address changeAddress =
            SegwitAddress.fromKey(PARAMS, ECKey.fromPrivate(BigInteger.valueOf(1_000_002)));
    @Getter
    private final String toAddress = SegwitAddress.fromKey(PARAMS, ECKey.fromPrivate(BigInteger.valueOf(1_000_003)))
            .toString();
    @Getter
    private final Set<String> fromAddresses = Set.of(fromAddress.toString());
    @Getter
    private final FeeService feeService = mock(FeeService.class);
    @Getter
    private final BtcWalletService btcWalletService;
    private final SendStoppingWallet wallet;
    private int fundingTxs;

    public WithdrawalWalletFixture(Coin feeRate) {
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
        when(feeService.getTxFeePerVbyte()).thenReturn(feeRate);

        btcWalletService = new BtcWalletService(walletsSetup, addressEntryList, preferences, feeService);
        ArgumentCaptor<Runnable> setupCompletedHandler = ArgumentCaptor.forClass(Runnable.class);
        verify(walletsSetup).addSetupCompletedHandler(setupCompletedHandler.capture());
        setupCompletedHandler.getValue().run();
    }

    // Each value is a confirmed output to the source address
    public void fund(long... values) {
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

    // The rounds of the fee estimation
    public int getCompletedTxs() {
        return wallet.completedTxs;
    }

    @Nullable
    public Transaction getSentTx() {
        return wallet.sentTx;
    }

    private static AddressEntry availableEntry(Address address) {
        AddressEntry addressEntry = mock(AddressEntry.class);
        when(addressEntry.getContext()).thenReturn(AddressEntry.Context.AVAILABLE);
        when(addressEntry.getAddress()).thenReturn(address);
        when(addressEntry.getAddressString()).thenReturn(address.toString());
        return addressEntry;
    }

    public static class SendStoppedException extends RuntimeException {
    }

    private static class SendStoppingWallet extends Wallet {
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
