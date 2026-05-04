package bisq.core.trade.protocol.bisq_v1.tasks.buyer_as_taker;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.model.RawTransactionInput;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.RegTestParams;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BuyerAsTakerSignsDepositTxTest {
    private static final NetworkParameters PARAMS = RegTestParams.get();
    private static final Coin SELLER_SECURITY_DEPOSIT = Coin.valueOf(20_000);
    private static final Coin OFFER_MIN_AMOUNT = Coin.valueOf(10_000);
    private static final Coin OFFER_AMOUNT = Coin.valueOf(150_000);
    private static final Coin TRADE_AMOUNT = Coin.valueOf(100_000);

    @Test
    void preparedDepositTxFromSellerAsMakerAcceptsNoMakerChange() {
        Transaction makersDepositTx = txWithOutputs(140_000);

        assertDoesNotThrow(() -> BuyerAsTakerSignsDepositTx.verifyPreparedDepositTxFromSellerAsMaker(
                makersDepositTx,
                SELLER_SECURITY_DEPOSIT,
                OFFER_MIN_AMOUNT,
                OFFER_AMOUNT,
                TRADE_AMOUNT,
                sellerInputs(120_000)));
    }

    @Test
    void preparedDepositTxFromSellerAsMakerAcceptsExpectedMakerChange() {
        Transaction makersDepositTx = txWithOutputs(140_000, 5_000);

        assertDoesNotThrow(() -> BuyerAsTakerSignsDepositTx.verifyPreparedDepositTxFromSellerAsMaker(
                makersDepositTx,
                SELLER_SECURITY_DEPOSIT,
                OFFER_MIN_AMOUNT,
                OFFER_AMOUNT,
                TRADE_AMOUNT,
                sellerInputs(125_000)));
    }

    @Test
    void preparedDepositTxFromSellerAsMakerRejectsMissingExpectedMakerChange() {
        Transaction makersDepositTx = txWithOutputs(140_000);

        assertThrows(TransactionVerificationException.class,
                () -> BuyerAsTakerSignsDepositTx.verifyPreparedDepositTxFromSellerAsMaker(
                        makersDepositTx,
                        SELLER_SECURITY_DEPOSIT,
                        OFFER_MIN_AMOUNT,
                        OFFER_AMOUNT,
                        TRADE_AMOUNT,
                        sellerInputs(125_000)));
    }

    @Test
    void preparedDepositTxFromSellerAsMakerRejectsChangeOutputWhenNoMakerChangeExpected() {
        Transaction makersDepositTx = txWithOutputs(140_000, 0);

        assertThrows(TransactionVerificationException.class,
                () -> BuyerAsTakerSignsDepositTx.verifyPreparedDepositTxFromSellerAsMaker(
                        makersDepositTx,
                        SELLER_SECURITY_DEPOSIT,
                        OFFER_MIN_AMOUNT,
                        OFFER_AMOUNT,
                        TRADE_AMOUNT,
                        sellerInputs(120_000)));
    }

    @Test
    void preparedDepositTxFromSellerAsMakerRejectsUnexpectedMakerChangeValue() {
        Transaction makersDepositTx = txWithOutputs(140_000, 10_000);

        assertThrows(TransactionVerificationException.class,
                () -> BuyerAsTakerSignsDepositTx.verifyPreparedDepositTxFromSellerAsMaker(
                        makersDepositTx,
                        SELLER_SECURITY_DEPOSIT,
                        OFFER_MIN_AMOUNT,
                        OFFER_AMOUNT,
                        TRADE_AMOUNT,
                        sellerInputs(125_000)));
    }

    @Test
    void preparedDepositTxFromSellerAsMakerRejectsMoreThanOneExtraOutput() {
        Transaction makersDepositTx = txWithOutputs(140_000, 5_000, 1_000);

        assertThrows(TransactionVerificationException.class,
                () -> BuyerAsTakerSignsDepositTx.verifyPreparedDepositTxFromSellerAsMaker(
                        makersDepositTx,
                        SELLER_SECURITY_DEPOSIT,
                        OFFER_MIN_AMOUNT,
                        OFFER_AMOUNT,
                        TRADE_AMOUNT,
                        sellerInputs(125_000)));
    }

    @Test
    void preparedDepositTxFromSellerAsMakerRejectsNegativeExpectedMakerChange() {
        Transaction makersDepositTx = txWithOutputs(140_000);

        assertThrows(IllegalArgumentException.class,
                () -> BuyerAsTakerSignsDepositTx.verifyPreparedDepositTxFromSellerAsMaker(
                        makersDepositTx,
                        SELLER_SECURITY_DEPOSIT,
                        OFFER_MIN_AMOUNT,
                        OFFER_AMOUNT,
                        TRADE_AMOUNT,
                        sellerInputs(119_999)));
    }

    @Test
    void preparedDepositTxFromSellerAsMakerRejectsChangeGreaterThanRemainingOfferAmount() {
        Transaction makersDepositTx = txWithOutputs(140_000, 60_000);

        assertThrows(IllegalArgumentException.class,
                () -> BuyerAsTakerSignsDepositTx.verifyPreparedDepositTxFromSellerAsMaker(
                        makersDepositTx,
                        SELLER_SECURITY_DEPOSIT,
                        OFFER_MIN_AMOUNT,
                        OFFER_AMOUNT,
                        TRADE_AMOUNT,
                        sellerInputs(180_000)));
    }

    @Test
    void preparedDepositTxFromSellerAsMakerRejectsTradeAmountBelowOfferMinimum() {
        Transaction makersDepositTx = txWithOutputs(140_000);

        assertThrows(IllegalArgumentException.class,
                () -> BuyerAsTakerSignsDepositTx.verifyPreparedDepositTxFromSellerAsMaker(
                        makersDepositTx,
                        SELLER_SECURITY_DEPOSIT,
                        OFFER_MIN_AMOUNT,
                        OFFER_AMOUNT,
                        Coin.valueOf(9_999),
                        sellerInputs(120_000)));
    }

    @Test
    void preparedDepositTxFromSellerAsMakerRejectsTradeAmountAboveOfferAmount() {
        Transaction makersDepositTx = txWithOutputs(140_000);

        assertThrows(IllegalArgumentException.class,
                () -> BuyerAsTakerSignsDepositTx.verifyPreparedDepositTxFromSellerAsMaker(
                        makersDepositTx,
                        SELLER_SECURITY_DEPOSIT,
                        OFFER_MIN_AMOUNT,
                        OFFER_AMOUNT,
                        Coin.valueOf(150_001),
                        sellerInputs(170_001)));
    }

    private static List<RawTransactionInput> sellerInputs(long value) {
        return Collections.singletonList(new RawTransactionInput(0, new byte[]{}, value));
    }

    private static Transaction txWithOutputs(long... outputValues) {
        Transaction tx = new Transaction(PARAMS);
        for (long outputValue : outputValues) {
            tx.addOutput(Coin.valueOf(outputValue), SegwitAddress.fromKey(PARAMS, new ECKey()));
        }
        return tx;
    }
}
