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
 * You should have received a copy of the GNU Affero General Public
 * License along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.trade.validation;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.exceptions.TradePriceOutOfToleranceException;
import bisq.core.offer.Offer;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.TradeFeeFactory;
import bisq.core.trade.protocol.bisq_v1.messages.InputsForDepositTxRequest;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.ScriptBuilder;

import java.security.KeyPair;

import java.nio.charset.StandardCharsets;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static bisq.core.trade.validation.DepositTxValidation.checkDepositTxMatchesIgnoringWitnessesAndScriptSigs;
import static bisq.core.trade.validation.TradeValidationTestUtils.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DepositTxValidationTest {
    static final String SELLER_ADDRESS = SegwitAddress.fromKey(PARAMS, new ECKey()).toString();
    static final String BUYER_ADDRESS = SegwitAddress.fromKey(PARAMS, new ECKey()).toString();
    static final Coin OFFER_MIN_AMOUNT = Coin.valueOf(1_000);
    static final Coin OFFER_MAX_AMOUNT = Coin.valueOf(5_000);


    /* --------------------------------------------------------------------- */
    // TradeAmount
    /* --------------------------------------------------------------------- */

    @Test
    void checkTradeAmountAcceptsOfferBoundsAndValuesBetweenThem() {
        Coin tradeAmount = Coin.valueOf(3_000);

        assertSame(OFFER_MIN_AMOUNT,
                DepositTxValidation.checkTradeAmount(OFFER_MIN_AMOUNT, OFFER_MIN_AMOUNT, OFFER_MAX_AMOUNT));
        assertSame(tradeAmount,
                DepositTxValidation.checkTradeAmount(tradeAmount, OFFER_MIN_AMOUNT, OFFER_MAX_AMOUNT));
        assertSame(OFFER_MAX_AMOUNT,
                DepositTxValidation.checkTradeAmount(OFFER_MAX_AMOUNT, OFFER_MIN_AMOUNT, OFFER_MAX_AMOUNT));
    }

    @Test
    void checkTradeAmountRejectsAmountsBelowOfferMinimum() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.checkTradeAmount(Coin.valueOf(999), OFFER_MIN_AMOUNT, OFFER_MAX_AMOUNT));

        assertEquals("Trade amount must not be less than minimum offer amount. " +
                        "tradeAmount=0.00000999 BTC, offerMinAmount=0.00001 BTC",
                exception.getMessage());
    }

    @Test
    void checkTradeAmountRejectsAmountsAboveOfferMaximum() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.checkTradeAmount(Coin.valueOf(5_001), OFFER_MIN_AMOUNT, OFFER_MAX_AMOUNT));

        assertEquals("Trade amount must not be higher than maximum offer amount. " +
                        "tradeAmount=0.00005001 BTC, offerMaxAmount=0.00005 BTC",
                exception.getMessage());
    }


    /* --------------------------------------------------------------------- */
    // TradePrice
    /* --------------------------------------------------------------------- */

    @Test
    void checkTakersTradePriceAcceptsVerifiedPrice() throws Exception {
        long takersTradePrice = 50_000_000L;
        Offer offer = mock(Offer.class);
        when(offer.isUseMarketBasedPrice()).thenReturn(true);

        assertEquals(takersTradePrice, DepositTxValidation.checkTakersTradePrice(takersTradePrice,
                mock(PriceFeedService.class),
                offer));
        verify(offer).verifyTakersTradePrice(takersTradePrice);
    }

    @Test
    void checkTakersTradePriceWrapsOfferPriceValidationFailure() throws Exception {
        long takersTradePrice = 50_000_000L;
        Offer offer = mock(Offer.class);
        doThrow(new TradePriceOutOfToleranceException("price outside tolerance"))
                .when(offer)
                .verifyTakersTradePrice(takersTradePrice);

        assertThrows(RuntimeException.class, () -> DepositTxValidation.checkTakersTradePrice(takersTradePrice,
                mock(PriceFeedService.class),
                offer));
    }


    /* --------------------------------------------------------------------- */
    // DepositTx checkMultiSigPubKey
    /* --------------------------------------------------------------------- */

    @Test
    void checkMultiSigPubKeyAcceptsCompressedPublicKey() {
        byte[] multiSigPubKey = new ECKey().getPubKey();

        assertEquals(33, multiSigPubKey.length);
        assertSame(multiSigPubKey, DepositTxValidation.checkMultiSigPubKey(multiSigPubKey));
    }

    @Test
    void checkMultiSigPubKeyRejectsNullPublicKey() {
        assertThrows(NullPointerException.class, () -> DepositTxValidation.checkMultiSigPubKey(null));
    }

    @Test
    void checkMultiSigPubKeyRejectsUncompressedPublicKey() {
        byte[] uncompressedPubKey = new ECKey().decompress().getPubKey();

        assertEquals(65, uncompressedPubKey.length);
        assertThrows(IllegalArgumentException.class, () -> DepositTxValidation.checkMultiSigPubKey(uncompressedPubKey));
    }

    @Test
    void checkMultiSigPubKeyRejectsMalformedCompressedPublicKey() {
        byte[] malformedCompressedPubKey = new byte[33];
        Arrays.fill(malformedCompressedPubKey, (byte) 0xff);
        malformedCompressedPubKey[0] = 0x02;

        assertThrows(IllegalArgumentException.class,
                () -> DepositTxValidation.checkMultiSigPubKey(malformedCompressedPubKey));
    }

    @Test
    void checkMultiSigPubKeyAcceptsValidCompressedCurvePoints() {
        // These deterministic encodings exercise x-coordinates where x^3 + 7 is a quadratic residue mod the
        // secp256k1 field prime, so both compressed y-parity prefixes map to valid curve points.
        String[] validEncodings = {
                "020000000000000000000000000000000000000000000000000000000000000001",
                "020000000000000000000000000000000000000000000000000000000000000002",
                "020000000000000000000000000000000000000000000000000000000000000003",
                "020000000000000000000000000000000000000000000000000000000000000004",
                "020000000000000000000000000000000000000000000000000000000000000006",
                "020000000000000000000000000000000000000000000000000000000000000008",
                "02000000000000000000000000000000000000000000000000000000000000000c",
                "02000000000000000000000000000000000000000000000000000000000000000d",
                "02000000000000000000000000000000000000000000000000000000000000000e",
                "030000000000000000000000000000000000000000000000000000000000000001",
                "030000000000000000000000000000000000000000000000000000000000000002",
                "030000000000000000000000000000000000000000000000000000000000000003",
                "030000000000000000000000000000000000000000000000000000000000000004",
                "030000000000000000000000000000000000000000000000000000000000000006",
                "030000000000000000000000000000000000000000000000000000000000000008",
                "03000000000000000000000000000000000000000000000000000000000000000c",
                "03000000000000000000000000000000000000000000000000000000000000000d",
                "03000000000000000000000000000000000000000000000000000000000000000e"
        };

        for (String validEncoding : validEncodings) {
            byte[] multiSigPubKey = Utilities.decodeFromHex(validEncoding);
            assertDoesNotThrow(() -> DepositTxValidation.checkMultiSigPubKey(multiSigPubKey), validEncoding);
        }
    }

    /* --------------------------------------------------------------------- */
    // InputsForDepositTxRequest
    /* --------------------------------------------------------------------- */


    @Test
    void checkInputsForDepositTxRequestAcceptsValidRequest() throws CryptoException {
        InputsForDepositTxRequestValidationFixture fixture = inputsForDepositTxRequestValidationFixture(null);

        assertSame(fixture.request, DepositTxValidation.checkInputsForDepositTxRequest(fixture.request,
                fixture.offer,
                fixture.user,
                fixture.btcWalletService,
                fixture.priceFeedService,
                fixture.delayedPayoutTxReceiverService,
                fixture.feeService));
    }

    @Test
    void checkInputsForDepositTxRequestRejectsInvalidAccountAgeWitnessSignature() throws CryptoException {
        InputsForDepositTxRequestValidationFixture fixture =
                inputsForDepositTxRequestValidationFixture(new byte[]{1});

        assertThrows(IllegalArgumentException.class, () -> DepositTxValidation.checkInputsForDepositTxRequest(fixture.request,
                fixture.offer,
                fixture.user,
                fixture.btcWalletService,
                fixture.priceFeedService,
                fixture.delayedPayoutTxReceiverService,
                fixture.feeService));
    }

    @Test
    void checkInputsForDepositTxRequestRejectsTxFeeOutsideAllowedTolerance() throws CryptoException {
        InputsForDepositTxRequestValidationFixture fixture = inputsForDepositTxRequestValidationFixture(null);
        FeeService feeService = configureTradeFeeService(Coin.valueOf(77), Coin.valueOf(100), 10);

        assertThrows(IllegalArgumentException.class, () -> DepositTxValidation.checkInputsForDepositTxRequest(fixture.request,
                fixture.offer,
                fixture.user,
                fixture.btcWalletService,
                fixture.priceFeedService,
                fixture.delayedPayoutTxReceiverService,
                feeService));
    }

    @Test
    void checkInputsForDepositTxRequestRejectsUnexpectedTakerFee() throws CryptoException {
        InputsForDepositTxRequestValidationFixture fixture =
                inputsForDepositTxRequestValidationFixture(null, Coin.valueOf(151));

        assertThrows(IllegalArgumentException.class, () -> DepositTxValidation.checkInputsForDepositTxRequest(fixture.request,
                fixture.offer,
                fixture.user,
                fixture.btcWalletService,
                fixture.priceFeedService,
                fixture.delayedPayoutTxReceiverService,
                fixture.feeService));
    }




    /* --------------------------------------------------------------------- */
    //
    /* --------------------------------------------------------------------- */


    @Test
    void checkMultiSigPubKeyRejectsInvalidCompressedCurvePoints() {
        // These x-coordinates do not produce a quadratic residue for x^3 + 7 mod the secp256k1 field prime,
        // so neither compressed y-parity prefix can decompress to a valid curve point.
        String[] invalidEncodings = {
                "020000000000000000000000000000000000000000000000000000000000000000",
                "020000000000000000000000000000000000000000000000000000000000000005",
                "020000000000000000000000000000000000000000000000000000000000000007",
                "020000000000000000000000000000000000000000000000000000000000000009",
                "02000000000000000000000000000000000000000000000000000000000000000a",
                "02000000000000000000000000000000000000000000000000000000000000000b",
                "02000000000000000000000000000000000000000000000000000000000000000f",
                "030000000000000000000000000000000000000000000000000000000000000000",
                "030000000000000000000000000000000000000000000000000000000000000005",
                "030000000000000000000000000000000000000000000000000000000000000007",
                "030000000000000000000000000000000000000000000000000000000000000009",
                "03000000000000000000000000000000000000000000000000000000000000000a",
                "03000000000000000000000000000000000000000000000000000000000000000b",
                "03000000000000000000000000000000000000000000000000000000000000000f"
        };

        for (String invalidEncoding : invalidEncodings) {
            byte[] multiSigPubKey = Utilities.decodeFromHex(invalidEncoding);
            assertThrows(IllegalArgumentException.class,
                    () -> DepositTxValidation.checkMultiSigPubKey(multiSigPubKey),
                    invalidEncoding);
        }
    }


    @Test
    void checkDepositTxMatchesIgnoringWitnessesAndScriptSigsAcceptsMatchingTxsAndReturnsDepositTx() {
        Transaction expectedDepositTx = depositTx(Coin.valueOf(10_000), BUYER_ADDRESS, 0);
        Transaction depositTx = copy(expectedDepositTx);
        addSignatureData(expectedDepositTx, new byte[]{1, 2}, new byte[]{3});
        addSignatureData(depositTx, new byte[]{4, 5}, new byte[]{6});

        assertSame(depositTx,
                checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(depositTx,
                        expectedDepositTx,
                        PARAMS));
    }

    @Test
    void checkDepositTxMatchesIgnoringWitnessesAndScriptSigsRejectsDifferentInputOutpoint() {
        Transaction expectedDepositTx = depositTx(Coin.valueOf(10_000), BUYER_ADDRESS, 0);
        Transaction depositTx = depositTx(Coin.valueOf(10_000), BUYER_ADDRESS, 1);

        assertThrows(IllegalArgumentException.class,
                () -> checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(depositTx,
                        expectedDepositTx,
                        PARAMS));
    }

    @Test
    void checkDepositTxMatchesIgnoringWitnessesAndScriptSigsRejectsDifferentOutputAmount() {
        Transaction expectedDepositTx = depositTx(Coin.valueOf(10_000), BUYER_ADDRESS, 0);
        Transaction depositTx = depositTx(Coin.valueOf(9_999), BUYER_ADDRESS, 0);

        assertThrows(IllegalArgumentException.class,
                () -> checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(depositTx,
                        expectedDepositTx,
                        PARAMS));
    }

    @Test
    void checkDepositTxMatchesIgnoringWitnessesAndScriptSigsRejectsDifferentOutputAddress() {
        Transaction expectedDepositTx = depositTx(Coin.valueOf(10_000), BUYER_ADDRESS, 0);
        Transaction depositTx = depositTx(Coin.valueOf(10_000), SELLER_ADDRESS, 0);

        assertThrows(IllegalArgumentException.class,
                () -> checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(depositTx,
                        expectedDepositTx,
                        PARAMS));
    }

    @Test
    void checkDepositTxMatchesIgnoringWitnessesAndScriptSigsRejectsNullPublicArgs() {
        Transaction depositTx = depositTx(Coin.valueOf(10_000), BUYER_ADDRESS, 0);
        BtcWalletService btcWalletService = TradeValidationTestUtils.btcWalletService();

        assertThrows(NullPointerException.class,
                () -> checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(null,
                        depositTx,
                        btcWalletService));
        assertThrows(NullPointerException.class,
                () -> checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(depositTx,
                        null,
                        btcWalletService));
        assertThrows(NullPointerException.class,
                () -> checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(depositTx,
                        depositTx,
                        (BtcWalletService) null));
    }

    static Transaction depositTx(Coin outputAmount, String addressString, long outpointIndex) {
        Transaction transaction = new Transaction(PARAMS);
        transaction.addInput(new TransactionInput(PARAMS,
                transaction,
                new byte[]{},
                new TransactionOutPoint(PARAMS, outpointIndex, Sha256Hash.ZERO_HASH),
                outputAmount.add(Coin.SATOSHI)));
        transaction.addOutput(outputAmount, Address.fromString(PARAMS, addressString));
        return transaction;
    }

    static void addSignatureData(Transaction transaction, byte[] scriptSigProgram, byte[] witnessProgram) {
        transaction.getInput(0).setScriptSig(new ScriptBuilder().data(scriptSigProgram).build());
        TransactionWitness witness = new TransactionWitness(1);
        witness.setPush(0, witnessProgram);
        transaction.getInput(0).setWitness(witness);
    }

    static Transaction copy(Transaction transaction) {
        return new Transaction(PARAMS, transaction.bitcoinSerialize());
    }


    static InputsForDepositTxRequestValidationFixture inputsForDepositTxRequestValidationFixture(
            byte[] accountAgeWitnessSignatureOverride) throws CryptoException {
        return inputsForDepositTxRequestValidationFixture(accountAgeWitnessSignatureOverride, Coin.valueOf(100));
    }

    static InputsForDepositTxRequestValidationFixture inputsForDepositTxRequestValidationFixture(
            byte[] accountAgeWitnessSignatureOverride,
            Coin requestTakerFee) throws CryptoException {
        String offerId = "offer-id";
        Coin tradeAmount = Coin.valueOf(3_000);
        Coin expectedTakerFee = Coin.valueOf(100);
        FeeService feeService = configureTradeFeeService(Coin.valueOf(77), expectedTakerFee, 2);
        Coin tradeTxFee = TradeFeeFactory.getTradeTxFee(feeService.getTxFeePerVbyte());
        NodeAddress mediatorNodeAddress = new NodeAddress("mediator.onion", 9999);
        BtcWalletService btcWalletService = btcWalletService();
        Offer offer = offer(true, Coin.valueOf(10_000), Coin.valueOf(12_000), Coin.valueOf(40_000));
        when(offer.getId()).thenReturn(offerId);
        when(offer.getMinAmount()).thenReturn(OFFER_MIN_AMOUNT);
        when(offer.isUseMarketBasedPrice()).thenReturn(true);

        KeyPair takerSignatureKeyPair = Sig.generateKeyPair();
        PubKeyRing takerPubKeyRing = pubKeyRing(takerSignatureKeyPair);
        byte[] accountAgeWitnessSignature = accountAgeWitnessSignatureOverride != null ?
                accountAgeWitnessSignatureOverride :
                Sig.sign(takerSignatureKeyPair.getPrivate(), offerId.getBytes(StandardCharsets.UTF_8));

        List<RawTransactionInput> rawTransactionInputs = rawTransactionInputs(btcWalletService,
                offer.getSellerSecurityDeposit()
                        .add(tradeAmount)
                        .add(tradeTxFee.multiply(2)));
        InputsForDepositTxRequest request = new InputsForDepositTxRequest(offerId,
                new NodeAddress("sender.onion", 9999),
                tradeAmount.value,
                50_000_000L,
                tradeTxFee.value,
                requestTakerFee.value,
                true,
                rawTransactionInputs,
                new ECKey().getPubKey(),
                SegwitAddress.fromKey(MainNetParams.get(), new ECKey()).toString(),
                takerPubKeyRing,
                "taker-account-id",
                TradeValidationTestUtils.VALID_TRANSACTION_ID,
                List.of(),
                List.of(mediatorNodeAddress),
                List.of(),
                null,
                mediatorNodeAddress,
                new NodeAddress("refund-agent.onion", 9999),
                "uid",
                1,
                accountAgeWitnessSignature,
                System.currentTimeMillis(),
                new byte[]{2},
                "SEPA",
                130);
        User user = userWithAcceptedMediator(mediatorNodeAddress,
                mediator(mediatorNodeAddress, pubKeyRing(Sig.generateKeyPair())));

        return new InputsForDepositTxRequestValidationFixture(request,
                offer,
                user,
                btcWalletService,
                mock(PriceFeedService.class),
                delayedPayoutTxReceiverService(130),
                feeService);
    }

    static class InputsForDepositTxRequestValidationFixture {
        private final InputsForDepositTxRequest request;
        private final Offer offer;
        private final User user;
        private final BtcWalletService btcWalletService;
        private final PriceFeedService priceFeedService;
        private final DelayedPayoutTxReceiverService delayedPayoutTxReceiverService;
        private final FeeService feeService;

        private InputsForDepositTxRequestValidationFixture(InputsForDepositTxRequest request,
                                                           Offer offer,
                                                           User user,
                                                           BtcWalletService btcWalletService,
                                                           PriceFeedService priceFeedService,
                                                           DelayedPayoutTxReceiverService delayedPayoutTxReceiverService,
                                                           FeeService feeService) {
            this.request = request;
            this.offer = offer;
            this.user = user;
            this.btcWalletService = btcWalletService;
            this.priceFeedService = priceFeedService;
            this.delayedPayoutTxReceiverService = delayedPayoutTxReceiverService;
            this.feeService = feeService;
        }
    }

}
