package bisq.core.trade.protocol.bisq_v1.tasks.seller_as_taker;

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

public class SellerAsTakerSignsDepositTxTest {
    private static final NetworkParameters PARAMS = RegTestParams.get();

    @Test
    void preparedDepositTxFromBuyerAsMakerAcceptsNoMakerChange() {
        Transaction makersDepositTx = txWithOutputs(140_000);

        assertDoesNotThrow(() -> SellerAsTakerSignsDepositTx.verifyPreparedDepositTxFromBuyerAsMaker(makersDepositTx));
    }

    @Test
    void preparedDepositTxFromBuyerAsMakerRejectsMakerChangeOutput() {
        Transaction makersDepositTx = txWithOutputs(140_000, 5_000);

        assertThrows(TransactionVerificationException.class,
                () -> SellerAsTakerSignsDepositTx.verifyPreparedDepositTxFromBuyerAsMaker(makersDepositTx));
    }

    @Test
    void preparedDepositTxFromBuyerAsMakerRejectsZeroValuedSecondOutput() {
        Transaction makersDepositTx = txWithOutputs(140_000, 0);

        assertThrows(TransactionVerificationException.class,
                () -> SellerAsTakerSignsDepositTx.verifyPreparedDepositTxFromBuyerAsMaker(makersDepositTx));
    }

    @Test
    void preparedDepositTxFromBuyerAsMakerRejectsMoreThanOneExtraOutput() {
        Transaction makersDepositTx = txWithOutputs(140_000, 5_000, 1_000);

        assertThrows(TransactionVerificationException.class,
                () -> SellerAsTakerSignsDepositTx.verifyPreparedDepositTxFromBuyerAsMaker(makersDepositTx));
    }

    private static Transaction txWithOutputs(long... outputValues) {
        Transaction tx = new Transaction(PARAMS);
        for (long outputValue : outputValues) {
            tx.addOutput(Coin.valueOf(outputValue), SegwitAddress.fromKey(PARAMS, new ECKey()));
        }
        return tx;
    }
}
