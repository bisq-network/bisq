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
import com.google.common.util.concurrent.FutureCallback;

import java.math.BigInteger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;

import org.mockito.ArgumentCaptor;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A BtcWalletService on a real bitcoinj wallet for withdrawal tests. The keys are fixed, so the signatures and the
 * transaction sizes are the same in every run: one P2WPKH input and a P2WPKH receiver give 110 vbytes, 141 vbytes with
 * a change output. A send completes and signs its transaction like a real send and then throws SendStoppedException,
 * before the wallet commits or broadcasts it.
 */
public class WithdrawalTestWallet {
    public static final NetworkParameters PARAMS = MainNetParams.get();
    // The rate of the fee service and the preferences. Withdrawals get their rate as a parameter.
    public static final Coin PREFERENCES_FEE_RATE = Coin.valueOf(10);

    @Getter
    private final String toAddress = SegwitAddress.fromKey(PARAMS, key(100)).toString();
    @Getter
    private final FeeService feeService = mock(FeeService.class);
    @Getter
    private final BtcWalletService btcWalletService;
    private final List<Address> sourceAddresses = new ArrayList<>();
    private final StoppingWallet wallet;
    private int fundingTxs;

    public WithdrawalTestWallet() {
        this(1);
    }

    public WithdrawalTestWallet(int numSourceAddresses) {
        Context.propagate(new Context(PARAMS));
        wallet = new StoppingWallet();
        List<AddressEntry> addressEntries = new ArrayList<>();
        for (int i = 0; i < numSourceAddresses; i++) {
            ECKey key = key(1 + i);
            wallet.importKey(key);
            Address address = SegwitAddress.fromKey(PARAMS, key);
            sourceAddresses.add(address);
            addressEntries.add(availableAddressEntry(address));
        }
        // An unused address for the change
        addressEntries.add(availableAddressEntry(SegwitAddress.fromKey(PARAMS, key(50))));
        AddressEntryList addressEntryList = mock(AddressEntryList.class);
        when(addressEntryList.getAddressEntriesAsListImmutable()).thenReturn(ImmutableList.copyOf(addressEntries));

        WalletsSetup walletsSetup = mock(WalletsSetup.class);
        when(walletsSetup.getParams()).thenReturn(PARAMS);
        when(walletsSetup.getBtcWallet()).thenReturn(wallet);
        when(walletsSetup.getChain()).thenReturn(mock(BlockChain.class));
        when(walletsSetup.getAddressesFromAddressEntries(any())).thenAnswer(invocation -> {
            Set<AddressEntry> entries = invocation.getArgument(0);
            return entries.stream().map(AddressEntry::getAddress).collect(Collectors.toSet());
        });
        Preferences preferences = mock(Preferences.class);
        when(preferences.getIgnoreDustThreshold()).thenReturn(546);
        when(feeService.getTxFeePerVbyte()).thenReturn(PREFERENCES_FEE_RATE);

        btcWalletService = new BtcWalletService(walletsSetup, addressEntryList, preferences, feeService);
        ArgumentCaptor<Runnable> setupCompletedHandler = ArgumentCaptor.forClass(Runnable.class);
        verify(walletsSetup).addSetupCompletedHandler(setupCompletedHandler.capture());
        setupCompletedHandler.getValue().run();
    }

    public Set<String> getSourceAddresses() {
        return sourceAddresses.stream().map(Address::toString).collect(Collectors.toSet());
    }

    // Each value becomes a confirmed coin of the first source address
    public void fund(long... values) {
        fundSourceAddress(0, values);
    }

    public void fundSourceAddress(int sourceAddressIndex, long... values) {
        for (long value : values) {
            Transaction fundingTx = new Transaction(PARAMS);
            fundingTx.addInput(Sha256Hash.of(new byte[]{(byte) ++fundingTxs}), 0, new Script(new byte[0]));
            fundingTx.addOutput(Coin.valueOf(value), sourceAddresses.get(sourceAddressIndex));
            TransactionConfidence confidence = fundingTx.getConfidence();
            confidence.setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING);
            confidence.setAppearedAtChainHeight(1);
            confidence.setDepthInBlocks(1);
            wallet.addWalletTransaction(new WalletTransaction(WalletTransaction.Pool.UNSPENT, fundingTx));
        }
    }

    // How often the wallet completed a transaction, the rounds of a fee estimation
    public int getCompletedTxs() {
        return wallet.completedTxs;
    }

    // The transaction of the last send, completed and signed but not committed
    @Nullable
    public Transaction getSentTx() {
        return wallet.sentTx;
    }

    // Sends from all source addresses as the desktop and the API do: the receiver gets sendersAmount minus the fee
    public Transaction send(Coin sendersAmount, Coin fee) throws Exception {
        try {
            btcWalletService.sendFundsForMultipleAddresses(getSourceAddresses(),
                    toAddress,
                    sendersAmount,
                    fee,
                    null,
                    null,
                    null,
                    noCallback());
        } catch (SendStoppedException e) {
            return wallet.sentTx;
        }
        throw new IllegalStateException("The send was not stopped");
    }

    public static FutureCallback<Transaction> noCallback() {
        return new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable Transaction result) {
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
            }
        };
    }

    private static ECKey key(int index) {
        return ECKey.fromPrivate(BigInteger.valueOf(1_000_000 + index));
    }

    private static AddressEntry availableAddressEntry(Address address) {
        AddressEntry addressEntry = mock(AddressEntry.class);
        when(addressEntry.getContext()).thenReturn(AddressEntry.Context.AVAILABLE);
        when(addressEntry.getAddress()).thenReturn(address);
        when(addressEntry.getAddressString()).thenReturn(address.toString());
        return addressEntry;
    }

    public static class SendStoppedException extends RuntimeException {
    }

    private static class StoppingWallet extends Wallet {
        private int completedTxs;
        @Nullable
        private Transaction sentTx;

        private StoppingWallet() {
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
