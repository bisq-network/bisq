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

package bisq.core.trade.validation;

import bisq.core.btc.wallet.BtcWalletService;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PayoutTxValidationTest {
    private static final NetworkParameters PARAMS = RegTestParams.get();
    private static final Coin DEPOSIT_OUTPUT_AMOUNT = Coin.valueOf(120_000);
    private static final Coin BUYER_PAYOUT_AMOUNT = Coin.valueOf(80_000);
    private static final Coin SELLER_PAYOUT_AMOUNT = Coin.valueOf(30_000);
    private static final ECKey BUYER_MULTI_SIG_KEY = new ECKey();
    private static final ECKey SELLER_MULTI_SIG_KEY = new ECKey();
    private static final ECKey BUYER_PAYOUT_KEY = new ECKey();
    private static final ECKey SELLER_PAYOUT_KEY = new ECKey();
    private static final Script REDEEM_SCRIPT = ScriptBuilder.createMultiSigOutputScript(2,
            Arrays.asList(SELLER_MULTI_SIG_KEY, BUYER_MULTI_SIG_KEY));
    private static final String BUYER_PAYOUT_ADDRESS = SegwitAddress.fromKey(PARAMS, BUYER_PAYOUT_KEY).toString();
    private static final String SELLER_PAYOUT_ADDRESS = SegwitAddress.fromKey(PARAMS, SELLER_PAYOUT_KEY).toString();
    private static final String FUNDING_TX_ID = "0000000000000000000000000000000000000000000000000000000000000001";
    private static final String OTHER_FUNDING_TX_ID = "0000000000000000000000000000000000000000000000000000000000000002";

    @Test
    void checkPayoutTxAcceptsExpectedPayoutTx() {
        PayoutFixture fixture = createPayoutFixture(BUYER_PAYOUT_AMOUNT, SELLER_PAYOUT_AMOUNT);

        assertSame(fixture.payoutTx, PayoutTxValidation.checkPayoutTx(fixture.payoutTx,
                fixture.depositTx,
                BUYER_PAYOUT_AMOUNT,
                SELLER_PAYOUT_AMOUNT,
                BUYER_PAYOUT_ADDRESS,
                SELLER_PAYOUT_ADDRESS,
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey(),
                PARAMS));
    }

    @Test
    void checkPayoutTxBytesReturnsSerializedPayoutTxWhenValid() {
        PayoutFixture fixture = createPayoutFixture(BUYER_PAYOUT_AMOUNT, SELLER_PAYOUT_AMOUNT);
        byte[] serializedPayoutTx = fixture.payoutTx.bitcoinSerialize();
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        when(btcWalletService.getParams()).thenReturn(PARAMS);

        assertSame(serializedPayoutTx, PayoutTxValidation.checkPayoutTx(serializedPayoutTx,
                btcWalletService,
                fixture.depositTx,
                BUYER_PAYOUT_AMOUNT,
                SELLER_PAYOUT_AMOUNT,
                BUYER_PAYOUT_ADDRESS,
                SELLER_PAYOUT_ADDRESS,
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey()));
    }

    @Test
    void checkPayoutTxBytesRejectsEmptySerializedPayoutTx() {
        PayoutFixture fixture = createPayoutFixture(BUYER_PAYOUT_AMOUNT, SELLER_PAYOUT_AMOUNT);
        BtcWalletService btcWalletService = mock(BtcWalletService.class);

        assertThrows(IllegalArgumentException.class, () -> PayoutTxValidation.checkPayoutTx(new byte[]{},
                btcWalletService,
                fixture.depositTx,
                BUYER_PAYOUT_AMOUNT,
                SELLER_PAYOUT_AMOUNT,
                BUYER_PAYOUT_ADDRESS,
                SELLER_PAYOUT_ADDRESS,
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey()));
    }

    @Test
    void checkPayoutTxBytesRejectsNullWalletService() {
        PayoutFixture fixture = createPayoutFixture(BUYER_PAYOUT_AMOUNT, SELLER_PAYOUT_AMOUNT);
        byte[] serializedPayoutTx = fixture.payoutTx.bitcoinSerialize();

        assertThrows(NullPointerException.class, () -> PayoutTxValidation.checkPayoutTx(serializedPayoutTx,
                null,
                fixture.depositTx,
                BUYER_PAYOUT_AMOUNT,
                SELLER_PAYOUT_AMOUNT,
                BUYER_PAYOUT_ADDRESS,
                SELLER_PAYOUT_ADDRESS,
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey()));
    }

    @Test
    void checkPayoutTxRejectsInputWhichDoesNotSpendDepositOutput() {
        PayoutFixture fixture = createPayoutFixture(BUYER_PAYOUT_AMOUNT, SELLER_PAYOUT_AMOUNT);
        Transaction otherDepositTx = createDepositTx(OTHER_FUNDING_TX_ID);

        assertThrows(IllegalArgumentException.class, () -> PayoutTxValidation.checkPayoutTx(fixture.payoutTx,
                otherDepositTx,
                BUYER_PAYOUT_AMOUNT,
                SELLER_PAYOUT_AMOUNT,
                BUYER_PAYOUT_ADDRESS,
                SELLER_PAYOUT_ADDRESS,
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey(),
                PARAMS));
    }

    @Test
    void checkPayoutTxRejectsBuyerPayoutAmountMismatch() {
        PayoutFixture fixture = createPayoutFixture(BUYER_PAYOUT_AMOUNT.subtract(Coin.SATOSHI), SELLER_PAYOUT_AMOUNT);

        assertThrows(IllegalArgumentException.class, () -> PayoutTxValidation.checkPayoutTx(fixture.payoutTx,
                fixture.depositTx,
                BUYER_PAYOUT_AMOUNT,
                SELLER_PAYOUT_AMOUNT,
                BUYER_PAYOUT_ADDRESS,
                SELLER_PAYOUT_ADDRESS,
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey(),
                PARAMS));
    }

    @Test
    void checkPayoutTxRejectsBuyerPayoutAddressMismatch() {
        String otherBuyerAddress = SegwitAddress.fromKey(PARAMS, new ECKey()).toString();
        PayoutFixture fixture = createPayoutFixture(BUYER_PAYOUT_AMOUNT,
                SELLER_PAYOUT_AMOUNT,
                otherBuyerAddress,
                SELLER_PAYOUT_ADDRESS);

        assertThrows(IllegalArgumentException.class, () -> PayoutTxValidation.checkPayoutTx(fixture.payoutTx,
                fixture.depositTx,
                BUYER_PAYOUT_AMOUNT,
                SELLER_PAYOUT_AMOUNT,
                BUYER_PAYOUT_ADDRESS,
                SELLER_PAYOUT_ADDRESS,
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey(),
                PARAMS));
    }

    @Test
    void checkPayoutTxRejectsMissingExpectedSellerOutput() {
        PayoutFixture fixture = createPayoutFixture(BUYER_PAYOUT_AMOUNT, Coin.ZERO);

        assertThrows(IllegalArgumentException.class, () -> PayoutTxValidation.checkPayoutTx(fixture.payoutTx,
                fixture.depositTx,
                BUYER_PAYOUT_AMOUNT,
                SELLER_PAYOUT_AMOUNT,
                BUYER_PAYOUT_ADDRESS,
                SELLER_PAYOUT_ADDRESS,
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey(),
                PARAMS));
    }

    @Test
    void checkPayoutTxAcceptsZeroSellerPayout() {
        PayoutFixture fixture = createPayoutFixture(BUYER_PAYOUT_AMOUNT, Coin.ZERO);

        assertSame(fixture.payoutTx, PayoutTxValidation.checkPayoutTx(fixture.payoutTx,
                fixture.depositTx,
                BUYER_PAYOUT_AMOUNT,
                Coin.ZERO,
                BUYER_PAYOUT_ADDRESS,
                SELLER_PAYOUT_ADDRESS,
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey(),
                PARAMS));
    }

    @Test
    void checkPayoutTxRejectsMissingInputWitness() {
        PayoutFixture fixture = createPayoutFixture(BUYER_PAYOUT_AMOUNT, SELLER_PAYOUT_AMOUNT);
        fixture.payoutTx.getInput(0).setWitness(TransactionWitness.EMPTY);

        assertThrows(IllegalArgumentException.class, () -> PayoutTxValidation.checkPayoutTx(fixture.payoutTx,
                fixture.depositTx,
                BUYER_PAYOUT_AMOUNT,
                SELLER_PAYOUT_AMOUNT,
                BUYER_PAYOUT_ADDRESS,
                SELLER_PAYOUT_ADDRESS,
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey(),
                PARAMS));
    }

    @Test
    void checkPayoutTxRejectsNegativeBuyerPayoutAmount() {
        PayoutFixture fixture = createPayoutFixture(BUYER_PAYOUT_AMOUNT, SELLER_PAYOUT_AMOUNT);

        assertThrows(IllegalArgumentException.class, () -> PayoutTxValidation.checkPayoutTx(fixture.payoutTx,
                fixture.depositTx,
                Coin.valueOf(-1),
                SELLER_PAYOUT_AMOUNT,
                BUYER_PAYOUT_ADDRESS,
                SELLER_PAYOUT_ADDRESS,
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey(),
                PARAMS));
    }

    @Test
    void checkPayoutTxRejectsZeroBuyerAndSellerPayoutAmounts() {
        PayoutFixture fixture = createPayoutFixture(BUYER_PAYOUT_AMOUNT, SELLER_PAYOUT_AMOUNT);

        assertThrows(IllegalArgumentException.class, () -> PayoutTxValidation.checkPayoutTx(fixture.payoutTx,
                fixture.depositTx,
                Coin.ZERO,
                Coin.ZERO,
                BUYER_PAYOUT_ADDRESS,
                SELLER_PAYOUT_ADDRESS,
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey(),
                PARAMS));
    }

    @Test
    void checkPayoutTxRejectsOutputSumAboveDepositOutputValue() {
        Coin buyerPayoutAmount = DEPOSIT_OUTPUT_AMOUNT.add(Coin.SATOSHI);
        PayoutFixture fixture = createPayoutFixture(buyerPayoutAmount, Coin.ZERO);

        assertThrows(IllegalArgumentException.class, () -> PayoutTxValidation.checkPayoutTx(fixture.payoutTx,
                fixture.depositTx,
                buyerPayoutAmount,
                Coin.ZERO,
                BUYER_PAYOUT_ADDRESS,
                SELLER_PAYOUT_ADDRESS,
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey(),
                PARAMS));
    }

    private static PayoutFixture createPayoutFixture(Coin buyerPayoutAmount,
                                                     Coin sellerPayoutAmount) {
        return createPayoutFixture(buyerPayoutAmount,
                sellerPayoutAmount,
                BUYER_PAYOUT_ADDRESS,
                SELLER_PAYOUT_ADDRESS);
    }

    private static PayoutFixture createPayoutFixture(Coin buyerPayoutAmount,
                                                     Coin sellerPayoutAmount,
                                                     String buyerPayoutAddress,
                                                     String sellerPayoutAddress) {
        Transaction depositTx = createDepositTx(FUNDING_TX_ID);
        Transaction payoutTx = createSignedPayoutTx(depositTx,
                buyerPayoutAmount,
                sellerPayoutAmount,
                buyerPayoutAddress,
                sellerPayoutAddress);
        return new PayoutFixture(depositTx, payoutTx);
    }

    private static Transaction createDepositTx(String fundingTxId) {
        Transaction depositTx = new Transaction(PARAMS);
        depositTx.addInput(new TransactionInput(PARAMS,
                depositTx,
                new byte[]{},
                new TransactionOutPoint(PARAMS, 0, Sha256Hash.wrap(fundingTxId)),
                DEPOSIT_OUTPUT_AMOUNT));
        depositTx.addOutput(DEPOSIT_OUTPUT_AMOUNT, ScriptBuilder.createP2WSHOutputScript(REDEEM_SCRIPT));
        return depositTx;
    }

    private static Transaction createSignedPayoutTx(Transaction depositTx,
                                                    Coin buyerPayoutAmount,
                                                    Coin sellerPayoutAmount,
                                                    String buyerPayoutAddress,
                                                    String sellerPayoutAddress) {
        Transaction payoutTx = new Transaction(PARAMS);
        payoutTx.addInput(depositTx.getOutput(0));
        if (buyerPayoutAmount.isPositive()) {
            payoutTx.addOutput(buyerPayoutAmount, Address.fromString(PARAMS, buyerPayoutAddress));
        }
        if (sellerPayoutAmount.isPositive()) {
            payoutTx.addOutput(sellerPayoutAmount, Address.fromString(PARAMS, sellerPayoutAddress));
        }

        TransactionInput input = payoutTx.getInput(0);
        Coin inputValue = depositTx.getOutput(0).getValue();
        Sha256Hash sigHash = payoutTx.hashForWitnessSignature(0,
                REDEEM_SCRIPT,
                inputValue,
                Transaction.SigHash.ALL,
                false);
        TransactionSignature buyerSignature = new TransactionSignature(BUYER_MULTI_SIG_KEY.sign(sigHash),
                Transaction.SigHash.ALL,
                false);
        TransactionSignature sellerSignature = new TransactionSignature(SELLER_MULTI_SIG_KEY.sign(sigHash),
                Transaction.SigHash.ALL,
                false);
        input.setScriptSig(ScriptBuilder.createEmpty());
        input.setWitness(TransactionWitness.redeemP2WSH(REDEEM_SCRIPT, sellerSignature, buyerSignature));
        return payoutTx;
    }

    private static final class PayoutFixture {
        private final Transaction depositTx;
        private final Transaction payoutTx;

        private PayoutFixture(Transaction depositTx, Transaction payoutTx) {
            this.depositTx = depositTx;
            this.payoutTx = payoutTx;
        }
    }
}
