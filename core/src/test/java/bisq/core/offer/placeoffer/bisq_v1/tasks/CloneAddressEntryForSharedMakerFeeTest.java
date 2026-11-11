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

package bisq.core.offer.placeoffer.bisq_v1.tasks;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.model.AddressEntryList;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.offer.placeoffer.bisq_v1.PlaceOfferModel;
import bisq.core.provider.fee.FeeService;
import bisq.core.user.Preferences;

import bisq.common.persistence.PersistenceManager;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletTransaction;

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloneAddressEntryForSharedMakerFeeTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();
    private static final String CLONED_OFFER_ID = "cloned-offer";

    private Wallet wallet;
    private BtcWalletService walletService;
    private AddressEntry originalReserve;
    private Transaction makerFeeTx;

    @BeforeEach
    void setUp() {
        // Wallet creation needs a context of PARAMS on the thread, and another test may leave a RegTest one
        Context.propagate(new Context(PARAMS));
        wallet = Wallet.createDeterministic(PARAMS, Script.ScriptType.P2WPKH);
        originalReserve = new AddressEntry(wallet.freshReceiveKey(),
                AddressEntry.Context.RESERVED_FOR_TRADE, "original-offer", true);
        @SuppressWarnings("unchecked")
        PersistenceManager<AddressEntryList> persistenceManager = mock(PersistenceManager.class);
        AddressEntryList addressEntries = new AddressEntryList(persistenceManager);
        addressEntries.addAddressEntry(originalReserve);
        WalletsSetup walletsSetup = mock(WalletsSetup.class);
        when(walletsSetup.getBtcWallet()).thenReturn(wallet);
        when(walletsSetup.getParams()).thenReturn(PARAMS);
        when(walletsSetup.getChain()).thenReturn(mock(BlockChain.class));
        walletService = new BtcWalletService(walletsSetup,
                addressEntries, mock(Preferences.class), mock(FeeService.class));
        ArgumentCaptor<Runnable> setupCompletedHandler = ArgumentCaptor.forClass(Runnable.class);
        verify(walletsSetup).addSetupCompletedHandler(setupCompletedHandler.capture());
        setupCompletedHandler.getValue().run();

        // Like CreateMakerFeeTx, the maker fee tx pays the trade fee, the reserved funds and the change
        makerFeeTx = tx(Coin.valueOf(5_000), SegwitAddress.fromKey(PARAMS, new ECKey()));
        makerFeeTx.addOutput(Coin.valueOf(60_000), originalReserve.getAddress());
        makerFeeTx.addOutput(Coin.valueOf(100_000), wallet.freshReceiveAddress());
        makerFeeTx.setUpdateTime(new Date(1_000_000));
    }

    @Test
    void clonesTheReserveOfTheMakerFeeTxWhenNewerCoinsAreOnItsAddress() {
        addToWallet(makerFeeTx, WalletTransaction.Pool.UNSPENT);
        addOtherCoinsToReservedAddress();

        assertTrue(runCloneAddressEntry());

        AddressEntry clonedReserve = walletService.getAddressEntry(CLONED_OFFER_ID,
                AddressEntry.Context.RESERVED_FOR_TRADE).orElseThrow();
        assertEquals(originalReserve.getAddress(), clonedReserve.getAddress());
    }

    @Test
    void failsWhenTheReservedOutputIsSpent() {
        addToWallet(makerFeeTx, WalletTransaction.Pool.UNSPENT);
        TransactionOutput reservedOutput = makerFeeTx.getOutput(1);
        reservedOutput.markAsSpent(new Transaction(PARAMS).addInput(reservedOutput));
        addOtherCoinsToReservedAddress();

        assertFalse(runCloneAddressEntry());

        assertTrue(walletService.getAddressEntry(CLONED_OFFER_ID, AddressEntry.Context.RESERVED_FOR_TRADE).isEmpty());
    }

    @Test
    void failsWhenTheMakerFeeTxIsDead() {
        makerFeeTx.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.DEAD);
        addToWallet(makerFeeTx, WalletTransaction.Pool.DEAD);

        assertFalse(runCloneAddressEntry());

        assertTrue(walletService.getAddressEntry(CLONED_OFFER_ID, AddressEntry.Context.RESERVED_FOR_TRADE).isEmpty());
    }

    private boolean runCloneAddressEntry() {
        Offer offer = mock(Offer.class);
        when(offer.getId()).thenReturn(CLONED_OFFER_ID);
        when(offer.getOfferFeePaymentTxId()).thenReturn(makerFeeTx.getTxId().toString());
        PlaceOfferModel model = mock(PlaceOfferModel.class);
        when(model.getOffer()).thenReturn(offer);
        when(model.getWalletService()).thenReturn(walletService);
        AtomicBoolean completed = new AtomicBoolean();
        TaskRunner<PlaceOfferModel> taskRunner = new TaskRunner<>(model, PlaceOfferModel.class,
                () -> completed.set(true), errorMessage -> {
        });
        taskRunner.addTasks(CloneAddressEntryForSharedMakerFee.class);
        taskRunner.run();
        return completed.get();
    }

    // Coins received after the maker fee tx are the most recent transaction on the reserved address
    private void addOtherCoinsToReservedAddress() {
        Transaction otherCoinsTx = tx(Coin.valueOf(5_321_500), originalReserve.getAddress());
        otherCoinsTx.setUpdateTime(new Date(2_000_000));
        addToWallet(otherCoinsTx, WalletTransaction.Pool.UNSPENT);
    }

    private void addToWallet(Transaction tx, WalletTransaction.Pool pool) {
        wallet.addWalletTransaction(new WalletTransaction(pool, tx));
    }

    private static Transaction tx(Coin value, Address address) {
        Transaction tx = new Transaction(PARAMS);
        tx.addInput(Sha256Hash.of(new ECKey().getPubKey()), 0, ScriptBuilder.createEmpty());
        tx.addOutput(value, address);
        return tx;
    }
}
