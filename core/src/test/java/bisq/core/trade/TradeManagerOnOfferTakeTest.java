package bisq.core.trade;

import bisq.core.arbitration.Arbitrator;
import bisq.core.arbitration.ArbitratorManager;
import bisq.core.arbitration.Mediator;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.model.InputsAndChangeOutput;
import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.filter.FilterManager;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.AliPayAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.closed.ClosedTradableManager;
import bisq.core.trade.failed.FailedTradesManager;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.Clock;
import bisq.common.app.Version;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.persistable.PersistenceProtoResolver;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;

import javafx.collections.FXCollections;

import java.security.KeyPair;

import java.nio.file.Files;

import java.io.File;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.NotNull;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;



import javax.validation.ValidationException;
import org.mockito.Mockito;

public class TradeManagerOnOfferTakeTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private Arbitrator arbitratorA;
    private Mediator mediatorA;
    private KeyRing keyRing;
    private BtcWalletService btcWalletService;
    private BsqWalletService bsqWalletService;
    private TradeWalletService tradeWalletService;
    private OpenOfferManager openOfferManager;
    private ClosedTradableManager closedTradableManager;
    private FailedTradesManager failedTradesManager;
    private FeeService feeService;
    private P2PService p2PService;
    private Preferences preferences;
    private PriceFeedService priceFeedService;
    private FilterManager filterManager;
    private TradeStatisticsManager tradeStatisticsManager;
    private ReferralIdService referralIdService;
    private PersistenceProtoResolver persistenceProtoResolver;
    private AccountAgeWitnessService accountAgeWitnessService;
    private ArbitratorManager arbitratorManager;
    private Clock clock;
    private File storageDir;
    private User user;
    private AliPayAccount usersPaymentAccount;
    private Offer offerToCreate;

    @Before
    public void setUp() throws Exception {
        Res.setup();//TODO this is ugly! We should be able inject Res instance as constructor param into TradeManager
        PaymentMethod.onAllServicesInitialized();
        usersPaymentAccount = new AliPayAccount();
        usersPaymentAccount.init();
        usersPaymentAccount.addCurrency(new FiatCurrency("USD"));
        Assert.assertNotNull(usersPaymentAccount.getId());

        arbitratorA = new Arbitrator(new NodeAddress("arbitratorA", 0), null, null, null, null, 0, null, null, null, null, null);
        mediatorA = new Mediator(new NodeAddress("mediatorA", 1), null, null, 0, null, null, null, null, null);

        keyRing = mock(KeyRing.class);
        when(keyRing.getSignatureKeyPair()).thenReturn(Sig.generateKeyPair());
        KeyPair keyPair = Sig.generateKeyPair();
        PubKeyRing pubKeyRing = new PubKeyRing(keyPair.getPublic(), keyPair.getPublic(), null);
        when(keyRing.getPubKeyRing()).thenReturn(pubKeyRing);

        btcWalletService = mock(BtcWalletService.class);
        AddressEntry addressEntry = new AddressEntry(mock(DeterministicKey.class), AddressEntry.Context.OFFER_FUNDING);
//        TODO stub more precisely, ideally with exact values or at least matchers
        when(btcWalletService.getOrCreateAddressEntry(any(), any())).thenReturn(addressEntry);
        when(btcWalletService.getFreshAddressEntry()).thenReturn(addressEntry);
        when(btcWalletService.getAvailableBalance()).thenReturn(Coin.SATOSHI);

        bsqWalletService = null;
        openOfferManager = null;
        closedTradableManager = null;
        failedTradesManager = null;
        feeService = null;
        p2PService = mock(P2PService.class);
        preferences = null;
        priceFeedService = null;
        filterManager = mock(FilterManager.class);

        tradeWalletService = mock(TradeWalletService.class);
        when(tradeWalletService.createBtcTradingFeeTx(any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Transaction transactionMock = mock(Transaction.class);
            when(transactionMock.getHashAsString()).thenReturn("transactionHashAsString");
            TxBroadcaster.Callback callback = invocation.getArgument(8);
            callback.onSuccess(transactionMock);
            return transactionMock;
        });
        when(tradeWalletService.takerCreatesDepositsTxInputs(any(), any(), any(), any())).thenReturn(new InputsAndChangeOutput(Collections.singletonList(new RawTransactionInput(0, new byte[0], 0)), 0, null));


        tradeStatisticsManager = mock(TradeStatisticsManager.class);
        when(tradeStatisticsManager.getObservableTradeStatisticsSet()).thenReturn(FXCollections.observableSet());

        referralIdService = null;
        persistenceProtoResolver = null;
        accountAgeWitnessService = null;

        arbitratorManager = mock(ArbitratorManager.class);
        when(arbitratorManager.getArbitratorsObservableMap()).thenReturn(FXCollections.observableMap(Collections.singletonMap(arbitratorA.getNodeAddress(), arbitratorA)));

        clock = null;

//        TODO it would be good if this test did not create any files
        storageDir = Files.createTempDirectory("tradeManagerTest").toFile();

        user = mock(User.class);
        when(user.getPaymentAccount(usersPaymentAccount.getId())).thenReturn(usersPaymentAccount);
        when(user.getAcceptedArbitratorAddresses()).thenReturn(Collections.singletonList(arbitratorA.getNodeAddress()));
        when(user.getAcceptedArbitratorByAddress(arbitratorA.getNodeAddress())).thenReturn(arbitratorA);
        when(user.getAcceptedMediatorByAddress(mediatorA.getNodeAddress())).thenReturn(mediatorA);
        when(user.getAcceptedMediatorAddresses()).thenReturn(Collections.singletonList(mediatorA.getNodeAddress()));
        when(user.getAccountId()).thenReturn("userAccountId");

        offerToCreate = createOffer(OfferPayload.Direction.SELL, UUID.randomUUID().toString(), arbitratorA, mediatorA);
    }

    @Test
    public void onTakeOffer_paymentAccountIdIsNull_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = "Payment account for given id does not exist: null";
        OnTakeOfferParams params = getValidParams();
        params.paymentAccountId = null;

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_noPaymentAccountForGivenId_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = "Payment account for given id does not exist: xyz";
        OnTakeOfferParams params = getValidParams();
        params.paymentAccountId = "xyz";

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_paymentAccountNotValidForOffer_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = "PaymentAccount is not valid for offer, needs PLN";
        OnTakeOfferParams params = getValidParams();
        when(offerToCreate.getCurrencyCode()).thenReturn("PLN");

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_offerIsNull_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = "Offer must not be null";
        OnTakeOfferParams params = getValidParams();
        params.offer = null;

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_currencyCodeIsNull_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = "No such currency: null";
        OnTakeOfferParams params = getValidParams();
        when(params.offer.getCurrencyCode()).thenReturn(null);

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_currencyCodeReferencesNonExistingCurrency_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = "No such currency: nonExistent";
        OnTakeOfferParams params = getValidParams();
        when(params.offer.getCurrencyCode()).thenReturn("nonExistent");

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_currencyIsBanned_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = Res.get("offerbook.warning.currencyBanned");
        Assert.assertNotNull(expectedExceptionMessage);
        OnTakeOfferParams params = getValidParams();
        when(filterManager.isCurrencyBanned(params.offer.getCurrencyCode())).thenReturn(true);

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_paymentMethodIsBanned_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = Res.get("offerbook.warning.paymentMethodBanned");
        Assert.assertNotNull(expectedExceptionMessage);
        OnTakeOfferParams params = getValidParams();
        when(filterManager.isPaymentMethodBanned(params.offer.getPaymentMethod())).thenReturn(true);

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_offerIdIsBanned_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = Res.get("offerbook.warning.offerBlocked");
        Assert.assertNotNull(expectedExceptionMessage);
        OnTakeOfferParams params = getValidParams();
        when(filterManager.isOfferIdBanned(params.offer.getId())).thenReturn(true);

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_makerNodeAddressIsBanned_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = Res.get("offerbook.warning.nodeBlocked");
        Assert.assertNotNull(expectedExceptionMessage);
        OnTakeOfferParams params = getValidParams();
        when(filterManager.isNodeAddressBanned(params.offer.getMakerNodeAddress())).thenReturn(true);

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_makerNodeAddressSameAsTaker_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = "Taker's address same as maker's";
        OnTakeOfferParams params = getValidParams();
        NodeAddress myNodeAddress = new NodeAddress("me", 0);
        when(p2PService.getAddress()).thenReturn(myNodeAddress);
        when(offerToCreate.getMakerNodeAddress()).thenReturn(myNodeAddress);

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_amountIsNull_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = "Amount must be a positive number";
        OnTakeOfferParams params = getValidParams();
        params.amount = null;

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_amountIsNegative_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = "Amount must be a positive number";
        OnTakeOfferParams params = getValidParams();
        params.amount = Coin.valueOf(-1);

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_amountIsZero_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = "Amount must be a positive number";
        OnTakeOfferParams params = getValidParams();
        params.amount = Coin.ZERO;

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_amountHigherThanOfferAmount_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = "Taken amount must not be higher than offer amount";
        OnTakeOfferParams params = getValidParams();
        params.amount = params.offer.getAmount().add(Coin.SATOSHI);
        Assert.assertTrue(params.amount.isGreaterThan(params.offer.getAmount()));

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_txFeeIsNull_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = "Transaction fee must be a positive number";
        OnTakeOfferParams params = getValidParams();
        params.txFee = null;

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_txFeeIsZero_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = "Transaction fee must be a positive number";
        OnTakeOfferParams params = getValidParams();
        params.txFee = Coin.ZERO;

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_txFeeIsNegative_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = "Transaction fee must be a positive number";
        OnTakeOfferParams params = getValidParams();
        params.txFee = Coin.valueOf(-1);

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_takerFeeIsNull_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = "Taker fee must be a positive number";
        OnTakeOfferParams params = getValidParams();
        params.takerFee = null;

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_takerFeeIsZero_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = "Taker fee must be a positive number";
        OnTakeOfferParams params = getValidParams();
        params.takerFee = Coin.ZERO;

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_takerFeeIsNegative_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = "Taker fee must be a positive number";
        OnTakeOfferParams params = getValidParams();
        params.takerFee = Coin.valueOf(-1);

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_tradePriceIsNegative_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = "Trade price must be a positive number";
        OnTakeOfferParams params = getValidParams();
        params.tradePrice = -1;

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_tradePriceIsZero_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = "Trade price must be a positive number";
        OnTakeOfferParams params = getValidParams();
        params.tradePrice = 0;

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }


    @Test
    public void onTakeOffer_offerIsNotAvailable_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = ValidationException.class;
        String expectedExceptionMessage = "Offer not available";
        OnTakeOfferParams params = getValidParams();
        when(params.offer.getState()).thenReturn(Offer.State.MAKER_OFFLINE);

        doAnswer(invocation -> {
            ((ResultHandler) invocation.getArgument(1)).handleResult();
            return null;
        }).when(offerToCreate).checkOfferAvailability(any(), any(), any());

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    @Test
    public void onTakeOffer_checkOfferAvailabilityFails_completesExceptionally() {
//        Given
        Class<? extends Throwable> expectedExceptionClass = TradeFailedException.class;
        String expectedExceptionMessage = "errorMessage" + new Date().toString();
        OnTakeOfferParams params = getValidParams();
        doAnswer(invocation -> {
            ErrorMessageHandler handler = invocation.getArgument(2);
            handler.handleErrorMessage(expectedExceptionMessage);
            return null;
        }).when(params.offer).checkOfferAvailability(any(), any(), any());

//        When
        CompletableFuture<Trade> completableFuture = onTakeOffer(params);

//        Then
        assertCompletedExceptionally(completableFuture, expectedExceptionClass, expectedExceptionMessage);
    }

    //    TODO do Tasks in BuyerAsTakerProtocol execute synchronously
    @Test
    public void onTakeOffer_firstAttemptToWriteATest_takeBuyOffer() throws Exception {
//        TODO why User is final? We cannot mock it if it's final.
//        Given
        offerToCreate = createOffer(OfferPayload.Direction.BUY, UUID.randomUUID().toString(), arbitratorA, mediatorA);
        doAnswer(invocation -> {
            ((ResultHandler) invocation.getArgument(1)).handleResult();
            return null;
        }).when(offerToCreate).checkOfferAvailability(any(), any(), any());

        OnTakeOfferParams params = getValidParams();

//        When
        Trade trade = onTakeOffer(params).get();

//        Then
//        TODO what properties should the trade have?
        Assert.assertNotNull(trade);
        Assert.assertTrue(trade instanceof SellerAsTakerTrade);
        Assert.assertNull("Trade should not have error property set", trade.getErrorMessage());
//        TODO what should happen as th e result of onTakeOffer call? What mocks should be called?
//        Following instructions can be used what calls are actually made
//        verifyNoMoreInteractions(p2pService);
//        verifyNoMoreInteractions(user);
//        verifyNoMoreInteractions(tradeStatisticsManager);
//        verifyNoMoreInteractions(arbitratorManager);
//        verifyNoMoreInteractions(btcWalletServiceMock);
//        verifyNoMoreInteractions(tradeWalletServiceMock);
    }
    @Test
    public void onTakeOffer_firstAttemptToWriteATest_takeSellOffer() throws Exception {
//        TODO why User is final? We cannot mock it if it's final.
//        Given
        offerToCreate = createOffer(OfferPayload.Direction.SELL, UUID.randomUUID().toString(), arbitratorA, mediatorA);
        doAnswer(invocation -> {
            ((ResultHandler) invocation.getArgument(1)).handleResult();
            return null;
        }).when(offerToCreate).checkOfferAvailability(any(), any(), any());

        OnTakeOfferParams params = getValidParams();

//        When
        Trade trade = onTakeOffer(params).get();

//        Then
//        TODO what properties should the trade have?
        Assert.assertNotNull(trade);
        Assert.assertTrue(trade instanceof BuyerAsTakerTrade);
        Assert.assertNull("Trade should not have error property set", trade.getErrorMessage());
//        TODO what should happen as th e result of onTakeOffer call? What mocks should be called?
//        Following instructions can be used what calls are actually made
//        verifyNoMoreInteractions(p2pService);
//        verifyNoMoreInteractions(user);
//        verifyNoMoreInteractions(tradeStatisticsManager);
//        verifyNoMoreInteractions(arbitratorManager);
//        verifyNoMoreInteractions(btcWalletServiceMock);
//        verifyNoMoreInteractions(tradeWalletServiceMock);
    }

    private void assertCompletedExceptionally(CompletableFuture<Trade> completableFuture, Class<? extends Throwable> exceptionClass, String exceptionMessage) {
        Assert.assertTrue("Should complete exceptionally", completableFuture.isCompletedExceptionally());
        try {
            completableFuture.get();
            Assert.fail("Expected exception:" + exceptionClass.getName() + " " + exceptionMessage);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            Assert.assertEquals(exceptionClass, cause.getClass());
            Assert.assertEquals(exceptionMessage, cause.getMessage());
        }
    }

    private CompletableFuture<Trade> onTakeOffer(@NotNull OnTakeOfferParams params) {
        return getTradeManager().onTakeOffer(params.amount,
                params.txFee,
                params.takerFee,
                params.isCurrencyForTakerFeeBtc,
                params.tradePrice,
                params.fundsNeededForTrade,
                params.offer,
                params.paymentAccountId,
                params.useSavingsWallet,
                params.maxFundsForTrade);
    }

    @NotNull
    private TradeManager getTradeManager() {
        TradeManager tradeManager = new TradeManager(user, keyRing, btcWalletService, bsqWalletService, tradeWalletService, openOfferManager, closedTradableManager, failedTradesManager, feeService, p2PService, preferences, priceFeedService, filterManager, tradeStatisticsManager, referralIdService, persistenceProtoResolver, accountAgeWitnessService, arbitratorManager, clock, storageDir);
        tradeManager.readPersisted();
        return tradeManager;
    }

    private OnTakeOfferParams getValidParams() {
        OnTakeOfferParams params = new OnTakeOfferParams();
        params.amount = Coin.SATOSHI;
        params.txFee = Coin.SATOSHI;
        params.takerFee = Coin.SATOSHI;
        params.isCurrencyForTakerFeeBtc = true;
        params.tradePrice = 1;

        params.fundsNeededForTrade = Coin.ZERO;
        params.offer = offerToCreate;
        params.paymentAccountId = usersPaymentAccount.getId();
        params.useSavingsWallet = false;
        return params;
    }

    private Offer createOffer(OfferPayload.Direction direction, String offerId, Arbitrator arbitrator, Mediator mediator) {
        long now = new Date().getTime();
        int price = 1;
        double marketPriceMargin = 0.1;
        boolean useMarketBasedPrice = false;
        int amount = 1;
        int minAmount = 1;
        String baseCurrencyCode = "BTC";
        String counterCurrencyCode = "USD";
        long lastBlockSeenHeight = 1;
        int txFee = 0;
        int makerFee = 0;
        boolean isCurrencyForMakerFeeBtc = false;
        int buyerSecurityDeposit = 0;
        int sellerSecurityDeposit = 0;
        int maxTradeLimit = 0;
        int maxTradePeriod = 0;
        boolean useAutoClose = false;
        boolean useReOpenAfterAutoClose = false;
        int lowerClosePrice = 0;
        int upperClosePrice = 0;
        boolean isPrivateOffer = false;
        String hashOfChallenge = null;
        Map<String, String> extraDataMap = null;
        KeyPair keyPair = Sig.generateKeyPair();
        PubKeyRing pubKeyRing = new PubKeyRing(keyPair.getPublic(), keyPair.getPublic(), null);
        List<NodeAddress> arbitrators = Collections.singletonList(arbitrator.getNodeAddress());
        List<NodeAddress> mediators = Collections.singletonList(mediator.getNodeAddress());
        OfferPayload offerPayload = new OfferPayload(offerId,
                now,
                new NodeAddress("0", 0),
                pubKeyRing,
                direction,
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
        Offer offer = new Offer(offerPayload);
        offer.setState(Offer.State.AVAILABLE);
        offer.setOfferFeePaymentTxId("abc");
//        TODO this is sick, offer should not include logic like checkOfferAvailability
        return Mockito.spy(offer);
    }

    private class OnTakeOfferParams {
        Long maxFundsForTrade;
        Coin amount;
        Coin txFee;
        Coin takerFee;
        boolean isCurrencyForTakerFeeBtc;
        long tradePrice;
        Coin fundsNeededForTrade;
        Offer offer;
        String paymentAccountId;
        boolean useSavingsWallet;
    }
}
