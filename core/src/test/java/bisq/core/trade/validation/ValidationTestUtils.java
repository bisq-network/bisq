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

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.filter.FilterManager;
import bisq.core.offer.Offer;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.dispute.mediation.mediator.Mediator;
import bisq.core.trade.TradeFeeFactory;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.protocol.bisq_v1.messages.InputsForDepositTxRequest;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

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

import java.math.BigInteger;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ValidationTestUtils {
    static final MainNetParams PARAMS = MainNetParams.get();
    static final int GENESIS_HEIGHT = 102;
    static final String VALID_TRANSACTION_ID =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    /* --------------------------------------------------------------------- */
    // Services and keys
    /* --------------------------------------------------------------------- */

    static BtcWalletService btcWalletService() {
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        when(btcWalletService.getParams()).thenReturn(PARAMS);
        return btcWalletService;
    }

    static PubKeyRing pubKeyRing(KeyPair signatureKeyPair) {
        return new PubKeyRing(signatureKeyPair.getPublic(), Encryption.generateKeyPair().getPublic());
    }

    static FeeService configureTradeFeeService(Coin makerFee, Coin takerFee) {
        return configureTradeFeeService(makerFee, takerFee, 1);
    }

    static FeeService configureTradeFeeService(Coin makerFee, Coin takerFee, long txFeePerVbyte) {
        int chainHeight = ValidationTestUtils.GENESIS_HEIGHT;
        DaoStateService daoStateService = mock(DaoStateService.class);
        PeriodService periodService = mock(PeriodService.class);
        FilterManager filterManager = mock(FilterManager.class);
        when(periodService.getChainHeight()).thenReturn(chainHeight);
        when(filterManager.getFilter()).thenReturn(null);
        when(daoStateService.getParamValueAsCoin(Param.MIN_MAKER_FEE_BTC, chainHeight)).thenReturn(makerFee);
        when(daoStateService.getParamValueAsCoin(Param.MIN_MAKER_FEE_BSQ, chainHeight)).thenReturn(makerFee);
        when(daoStateService.getParamValueAsCoin(Param.MIN_TAKER_FEE_BTC, chainHeight)).thenReturn(takerFee);
        when(daoStateService.getParamValueAsCoin(Param.MIN_TAKER_FEE_BSQ, chainHeight)).thenReturn(takerFee);
        when(daoStateService.getParamValueAsCoin(Param.DEFAULT_MAKER_FEE_BTC, chainHeight)).thenReturn(Coin.SATOSHI);
        when(daoStateService.getParamValueAsCoin(Param.DEFAULT_MAKER_FEE_BSQ, chainHeight)).thenReturn(Coin.SATOSHI);
        when(daoStateService.getParamValueAsCoin(Param.DEFAULT_TAKER_FEE_BTC, chainHeight)).thenReturn(Coin.SATOSHI);
        when(daoStateService.getParamValueAsCoin(Param.DEFAULT_TAKER_FEE_BSQ, chainHeight)).thenReturn(Coin.SATOSHI);
        FeeService feeService = new FeeService(daoStateService, periodService);
        feeService.onAllServicesInitialized(filterManager);
        feeService.updateFeeInfo(txFeePerVbyte, 1);
        return feeService;
    }

    /* --------------------------------------------------------------------- */
    // Trade domain fixtures
    /* --------------------------------------------------------------------- */

    static Offer offer(boolean isBuyOffer,
                       Coin buyerSecurityDeposit,
                       Coin sellerSecurityDeposit,
                       Coin offerAmount) {
        Offer offer = mock(Offer.class);
        when(offer.isBuyOffer()).thenReturn(isBuyOffer);
        when(offer.getBuyerSecurityDeposit()).thenReturn(buyerSecurityDeposit);
        when(offer.getSellerSecurityDeposit()).thenReturn(sellerSecurityDeposit);
        when(offer.getAmount()).thenReturn(offerAmount);
        return offer;
    }

    static Trade trade(Offer offer, Coin tradeTxFee) {
        return trade(offer, tradeTxFee, Coin.ZERO);
    }

    static Trade trade(Offer offer, Coin tradeTxFee, Coin tradeAmount) {
        Trade trade = mock(Trade.class);
        when(trade.getOffer()).thenReturn(offer);
        when(trade.getTradeTxFee()).thenReturn(tradeTxFee);
        when(trade.getTradeTxFeeAsLong()).thenReturn(tradeTxFee.value);
        when(trade.getAmountAsLong()).thenReturn(tradeAmount.value);
        return trade;
    }

    static DelayedPayoutTxReceiverService delayedPayoutTxReceiverService(int burningManSelectionHeight) {
        DelayedPayoutTxReceiverService delayedPayoutTxReceiverService = mock(DelayedPayoutTxReceiverService.class);
        when(delayedPayoutTxReceiverService.getBurningManSelectionHeight()).thenReturn(burningManSelectionHeight);
        return delayedPayoutTxReceiverService;
    }

    static User userWithAcceptedMediator(NodeAddress mediatorNodeAddress, Mediator mediator) {
        User user = mock(User.class);
        when(user.getAcceptedMediatorByAddress(mediatorNodeAddress)).thenReturn(mediator);
        return user;
    }

    static TradeMessage tradeMessage(String tradeId) {
        TradeMessage tradeMessage = mock(TradeMessage.class);
        when(tradeMessage.getTradeId()).thenReturn(tradeId);
        return tradeMessage;
    }

    /* --------------------------------------------------------------------- */
    // Transaction fixtures
    /* --------------------------------------------------------------------- */

    static byte[] serializedTransaction() {
        return transaction(new byte[]{}).bitcoinSerialize();
    }

    static byte[] serializedTransactionWithoutOutputs() {
        return transactionWithoutOutputs().bitcoinSerialize();
    }

    static byte[] serializedTransactionWithScriptSig() {
        return transaction(new byte[]{1, 1}).bitcoinSerialize();
    }

    static byte[] serializedTransactionWithWitness() {
        Transaction transaction = transaction(new byte[]{});
        TransactionWitness witness = new TransactionWitness(1);
        witness.setPush(0, new byte[]{1});
        transaction.getInput(0).setWitness(witness);
        return transaction.bitcoinSerialize();
    }

    static Transaction transaction(byte[] scriptSigProgram) {
        Transaction transaction = new Transaction(MainNetParams.get());
        transaction.addInput(new TransactionInput(MainNetParams.get(),
                transaction,
                scriptSigProgram,
                new TransactionOutPoint(MainNetParams.get(), 0, Sha256Hash.ZERO_HASH),
                Coin.valueOf(2_000)));
        transaction.addOutput(Coin.valueOf(1_000), ScriptBuilder.createP2WPKHOutputScript(new ECKey()));
        return transaction;
    }

    static Transaction transactionWithoutOutputs() {
        Transaction transaction = new Transaction(MainNetParams.get());
        transaction.addInput(new TransactionInput(MainNetParams.get(),
                transaction,
                new byte[]{},
                new TransactionOutPoint(MainNetParams.get(), 0, Sha256Hash.ZERO_HASH),
                Coin.valueOf(2_000)));
        return transaction;
    }

    static byte[] bitcoinSignature(BigInteger r, BigInteger s) {
        return new ECKey.ECDSASignature(r, s).encodeToDER();
    }

    static Mediator mediator(NodeAddress mediatorNodeAddress, PubKeyRing pubKeyRing) {
        return new Mediator(mediatorNodeAddress,
                pubKeyRing,
                List.of("en"),
                System.currentTimeMillis(),
                new byte[]{1},
                "registrationSignature",
                null,
                null,
                null);
    }

    static List<RawTransactionInput> rawTransactionInputs(BtcWalletService btcWalletService, Coin inputAmount) {
        RawTransactionInput rawTransactionInput = rawTransactionInput(inputAmount);
        byte[] parentTransaction = rawTransactionInput.parentTransaction;
        Transaction transaction = new Transaction(MainNetParams.get());
        transaction.addOutput(inputAmount, ScriptBuilder.createP2WPKHOutputScript(new ECKey()));

        when(btcWalletService.getTxFromSerializedTx(parentTransaction)).thenReturn(transaction);
        when(btcWalletService.isP2WH(rawTransactionInput)).thenReturn(true);

        return List.of(rawTransactionInput);
    }

    static RawTransactionInput rawTransactionInput(Coin inputAmount) {
        byte[] parentTransaction = new byte[]{1, 2, 3};
        return new RawTransactionInput(0,
                parentTransaction,
                inputAmount.value);
    }

    /* --------------------------------------------------------------------- */
    // InputsForDepositTxRequest fixture
    /* --------------------------------------------------------------------- */

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
        when(offer.getMinAmount()).thenReturn(Coin.valueOf(1_000));
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
                VALID_TRANSACTION_ID,
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

    static final class InputsForDepositTxRequestValidationFixture {
        final InputsForDepositTxRequest request;
        final Offer offer;
        final User user;
        final BtcWalletService btcWalletService;
        final PriceFeedService priceFeedService;
        final DelayedPayoutTxReceiverService delayedPayoutTxReceiverService;
        final FeeService feeService;

        InputsForDepositTxRequestValidationFixture(InputsForDepositTxRequest request,
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
