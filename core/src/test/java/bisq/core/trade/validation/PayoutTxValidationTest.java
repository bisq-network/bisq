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

import bisq.core.btc.model.AddressEntry;
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
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayoutTxValidationTest {
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

    /* --------------------------------------------------------------------- */
    // Payout address validation
    /* --------------------------------------------------------------------- */

    @Test
    void checkTradePayoutAddressEntryReturnsContractAddressWhenAddressEntryMatches() {
        BtcWalletService btcWalletService = btcWalletServiceWithParams();
        AddressEntry payoutAddressEntry = mock(AddressEntry.class);
        when(btcWalletService.getAddressEntry("trade-id", AddressEntry.Context.TRADE_PAYOUT))
                .thenReturn(Optional.of(payoutAddressEntry));
        when(payoutAddressEntry.getAddressString()).thenReturn(BUYER_PAYOUT_ADDRESS);

        assertEquals(BUYER_PAYOUT_ADDRESS,
                PayoutTxValidation.checkTradePayoutAddressEntry(BUYER_PAYOUT_ADDRESS,
                        btcWalletService,
                        "trade-id",
                        "Buyer"));
    }

    @Test
    void checkTradePayoutAddressEntryAcceptsEquivalentParsedAddress() {
        BtcWalletService btcWalletService = btcWalletServiceWithParams();
        AddressEntry payoutAddressEntry = mock(AddressEntry.class);
        when(btcWalletService.getAddressEntry("trade-id", AddressEntry.Context.TRADE_PAYOUT))
                .thenReturn(Optional.of(payoutAddressEntry));
        when(payoutAddressEntry.getAddressString()).thenReturn(BUYER_PAYOUT_ADDRESS.toUpperCase());

        assertEquals(BUYER_PAYOUT_ADDRESS,
                PayoutTxValidation.checkTradePayoutAddressEntry(BUYER_PAYOUT_ADDRESS,
                        btcWalletService,
                        "trade-id",
                        "Buyer"));
    }

    @Test
    void checkTradePayoutAddressEntryRejectsMissingAddressEntry() {
        BtcWalletService btcWalletService = btcWalletServiceWithParams();
        when(btcWalletService.getAddressEntry("trade-id", AddressEntry.Context.TRADE_PAYOUT))
                .thenReturn(Optional.empty());

        assertThrows(NullPointerException.class,
                () -> PayoutTxValidation.checkTradePayoutAddressEntry(BUYER_PAYOUT_ADDRESS,
                        btcWalletService,
                        "trade-id",
                        "Buyer"));
    }

    @Test
    void checkTradePayoutAddressEntryRejectsAddressEntryMismatch() {
        BtcWalletService btcWalletService = btcWalletServiceWithParams();
        AddressEntry payoutAddressEntry = mock(AddressEntry.class);
        when(btcWalletService.getAddressEntry("trade-id", AddressEntry.Context.TRADE_PAYOUT))
                .thenReturn(Optional.of(payoutAddressEntry));
        when(payoutAddressEntry.getAddressString()).thenReturn(SELLER_PAYOUT_ADDRESS);

        assertThrows(IllegalArgumentException.class,
                () -> PayoutTxValidation.checkTradePayoutAddressEntry(BUYER_PAYOUT_ADDRESS,
                        btcWalletService,
                        "trade-id",
                        "Buyer"));
    }

    @Test
    void checkTradingPeerPayoutAddressReturnsContractAddressWhenPeerAddressMatches() {
        BtcWalletService btcWalletService = btcWalletServiceWithParams();

        assertEquals(SELLER_PAYOUT_ADDRESS,
                PayoutTxValidation.checkTradingPeerPayoutAddress(SELLER_PAYOUT_ADDRESS,
                        SELLER_PAYOUT_ADDRESS,
                        btcWalletService,
                        "trade-id",
                        "Seller"));
    }

    @Test
    void checkTradingPeerPayoutAddressAcceptsEquivalentParsedAddress() {
        BtcWalletService btcWalletService = btcWalletServiceWithParams();

        assertEquals(SELLER_PAYOUT_ADDRESS,
                PayoutTxValidation.checkTradingPeerPayoutAddress(SELLER_PAYOUT_ADDRESS,
                        SELLER_PAYOUT_ADDRESS.toUpperCase(),
                        btcWalletService,
                        "trade-id",
                        "Seller"));
    }

    @Test
    void checkTradingPeerPayoutAddressRejectsPeerAddressMismatch() {
        BtcWalletService btcWalletService = btcWalletServiceWithParams();

        assertThrows(IllegalArgumentException.class,
                () -> PayoutTxValidation.checkTradingPeerPayoutAddress(SELLER_PAYOUT_ADDRESS,
                        BUYER_PAYOUT_ADDRESS,
                        btcWalletService,
                        "trade-id",
                        "Seller"));
    }

    @Test
    void checkTradingPeerPayoutAddressRejectsBlankPeerAddress() {
        BtcWalletService btcWalletService = btcWalletServiceWithParams();

        assertThrows(IllegalArgumentException.class,
                () -> PayoutTxValidation.checkTradingPeerPayoutAddress(SELLER_PAYOUT_ADDRESS,
                        " ",
                        btcWalletService,
                        "trade-id",
                        "Seller"));
    }

    /* --------------------------------------------------------------------- */
    // Valid payout tx
    /* --------------------------------------------------------------------- */

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
        when(btcWalletService.getParams()).thenReturn(PARAMS);

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

    /* --------------------------------------------------------------------- */
    // Input and output validation
    /* --------------------------------------------------------------------- */

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

    /* --------------------------------------------------------------------- */
    // Witness and signature validation
    /* --------------------------------------------------------------------- */

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
    void checkPayoutTxRejectsWrongBuyerMultiSigPubKey() {
        PayoutFixture fixture = createPayoutFixture(BUYER_PAYOUT_AMOUNT, SELLER_PAYOUT_AMOUNT);
        byte[] wrongBuyerMultiSigPubKey = new ECKey().getPubKey();

        assertThrows(IllegalArgumentException.class, () -> PayoutTxValidation.checkPayoutTx(fixture.payoutTx,
                fixture.depositTx,
                BUYER_PAYOUT_AMOUNT,
                SELLER_PAYOUT_AMOUNT,
                BUYER_PAYOUT_ADDRESS,
                SELLER_PAYOUT_ADDRESS,
                wrongBuyerMultiSigPubKey,
                SELLER_MULTI_SIG_KEY.getPubKey(),
                PARAMS));
    }

    @Test
    void checkPayoutTxRejectsSwappedWitnessSignatures() {
        PayoutFixture fixture = createPayoutFixture(BUYER_PAYOUT_AMOUNT, SELLER_PAYOUT_AMOUNT);
        TransactionWitness witness = fixture.payoutTx.getInput(0).getWitness();
        setP2wshWitness(fixture.payoutTx,
                witness.getPush(2),
                witness.getPush(1),
                REDEEM_SCRIPT);

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
    void checkPayoutTxRejectsInvalidWitnessSignature() {
        PayoutFixture fixture = createPayoutFixture(BUYER_PAYOUT_AMOUNT, SELLER_PAYOUT_AMOUNT);
        TransactionSignature invalidSellerSignature = createWitnessSignature(fixture.payoutTx,
                fixture.depositTx,
                new ECKey(),
                Transaction.SigHash.ALL);
        TransactionSignature buyerSignature = createWitnessSignature(fixture.payoutTx,
                fixture.depositTx,
                BUYER_MULTI_SIG_KEY,
                Transaction.SigHash.ALL);
        setP2wshWitness(fixture.payoutTx, invalidSellerSignature, buyerSignature, REDEEM_SCRIPT);

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
    void checkPayoutTxRejectsUnexpectedWitnessRedeemScript() {
        PayoutFixture fixture = createPayoutFixture(BUYER_PAYOUT_AMOUNT, SELLER_PAYOUT_AMOUNT);
        Script unexpectedRedeemScript = ScriptBuilder.createMultiSigOutputScript(2,
                Arrays.asList(new ECKey(), new ECKey()));
        TransactionWitness witness = fixture.payoutTx.getInput(0).getWitness();
        setP2wshWitness(fixture.payoutTx,
                witness.getPush(1),
                witness.getPush(2),
                unexpectedRedeemScript);

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
    void checkPayoutTxRejectsWitnessSignaturesWithoutSigHashAll() {
        PayoutFixture fixture = createPayoutFixture(BUYER_PAYOUT_AMOUNT, SELLER_PAYOUT_AMOUNT);
        TransactionSignature sellerSignature = createWitnessSignature(fixture.payoutTx,
                fixture.depositTx,
                SELLER_MULTI_SIG_KEY,
                Transaction.SigHash.NONE);
        TransactionSignature buyerSignature = createWitnessSignature(fixture.payoutTx,
                fixture.depositTx,
                BUYER_MULTI_SIG_KEY,
                Transaction.SigHash.NONE);
        setP2wshWitness(fixture.payoutTx, sellerSignature, buyerSignature, REDEEM_SCRIPT);

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
    void checkPayoutTxSignatureAcceptsExpectedBuyerSignature() {
        PayoutFixture fixture = createPayoutFixture(BUYER_PAYOUT_AMOUNT, SELLER_PAYOUT_AMOUNT);
        byte[] buyerSignature = createDerSignature(fixture.payoutTx, fixture.depositTx, BUYER_MULTI_SIG_KEY);

        assertSame(buyerSignature, PayoutTxValidation.checkPayoutTxSignature(buyerSignature,
                fixture.depositTx,
                BUYER_PAYOUT_AMOUNT,
                SELLER_PAYOUT_AMOUNT,
                BUYER_PAYOUT_ADDRESS,
                SELLER_PAYOUT_ADDRESS,
                BUYER_MULTI_SIG_KEY.getPubKey(),
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey(),
                PARAMS,
                "buyerSignature"));
    }

    @Test
    void checkPayoutTxSignatureRejectsSignatureFromUnexpectedKey() {
        PayoutFixture fixture = createPayoutFixture(BUYER_PAYOUT_AMOUNT, SELLER_PAYOUT_AMOUNT);
        byte[] wrongSignature = createDerSignature(fixture.payoutTx, fixture.depositTx, new ECKey());

        assertThrows(IllegalArgumentException.class, () -> PayoutTxValidation.checkPayoutTxSignature(wrongSignature,
                fixture.depositTx,
                BUYER_PAYOUT_AMOUNT,
                SELLER_PAYOUT_AMOUNT,
                BUYER_PAYOUT_ADDRESS,
                SELLER_PAYOUT_ADDRESS,
                BUYER_MULTI_SIG_KEY.getPubKey(),
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey(),
                PARAMS,
                "buyerSignature"));
    }

    @Test
    void checkPayoutTxSignatureRejectsSignatureForDifferentPayoutAddress() {
        PayoutFixture fixture = createPayoutFixture(BUYER_PAYOUT_AMOUNT, SELLER_PAYOUT_AMOUNT);
        String otherBuyerAddress = SegwitAddress.fromKey(PARAMS, new ECKey()).toString();
        byte[] buyerSignature = createDerSignature(fixture.payoutTx, fixture.depositTx, BUYER_MULTI_SIG_KEY);

        assertThrows(IllegalArgumentException.class, () -> PayoutTxValidation.checkPayoutTxSignature(buyerSignature,
                fixture.depositTx,
                BUYER_PAYOUT_AMOUNT,
                SELLER_PAYOUT_AMOUNT,
                otherBuyerAddress,
                SELLER_PAYOUT_ADDRESS,
                BUYER_MULTI_SIG_KEY.getPubKey(),
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey(),
                PARAMS,
                "buyerSignature"));
    }

    /* --------------------------------------------------------------------- */
    // Payout amount validation
    /* --------------------------------------------------------------------- */

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
        TransactionSignature sellerSignature = createWitnessSignature(payoutTx,
                depositTx,
                SELLER_MULTI_SIG_KEY,
                Transaction.SigHash.ALL);
        TransactionSignature buyerSignature = createWitnessSignature(payoutTx,
                depositTx,
                BUYER_MULTI_SIG_KEY,
                Transaction.SigHash.ALL);
        input.setScriptSig(ScriptBuilder.createEmpty());
        setP2wshWitness(payoutTx, sellerSignature, buyerSignature, REDEEM_SCRIPT);
        return payoutTx;
    }

    private static TransactionSignature createWitnessSignature(Transaction payoutTx,
                                                               Transaction depositTx,
                                                               ECKey key,
                                                               Transaction.SigHash sigHashMode) {
        Sha256Hash sigHash = payoutTx.hashForWitnessSignature(0,
                REDEEM_SCRIPT,
                depositTx.getOutput(0).getValue(),
                sigHashMode,
                false);
        return new TransactionSignature(key.sign(sigHash), sigHashMode, false);
    }

    private static byte[] createDerSignature(Transaction payoutTx,
                                             Transaction depositTx,
                                             ECKey key) {
        Sha256Hash sigHash = payoutTx.hashForWitnessSignature(0,
                REDEEM_SCRIPT,
                depositTx.getOutput(0).getValue(),
                Transaction.SigHash.ALL,
                false);
        return key.sign(sigHash).encodeToDER();
    }

    private static void setP2wshWitness(Transaction payoutTx,
                                        TransactionSignature sellerSignature,
                                        TransactionSignature buyerSignature,
                                        Script redeemScript) {
        payoutTx.getInput(0).setWitness(TransactionWitness.redeemP2WSH(redeemScript,
                sellerSignature,
                buyerSignature));
    }

    private static void setP2wshWitness(Transaction payoutTx,
                                        byte[] sellerSignature,
                                        byte[] buyerSignature,
                                        Script redeemScript) {
        TransactionWitness witness = new TransactionWitness(4);
        witness.setPush(0, new byte[]{});
        witness.setPush(1, sellerSignature);
        witness.setPush(2, buyerSignature);
        witness.setPush(3, redeemScript.getProgram());
        payoutTx.getInput(0).setWitness(witness);
    }

    private static BtcWalletService btcWalletServiceWithParams() {
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        when(btcWalletService.getParams()).thenReturn(PARAMS);
        return btcWalletService;
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
