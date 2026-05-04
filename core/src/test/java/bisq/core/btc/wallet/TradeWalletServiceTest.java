package bisq.core.btc.wallet;

import bisq.core.btc.exceptions.TransactionVerificationException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.RegTestParams;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TradeWalletServiceTest {
    private static final NetworkParameters PARAMS = RegTestParams.get();

    @Test
    void preparedDepositTxOutputsAcceptNoMakerChange() {
        Transaction makersDepositTx = txWithOutputs(100_000);

        assertDoesNotThrow(() -> TradeWalletService.checkPreparedDepositTxOutputs(
                makersDepositTx,
                Coin.ZERO));
    }

    @Test
    void preparedDepositTxOutputsRejectChangeOutputWhenNoMakerChangeExpected() {
        Transaction makersDepositTx = txWithOutputs(100_000, 0);

        assertThrows(TransactionVerificationException.class,
                () -> TradeWalletService.checkPreparedDepositTxOutputs(
                        makersDepositTx,
                        Coin.ZERO));
    }

    @Test
    void preparedDepositTxOutputsAcceptExpectedMakerChange() {
        Transaction makersDepositTx = txWithOutputs(100_000, 5_000);

        assertDoesNotThrow(() -> TradeWalletService.checkPreparedDepositTxOutputs(
                makersDepositTx,
                Coin.valueOf(5_000)));
    }

    @Test
    void preparedDepositTxOutputsRejectMissingExpectedMakerChangeOutput() {
        Transaction makersDepositTx = txWithOutputs(100_000);

        assertThrows(TransactionVerificationException.class,
                () -> TradeWalletService.checkPreparedDepositTxOutputs(
                        makersDepositTx,
                        Coin.valueOf(5_000)));
    }

    @Test
    void preparedDepositTxOutputsRejectUnexpectedMakerChangeValue() {
        Transaction makersDepositTx = txWithOutputs(100_000, 10_000);

        assertThrows(TransactionVerificationException.class,
                () -> TradeWalletService.checkPreparedDepositTxOutputs(
                        makersDepositTx,
                        Coin.valueOf(5_000)));
    }

    @Test
    void preparedDepositTxOutputsRejectMoreThanOneExtraOutput() {
        Transaction makersDepositTx = txWithOutputs(100_000, 2_000, 3_000);

        assertThrows(TransactionVerificationException.class,
                () -> TradeWalletService.checkPreparedDepositTxOutputs(
                        makersDepositTx,
                        Coin.valueOf(5_000)));
    }

    @Test
    void preparedDepositTxOutputsRejectNegativeExpectedMakerChange() {
        Transaction makersDepositTx = txWithOutputs(100_000);

        assertThrows(IllegalArgumentException.class,
                () -> TradeWalletService.checkPreparedDepositTxOutputs(
                        makersDepositTx,
                        Coin.valueOf(-1)));
    }

    private static Transaction txWithOutputs(long... outputValues) {
        Transaction tx = new Transaction(PARAMS);
        for (long outputValue : outputValues) {
            tx.addOutput(Coin.valueOf(outputValue), SegwitAddress.fromKey(PARAMS, new ECKey()));
        }
        return tx;
    }
}
