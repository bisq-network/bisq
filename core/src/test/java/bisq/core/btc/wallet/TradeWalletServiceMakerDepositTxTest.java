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

import bisq.core.btc.model.PreparedDepositTxAndMakerInputs;
import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.user.Preferences;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletTransaction;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeWalletServiceMakerDepositTxTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();
    private static final Coin BUYER_SECURITY_DEPOSIT = Coin.valueOf(60_000);
    private static final Coin SELLER_SECURITY_DEPOSIT = Coin.valueOf(60_000);
    private static final Coin OFFER_AMOUNT = Coin.valueOf(300_000);
    private static final Coin TRADE_AMOUNT = Coin.valueOf(200_000);
    private static final Coin TRADE_TX_FEE = Coin.valueOf(2_700);
    private static final Coin OTHER_COINS = Coin.valueOf(5_321_500);

    private final ECKey reservedKey = new ECKey();
    private final Address reservedAddress = SegwitAddress.fromKey(PARAMS, reservedKey);
    private final Address changeAddress = SegwitAddress.fromKey(PARAMS, new ECKey());
    private final byte[] buyerPubKey = new ECKey().getPubKey();
    private final byte[] sellerPubKey = new ECKey().getPubKey();
    private Wallet wallet;
    private TradeWalletService tradeWalletService;

    @BeforeEach
    void setUp() {
        // Wallet.createBasic needs a context of PARAMS on the thread, and another test may leave a RegTest one
        Context.propagate(new Context(PARAMS));
        wallet = Wallet.createBasic(PARAMS);
        wallet.importKey(reservedKey);
        WalletsSetup walletsSetup = mock(WalletsSetup.class);
        when(walletsSetup.getBtcWallet()).thenReturn(wallet);
        tradeWalletService = new TradeWalletService(walletsSetup, mock(Preferences.class));
        ArgumentCaptor<Runnable> setupCompletedHandler = ArgumentCaptor.forClass(Runnable.class);
        verify(walletsSetup).addSetupCompletedHandler(setupCompletedHandler.capture());
        setupCompletedHandler.getValue().run();
    }

    @Test
    void buyerAsMakerSpendsOnlyTheReservedOutputOfTheMakerFeeTx() throws Exception {
        Transaction makerFeeTx = makerFeeTx(BUYER_SECURITY_DEPOSIT);
        // Older and larger coins on the reserved address have more coin age, so a plain coin selection prefers them
        addToWallet(tx(OTHER_COINS, reservedAddress), 1_000);

        PreparedDepositTxAndMakerInputs result = buyerAsMakerCreatesDepositTx(makerFeeTx);

        assertSpendsOnlyReservedOutput(result, makerFeeTx);
        // The reserved output is exactly the buyer's deposit, so there is no change
        assertEquals(1, new Transaction(PARAMS, result.depositTransaction).getOutputs().size());
    }

    @Test
    void sellerAsMakerSpendsOnlyTheReservedOutputOfTheMakerFeeTx() throws Exception {
        Transaction makerFeeTx = makerFeeTx(SELLER_SECURITY_DEPOSIT.add(OFFER_AMOUNT));
        addToWallet(tx(OTHER_COINS, reservedAddress), 1_000);
        Coin makerInputAmount = SELLER_SECURITY_DEPOSIT.add(TRADE_AMOUNT);

        PreparedDepositTxAndMakerInputs result = tradeWalletService.sellerAsMakerCreatesDepositTx(makerFeeTx,
                makerInputAmount,
                makerInputAmount.add(TRADE_TX_FEE).add(BUYER_SECURITY_DEPOSIT),
                takerInputs(BUYER_SECURITY_DEPOSIT.add(TRADE_TX_FEE.multiply(2))),
                reservedAddress,
                changeAddress,
                buyerPubKey,
                sellerPubKey);

        assertSpendsOnlyReservedOutput(result, makerFeeTx);
        // The part of the offer amount which is not traded goes back to the maker
        TransactionOutput change = new Transaction(PARAMS, result.depositTransaction).getOutput(1);
        assertEquals(OFFER_AMOUNT.subtract(TRADE_AMOUNT), change.getValue());
        assertEquals(changeAddress, change.getScriptPubKey().getToAddress(PARAMS));
    }

    @Test
    void makerDoesNotUseOtherCoinsWhenTheReservedOutputIsSpent() {
        Transaction makerFeeTx = makerFeeTx(BUYER_SECURITY_DEPOSIT);
        addToWallet(tx(OTHER_COINS, reservedAddress), 1_000);
        TransactionOutput reservedOutput = makerFeeTx.getOutput(1);
        reservedOutput.markAsSpent(new Transaction(PARAMS).addInput(reservedOutput));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> buyerAsMakerCreatesDepositTx(makerFeeTx));

        assertEquals(String.format("The maker fee tx %s must have exactly 1 unspent output to the maker's address %s",
                makerFeeTx.getTxId(), reservedAddress), exception.getMessage());
    }

    private PreparedDepositTxAndMakerInputs buyerAsMakerCreatesDepositTx(Transaction makerFeeTx) throws Exception {
        return tradeWalletService.buyerAsMakerCreatesAndSignsDepositTx(makerFeeTx,
                BUYER_SECURITY_DEPOSIT,
                BUYER_SECURITY_DEPOSIT.add(TRADE_TX_FEE).add(SELLER_SECURITY_DEPOSIT).add(TRADE_AMOUNT),
                takerInputs(SELLER_SECURITY_DEPOSIT.add(TRADE_AMOUNT).add(TRADE_TX_FEE.multiply(2))),
                reservedAddress,
                changeAddress,
                buyerPubKey,
                sellerPubKey);
    }

    private void assertSpendsOnlyReservedOutput(PreparedDepositTxAndMakerInputs result, Transaction makerFeeTx) {
        assertEquals(1, result.rawMakerInputs.size());
        RawTransactionInput makerInput = result.rawMakerInputs.get(0);
        assertEquals(makerFeeTx.getTxId(), new Transaction(PARAMS, makerInput.parentTransaction).getTxId());
        assertEquals(1, makerInput.index);
        assertEquals(makerFeeTx.getOutput(1).getValue().value, makerInput.value);
    }

    // Like CreateMakerFeeTx, the maker fee tx pays the trade fee, the reserved funds and the change. The change goes to
    // our wallet as well, so only the address tells the reserved output apart.
    private Transaction makerFeeTx(Coin reservedFunds) {
        ECKey changeKey = new ECKey();
        wallet.importKey(changeKey);
        Transaction makerFeeTx = tx(Coin.valueOf(5_000), SegwitAddress.fromKey(PARAMS, new ECKey()));
        makerFeeTx.addOutput(reservedFunds, reservedAddress);
        makerFeeTx.addOutput(Coin.valueOf(100_000), SegwitAddress.fromKey(PARAMS, changeKey));
        return addToWallet(makerFeeTx, 6);
    }

    // The confidence is looked up by the tx id, so we set it only after the tx is complete
    private Transaction addToWallet(Transaction tx, int depthInBlocks) {
        tx.getConfidence().setAppearedAtChainHeight(100);
        tx.getConfidence().setDepthInBlocks(depthInBlocks);
        wallet.addWalletTransaction(new WalletTransaction(WalletTransaction.Pool.UNSPENT, tx));
        return tx;
    }

    private static Transaction tx(Coin value, Address address) {
        Transaction tx = new Transaction(PARAMS);
        tx.addInput(Sha256Hash.of(new ECKey().getPubKey()), 0, ScriptBuilder.createEmpty());
        tx.addOutput(value, address);
        return tx;
    }

    private static List<RawTransactionInput> takerInputs(Coin value) {
        Transaction parentTx = tx(value, SegwitAddress.fromKey(PARAMS, new ECKey()));
        return List.of(new RawTransactionInput(new Transaction(PARAMS).addInput(parentTx.getOutput(0))));
    }
}
