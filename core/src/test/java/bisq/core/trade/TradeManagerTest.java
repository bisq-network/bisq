package bisq.core.trade;

import bisq.core.arbitration.Arbitrator;
import bisq.core.arbitration.ArbitratorManager;
import bisq.core.arbitration.Mediator;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.model.InputsAndChangeOutput;
import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.payment.AliPayAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.closed.ClosedTradableManager;
import bisq.core.trade.failed.FailedTradesManager;
import bisq.core.trade.handlers.TradeResultHandler;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.app.Version;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;

import javafx.collections.FXCollections;

import java.security.KeyPair;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.*;



import org.mockito.Mockito;

public class TradeManagerTest {

    @Test
    public void onTakeOffer_firstAttemptToWriteATest() throws Exception {
//        Given
        PaymentMethod.onAllServicesInitialized();
        final String paymentAccountId = "paymentAccountId";
        final P2PService p2pServiceMock = mock(P2PService.class);
        final KeyRing keyRingMock = mock(KeyRing.class);
        when(keyRingMock.getSignatureKeyPair()).thenReturn(Sig.generateKeyPair());
        final KeyPair keyPair = Sig.generateKeyPair();
        final PubKeyRing pubKeyRing = new PubKeyRing(keyPair.getPublic(), keyPair.getPublic(), null);
        when(keyRingMock.getPubKeyRing()).thenReturn(pubKeyRing);
        final Arbitrator arbitrator = new Arbitrator(new NodeAddress("a", 0), null, null, null, null, 0, null, null, null, null, null);
        final Mediator mediator = new Mediator(new NodeAddress("m", 1), null, null, 0, null, null, null, null, null);
        final User userMock = mock(User.class);
        when(userMock.getAcceptedArbitratorAddresses()).thenReturn(Collections.singletonList(arbitrator.getNodeAddress()));
        when(userMock.getAcceptedArbitratorByAddress(arbitrator.getNodeAddress())).thenReturn(arbitrator);
        when(userMock.getAcceptedMediatorByAddress(mediator.getNodeAddress())).thenReturn(mediator);
        when(userMock.getAcceptedMediatorAddresses()).thenReturn(Collections.singletonList(mediator.getNodeAddress()));
        when(userMock.getAccountId()).thenReturn("userAccountId");
        final AliPayAccount paymentAccount = new AliPayAccount();
        paymentAccount.init();
        when(userMock.getPaymentAccount(paymentAccountId)).thenReturn(paymentAccount);
        final TradeStatisticsManager tradeStatisticsManagerMock = mock(TradeStatisticsManager.class);
        when(tradeStatisticsManagerMock.getObservableTradeStatisticsSet()).thenReturn(FXCollections.observableSet());
        final ArbitratorManager arbitratorManagerMock = mock(ArbitratorManager.class);
        when(arbitratorManagerMock.getArbitratorsObservableMap()).thenReturn(FXCollections.observableMap(Collections.singletonMap(arbitrator.getNodeAddress(), arbitrator)));
        final String offerId = "offerId";
        final BtcWalletService btcWalletServiceMock = mock(BtcWalletService.class);
        final AddressEntry addressEntry = new AddressEntry(mock(DeterministicKey.class), AddressEntry.Context.OFFER_FUNDING);
        when(btcWalletServiceMock.getOrCreateAddressEntry(matches(offerId), any())).thenReturn(addressEntry);
        when(btcWalletServiceMock.getFreshAddressEntry()).thenReturn(addressEntry);
        final TradeWalletService tradeWalletServiceMock = mock(TradeWalletService.class);

        final Path tradeManagerTest = Files.createTempDirectory("tradeManagerTest");
        final TradeManager tradeManager = new TradeManager(userMock, keyRingMock, btcWalletServiceMock, null, tradeWalletServiceMock, null, mock(ClosedTradableManager.class), mock(FailedTradesManager.class), p2pServiceMock, null, null, tradeStatisticsManagerMock, null, null, null, arbitratorManagerMock, null, tradeManagerTest.toFile());
        final ErrorMessageHandler errorMessageHandlerMock = mock(ErrorMessageHandler.class);
        when(tradeWalletServiceMock.createBtcTradingFeeTx(any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any())).thenAnswer(invocation -> {
            final Transaction transactionMock = mock(Transaction.class);
            when(transactionMock.getHashAsString()).thenReturn("transactionHashAsString");
            final TxBroadcaster.Callback callback = invocation.getArgument(8);
            callback.onSuccess(transactionMock);
            return transactionMock;
        });
        when(tradeWalletServiceMock.takerCreatesDepositsTxInputs(any(), any(), any(), any())).thenReturn(new InputsAndChangeOutput(Collections.singletonList(new RawTransactionInput(0, new byte[0], 0)), 0, null));
        final Coin txFee = Coin.ZERO;
        final Coin takerFee = Coin.ZERO;
        final Coin amount = Coin.SATOSHI;
        final Coin fundsNeededForTrade = Coin.ZERO;
        tradeManager.readPersisted();
        final TradeResultHandler tradeResultHandlerMock = mock(TradeResultHandler.class);
        final Offer offer = Mockito.spy(createOffer(offerId, arbitrator, mediator));
        doAnswer(invocation -> {
            ((ResultHandler) invocation.getArgument(1)).handleResult();
            return null;
        }).when(offer).checkOfferAvailability(any(), any(), any());

//        When
        doAnswer(invocation -> {
            Assert.assertNull("Trade should not have error property set", ((Trade) invocation.getArgument(0)).getErrorMessage());
            return null;
        }).when(tradeResultHandlerMock).handleResult(any());
        tradeManager.onTakeOffer(amount, txFee, takerFee, true, 1, fundsNeededForTrade, offer, paymentAccountId, false, tradeResultHandlerMock, errorMessageHandlerMock);

//        Then
        verifyNoMoreInteractions(errorMessageHandlerMock);
//        TODO what properties should the trade have?
        verify(tradeResultHandlerMock).handleResult(any(Trade.class));
//        TODO what should happen as the result of onTakeOffer call? What mocks should be called?
//        Following instructions can be used what calls are actually made
//        verifyNoMoreInteractions(p2pServiceMock);
//        verifyNoMoreInteractions(userMock);
//        verifyNoMoreInteractions(tradeStatisticsManagerMock);
//        verifyNoMoreInteractions(arbitratorManagerMock);
//        verifyNoMoreInteractions(btcWalletServiceMock);
//        verifyNoMoreInteractions(tradeWalletServiceMock);
    }

