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

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.model.PreparedDepositTxAndMakerInputs;
import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.utils.DepositTransactionUtils;
import bisq.core.user.Preferences;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletTransaction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The maker creates the deposit tx with its wallet and the taker signs it with another wallet, as in a trade.
 */
class TradeWalletServiceTakerDepositTxTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();
    private static final Coin BUYER_SECURITY_DEPOSIT = Coin.valueOf(60_000);
    private static final Coin SELLER_SECURITY_DEPOSIT = Coin.valueOf(60_000);
    private static final Coin OFFER_AMOUNT = Coin.valueOf(300_000);
    private static final Coin TRADE_AMOUNT = Coin.valueOf(200_000);
    private static final Coin TRADE_TX_FEE = Coin.valueOf(2_700);
    private static final Coin MS_OUTPUT_AMOUNT = BUYER_SECURITY_DEPOSIT.add(SELLER_SECURITY_DEPOSIT)
            .add(TRADE_AMOUNT).add(TRADE_TX_FEE);
    // The taker pays the miner fee of the deposit tx and of the payout tx
    private static final Coin SELLER_AS_TAKER_INPUT_AMOUNT = SELLER_SECURITY_DEPOSIT.add(TRADE_AMOUNT)
            .add(TRADE_TX_FEE.multiply(2));
    private static final Coin BUYER_AS_TAKER_INPUT_AMOUNT = BUYER_SECURITY_DEPOSIT.add(TRADE_TX_FEE.multiply(2));

    // What a maker can change in its prepared deposit tx without changing an input outpoint or an output. The taker
    // builds the tx it signs from the raw inputs and the outputs, so its tx does not get any of these changes.
    private enum OtherTxId {
        SEQUENCE_OF_FIRST_INPUT(tx -> tx.getInput(0).setSequenceNumber(TransactionInput.NO_SEQUENCE - 1)),
        SEQUENCE_OF_LAST_INPUT(tx -> tx.getInput(tx.getInputs().size() - 1)
                .setSequenceNumber(TransactionInput.NO_SEQUENCE - 1)),
        VERSION(tx -> tx.setVersion(2)),
        LOCK_TIME(tx -> tx.setLockTime(1));

        private final Consumer<Transaction> change;

        OtherTxId(Consumer<Transaction> change) {
            this.change = change;
        }
    }

    private final Address makerChangeAddress = SegwitAddress.fromKey(PARAMS, new ECKey());
    private final byte[] buyerPubKey = new ECKey().getPubKey();
    private final byte[] sellerPubKey = new ECKey().getPubKey();
    private Wallet makerWallet;
    private Wallet takerWallet;
    private Address makerAddress;
    private TradeWalletService makerTradeWalletService;
    private TradeWalletService takerTradeWalletService;

    @BeforeEach
    void setUp() {
        // Wallet creation needs a context of PARAMS on the thread, and another test may leave a RegTest one
        Context.propagate(new Context(PARAMS));
        makerWallet = Wallet.createDeterministic(PARAMS, Script.ScriptType.P2WPKH);
        takerWallet = Wallet.createDeterministic(PARAMS, Script.ScriptType.P2WPKH);
        makerAddress = SegwitAddress.fromKey(PARAMS, makerWallet.freshReceiveKey());
        makerTradeWalletService = tradeWalletService(makerWallet);
        takerTradeWalletService = tradeWalletService(takerWallet);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void sellerAsTakerSignsThePreparedDepositTxOfTheBuyerAsMaker(int numTakerInputs) throws Exception {
        List<RawTransactionInput> sellerInputs = takerInputs(SELLER_AS_TAKER_INPUT_AMOUNT, numTakerInputs);
        PreparedDepositTxAndMakerInputs makersResult = buyerAsMakerCreatesDepositTx(sellerInputs);
        List<RawTransactionInput> buyerInputs = makersResult.rawMakerInputs;
        Transaction preparedDepositTx = preparedDepositTxAsSentToTaker(makersResult);

        Transaction depositTx = sellerAsTakerSignsDepositTx(preparedDepositTx, buyerInputs, sellerInputs);

        assertEquals(preparedDepositTx.getTxId(), depositTx.getTxId());
        assertEquals(buyerInputs.size() + numTakerInputs, depositTx.getInputs().size());
        for (int i = 0; i < depositTx.getInputs().size(); i++) {
            assertEquals(i >= buyerInputs.size(), depositTx.getInput(i).hasWitness());
        }
        // As the seller does once it got the delayed payout tx signature of the buyer. It fails if a signature of the
        // buyer or of the seller is not valid for the deposit tx.
        Transaction buyersDepositTxWithWitnesses = new Transaction(PARAMS, makersResult.depositTransaction);
        takerTradeWalletService.sellerAddsBuyerWitnessesToDepositTx(depositTx,
                buyersDepositTxWithWitnesses,
                buyerInputs,
                sellerInputs);
        assertEquals(preparedDepositTx.getTxId(), depositTx.getTxId());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void buyerAsTakerSignsThePreparedDepositTxOfTheSellerAsMaker(int numTakerInputs) throws Exception {
        List<RawTransactionInput> buyerInputs = takerInputs(BUYER_AS_TAKER_INPUT_AMOUNT, numTakerInputs);
        PreparedDepositTxAndMakerInputs makersResult = sellerAsMakerCreatesDepositTx(buyerInputs);
        List<RawTransactionInput> sellerInputs = makersResult.rawMakerInputs;
        Transaction preparedDepositTx = preparedDepositTxAsSentToTaker(makersResult);

        Transaction depositTx = buyerAsTakerSignsDepositTx(preparedDepositTx, buyerInputs, sellerInputs);

        assertEquals(preparedDepositTx.getTxId(), depositTx.getTxId());
        assertEquals(numTakerInputs + sellerInputs.size(), depositTx.getInputs().size());
        for (int i = 0; i < depositTx.getInputs().size(); i++) {
            assertEquals(i < numTakerInputs, depositTx.getInput(i).hasWitness());
        }
        // As the seller does once it got the delayed payout tx signature of the buyer. It fails if a signature of the
        // buyer or of the seller is not valid for the deposit tx.
        Transaction sellersDepositTx = new Transaction(PARAMS, makersResult.depositTransaction);
        makerTradeWalletService.sellerAddsBuyerWitnessesToDepositTx(sellersDepositTx,
                depositTx,
                buyerInputs,
                sellerInputs);
        assertEquals(preparedDepositTx.getTxId(), sellersDepositTx.getTxId());
    }

    // A maker up to v1.10.11 could fund the deposit tx from several outputs to its reserved address
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void takerSignsAPreparedDepositTxWithSeveralMakerInputs(boolean takerIsSeller) throws Exception {
        List<RawTransactionInput> makerInputs = List.of(rawInput(Coin.valueOf(100_000), makerAddress),
                rawInput(Coin.valueOf(200_000), makerAddress));
        List<RawTransactionInput> takerInputs = takerInputs(Coin.valueOf(100_000), 1);
        List<RawTransactionInput> buyerInputs = takerIsSeller ? makerInputs : takerInputs;
        List<RawTransactionInput> sellerInputs = takerIsSeller ? takerInputs : makerInputs;
        Transaction makersDepositTx = new Transaction(PARAMS);
        Stream.concat(buyerInputs.stream(), sellerInputs.stream())
                .forEach(rawInput -> makersDepositTx.addInput(new TransactionInput(PARAMS,
                        makersDepositTx,
                        new byte[]{},
                        WalletUtils.getConnectedOutPoint(rawInput, PARAMS),
                        Coin.valueOf(rawInput.value))));
        makersDepositTx.addOutput(MS_OUTPUT_AMOUNT,
                DepositTransactionUtils.get2of2MultiSigOutputScript(buyerPubKey, sellerPubKey));
        Transaction preparedDepositTx = new Transaction(PARAMS, makersDepositTx.bitcoinSerialize(false));

        Transaction depositTx = takerTradeWalletService.takerSignsDepositTx(takerIsSeller,
                preparedDepositTx,
                MS_OUTPUT_AMOUNT,
                buyerInputs,
                sellerInputs,
                buyerPubKey,
                sellerPubKey);

        assertEquals(preparedDepositTx.getTxId(), depositTx.getTxId());
        int takerInputIndex = takerIsSeller ? 2 : 0;
        for (int i = 0; i < 3; i++) {
            assertEquals(i == takerInputIndex, depositTx.getInput(i).hasWitness());
        }
    }

    @ParameterizedTest
    @EnumSource(OtherTxId.class)
    void sellerAsTakerDoesNotSignATxWithAnotherTxIdThanThePreparedDepositTx(OtherTxId otherTxId) throws Exception {
        List<RawTransactionInput> sellerInputs = takerInputs(SELLER_AS_TAKER_INPUT_AMOUNT, 1);
        PreparedDepositTxAndMakerInputs makersResult = buyerAsMakerCreatesDepositTx(sellerInputs);
        Transaction preparedDepositTx = preparedDepositTxWithOtherTxId(makersResult, otherTxId);

        TransactionVerificationException exception = assertThrows(TransactionVerificationException.class,
                () -> sellerAsTakerSignsDepositTx(preparedDepositTx, makersResult.rawMakerInputs, sellerInputs));

        assertTrue(exception.getMessage().contains(preparedDepositTx.getTxId().toString()), exception.getMessage());
    }

    @ParameterizedTest
    @EnumSource(OtherTxId.class)
    void buyerAsTakerDoesNotSignATxWithAnotherTxIdThanThePreparedDepositTx(OtherTxId otherTxId) throws Exception {
        List<RawTransactionInput> buyerInputs = takerInputs(BUYER_AS_TAKER_INPUT_AMOUNT, 1);
        PreparedDepositTxAndMakerInputs makersResult = sellerAsMakerCreatesDepositTx(buyerInputs);
        Transaction preparedDepositTx = preparedDepositTxWithOtherTxId(makersResult, otherTxId);

        TransactionVerificationException exception = assertThrows(TransactionVerificationException.class,
                () -> buyerAsTakerSignsDepositTx(preparedDepositTx, buyerInputs, makersResult.rawMakerInputs));

        assertTrue(exception.getMessage().contains(preparedDepositTx.getTxId().toString()), exception.getMessage());
    }

    private Transaction sellerAsTakerSignsDepositTx(Transaction preparedDepositTx,
                                                    List<RawTransactionInput> buyerInputs,
                                                    List<RawTransactionInput> sellerInputs) throws Exception {
        return takerTradeWalletService.takerSignsDepositTx(true,
                preparedDepositTx,
                MS_OUTPUT_AMOUNT,
                buyerInputs,
                sellerInputs,
                buyerPubKey,
                sellerPubKey);
    }

    private Transaction buyerAsTakerSignsDepositTx(Transaction preparedDepositTx,
                                                   List<RawTransactionInput> buyerInputs,
                                                   List<RawTransactionInput> sellerInputs) throws Exception {
        return takerTradeWalletService.takerSignsDepositTx(false,
                preparedDepositTx,
                MS_OUTPUT_AMOUNT,
                buyerInputs,
                sellerInputs,
                buyerPubKey,
                sellerPubKey);
    }

    private PreparedDepositTxAndMakerInputs buyerAsMakerCreatesDepositTx(List<RawTransactionInput> takerInputs)
            throws Exception {
        return makerTradeWalletService.buyerAsMakerCreatesAndSignsDepositTx(makerFeeTx(BUYER_SECURITY_DEPOSIT),
                BUYER_SECURITY_DEPOSIT,
                MS_OUTPUT_AMOUNT,
                takerInputs,
                makerAddress,
                makerChangeAddress,
                buyerPubKey,
                sellerPubKey);
    }

    private PreparedDepositTxAndMakerInputs sellerAsMakerCreatesDepositTx(List<RawTransactionInput> takerInputs)
            throws Exception {
        // The part of the offer amount which is not traded goes back to the maker as change
        Transaction makerFeeTx = makerFeeTx(SELLER_SECURITY_DEPOSIT.add(OFFER_AMOUNT));
        return makerTradeWalletService.sellerAsMakerCreatesDepositTx(makerFeeTx,
                SELLER_SECURITY_DEPOSIT.add(TRADE_AMOUNT),
                MS_OUTPUT_AMOUNT,
                takerInputs,
                makerAddress,
                makerChangeAddress,
                buyerPubKey,
                sellerPubKey);
    }

    // The maker sends its prepared deposit tx without its signatures, see BuyerAsMakerSendsInputsForDepositTxResponse
    // and SellerAsMakerSendsInputsForDepositTxResponse. The taker parses the received bytes.
    private static Transaction preparedDepositTxAsSentToTaker(PreparedDepositTxAndMakerInputs makersResult) {
        Transaction preparedDepositTx = new Transaction(PARAMS, makersResult.depositTransaction);
        preparedDepositTx.getInputs().forEach(input -> input.setScriptSig(new Script(new byte[]{})));
        return new Transaction(PARAMS, preparedDepositTx.bitcoinSerialize(false));
    }

    private static Transaction preparedDepositTxWithOtherTxId(PreparedDepositTxAndMakerInputs makersResult,
                                                              OtherTxId otherTxId) {
        Transaction honestPreparedDepositTx = preparedDepositTxAsSentToTaker(makersResult);
        Transaction preparedDepositTx = preparedDepositTxAsSentToTaker(makersResult);
        otherTxId.change.accept(preparedDepositTx);
        preparedDepositTx = new Transaction(PARAMS, preparedDepositTx.bitcoinSerialize(false));
        assertNotEquals(honestPreparedDepositTx.getTxId(), preparedDepositTx.getTxId());
        assertFalse(preparedDepositTx.hasWitnesses());
        return preparedDepositTx;
    }

    // Like CreateMakerFeeTx, the maker fee tx pays the trade fee, the reserved funds and the change
    private Transaction makerFeeTx(Coin reservedFunds) {
        Transaction makerFeeTx = tx(Coin.valueOf(5_000), SegwitAddress.fromKey(PARAMS, new ECKey()));
        makerFeeTx.addOutput(reservedFunds, makerAddress);
        makerFeeTx.addOutput(Coin.valueOf(100_000), makerWallet.freshReceiveAddress());
        makerFeeTx.getConfidence().setAppearedAtChainHeight(100);
        makerFeeTx.getConfidence().setDepthInBlocks(6);
        makerFeeTx.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING);
        makerWallet.addWalletTransaction(new WalletTransaction(WalletTransaction.Pool.UNSPENT, makerFeeTx));
        return makerFeeTx;
    }

    // The taker funds the deposit tx with outputs to keys of its wallet. A trade has one taker input, the reserved
    // output of the taker fee tx, but the protocol carries a list.
    private List<RawTransactionInput> takerInputs(Coin totalValue, int numInputs) {
        List<RawTransactionInput> takerInputs = new ArrayList<>();
        Coin remaining = totalValue;
        for (int i = 0; i < numInputs; i++) {
            Coin value = i == numInputs - 1 ? remaining : totalValue.divide(numInputs);
            remaining = remaining.subtract(value);
            takerInputs.add(rawInput(value, SegwitAddress.fromKey(PARAMS, takerWallet.freshReceiveKey())));
        }
        return takerInputs;
    }

    private static RawTransactionInput rawInput(Coin value, Address address) {
        return new RawTransactionInput(new Transaction(PARAMS).addInput(tx(value, address).getOutput(0)));
    }

    private static Transaction tx(Coin value, Address address) {
        Transaction tx = new Transaction(PARAMS);
        tx.addInput(Sha256Hash.of(new ECKey().getPubKey()), 0, ScriptBuilder.createEmpty());
        tx.addOutput(value, address);
        return tx;
    }

    private static TradeWalletService tradeWalletService(Wallet wallet) {
        WalletsSetup walletsSetup = mock(WalletsSetup.class);
        when(walletsSetup.getBtcWallet()).thenReturn(wallet);
        TradeWalletService tradeWalletService = new TradeWalletService(walletsSetup, mock(Preferences.class));
        ArgumentCaptor<Runnable> setupCompletedHandler = ArgumentCaptor.forClass(Runnable.class);
        verify(walletsSetup).addSetupCompletedHandler(setupCompletedHandler.capture());
        setupCompletedHandler.getValue().run();
        return tradeWalletService;
    }
}