    private Offer createOffer(String offerId, Arbitrator arbitrator, Mediator mediator) {
        final long now = new Date().getTime();
        final int price = 1;
        final double marketPriceMargin = 0.1;
        final boolean useMarketBasedPrice = false;
        final int amount = 1;
        final int minAmount = 1;
        final String baseCurrencyCode = "BTC";
        final String counterCurrencyCode = "USD";
        final long lastBlockSeenHeight = 1;
        final int txFee = 0;
        final int makerFee = 0;
        final boolean isCurrencyForMakerFeeBtc = false;
        final int buyerSecurityDeposit = 0;
        final int sellerSecurityDeposit = 0;
        final int maxTradeLimit = 0;
        final int maxTradePeriod = 0;
        final boolean useAutoClose = false;
        final boolean useReOpenAfterAutoClose = false;
        final int lowerClosePrice = 0;
        final int upperClosePrice = 0;
        final boolean isPrivateOffer = false;
        final String hashOfChallenge = null;
        final Map<String, String> extraDataMap = null;
        final KeyPair keyPair = Sig.generateKeyPair();
        final PubKeyRing pubKeyRing = new PubKeyRing(keyPair.getPublic(), keyPair.getPublic(), null);
        final List<NodeAddress> arbitrators = Collections.singletonList(arbitrator.getNodeAddress());
        final List<NodeAddress> mediators = Collections.singletonList(mediator.getNodeAddress());
        OfferPayload offerPayload = new OfferPayload(offerId,
                now,
                new NodeAddress("0", 0),
                pubKeyRing,
                OfferPayload.Direction.SELL,
                price,
                marketPriceMargin,
                useMarketBasedPrice,
                amount,
                minAmount,
                baseCurrencyCode,
                counterCurrencyCode,
                arbitrators,
                mediators,
                PaymentMethod.ALI_PAY_ID,
                "paymentAccountId",
                null,
                null,
                null,
                null,
                null,
                Version.VERSION,
                lastBlockSeenHeight,
                txFee,
                makerFee,
                isCurrencyForMakerFeeBtc,
                buyerSecurityDeposit,
                sellerSecurityDeposit,
                maxTradeLimit,
                maxTradePeriod,
                useAutoClose,
                useReOpenAfterAutoClose,
                lowerClosePrice,
                upperClosePrice,
                isPrivateOffer,
                hashOfChallenge,
                extraDataMap,
                Version.TRADE_PROTOCOL_VERSION);
        final Offer offer = new Offer(offerPayload);
        offer.setState(Offer.State.AVAILABLE);
        offer.setOfferFeePaymentTxId("abc");
        return offer;
    }
}
