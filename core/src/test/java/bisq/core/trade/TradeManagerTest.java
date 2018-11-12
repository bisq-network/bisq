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
import bisq.core.payment.AliPayAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.closed.ClosedTradableManager;
import bisq.core.trade.failed.FailedTradesManager;
import bisq.core.trade.handlers.TradeResultHandler;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.User;
import bisq.core.user.UserPayload;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.crypto.KeyRing;
import bisq.common.crypto.Sig;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.storage.Storage;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;

import javafx.collections.FXCollections;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Collections;

import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TradeManagerTest {

    @Test
    public void onTakeOffer_firstAttemptToWriteATest() throws Exception {
        PaymentMethod.onAllServicesInitialized();
        final String paymentAccountId = "paymentAccountId";
        final P2PService p2pServiceMock = mock(P2PService.class);
        final KeyRing keyRingMock = mock(KeyRing.class);
        when(keyRingMock.getSignatureKeyPair()).thenReturn(Sig.generateKeyPair());
        final Storage<UserPayload> storageMock = mock(Storage.class);
        final UserPayload userPayload = mock(UserPayload.class);
        final NodeAddress arbitratorNodeAddress = new NodeAddress("a", 0);
        final NodeAddress mediatorNodeAddress = new NodeAddress("m", 0);
        final Arbitrator arbitrator = new Arbitrator(arbitratorNodeAddress, null, null, null, null, 0, null, null, null, null, null);
        final Mediator mediator = new Mediator(mediatorNodeAddress, null, null, 0, null, null, null, null, null);
        when(userPayload.getAcceptedArbitrators()).thenReturn(Collections.singletonList(arbitrator));
        when(storageMock.initAndGetPersistedWithFileName(any(), anyLong())).thenReturn(userPayload);
        final User user = mock(User.class);
        when(user.getAcceptedArbitratorAddresses()).thenReturn(Collections.singletonList(arbitratorNodeAddress));
        when(user.getAcceptedArbitratorByAddress(arbitratorNodeAddress)).thenReturn(arbitrator);
        when(user.getAcceptedMediatorByAddress(mediatorNodeAddress)).thenReturn(mediator);
        when(user.getAcceptedMediatorAddresses()).thenReturn(Collections.singletonList(mediatorNodeAddress));
        final AliPayAccount paymentAccount = new AliPayAccount();
        paymentAccount.init();
        when(user.getPaymentAccount(paymentAccountId)).thenReturn(paymentAccount);
        final TradeStatisticsManager tradeStatisticsManagerMock = mock(TradeStatisticsManager.class);
        when(tradeStatisticsManagerMock.getObservableTradeStatisticsSet()).thenReturn(FXCollections.observableSet());
        final ArbitratorManager arbitratorManager = mock(ArbitratorManager.class);
        when(arbitratorManager.getArbitratorsObservableMap()).thenReturn(FXCollections.observableMap(Collections.singletonMap(arbitratorNodeAddress, arbitrator)));
        final String offerId = "offerId";
        final BtcWalletService btcWalletService = mock(BtcWalletService.class);
        final AddressEntry addressEntry = new AddressEntry(mock(DeterministicKey.class), AddressEntry.Context.OFFER_FUNDING);
        when(btcWalletService.getOrCreateAddressEntry(matches(offerId), any())).thenReturn(addressEntry);
        when(btcWalletService.getFreshAddressEntry()).thenReturn(addressEntry);
        final TradeWalletService tradeWalletService = mock(TradeWalletService.class);

        final Path tradeManagerTest = Files.createTempDirectory("tradeManagerTest");
        final TradeManager tradeManager = new TradeManager(user, keyRingMock, btcWalletService, null, tradeWalletService, null, mock(ClosedTradableManager.class), mock(FailedTradesManager.class), p2pServiceMock, null, null, tradeStatisticsManagerMock, null, null, null, arbitratorManager, null, tradeManagerTest.toFile());
        final ErrorMessageHandler errorMessageHandler = mock(ErrorMessageHandler.class);
        final Offer offer = mock(Offer.class);
        when(offer.getId()).thenReturn(offerId);
        when(offer.getState()).thenReturn(Offer.State.AVAILABLE);
        when(offer.getCurrencyCode()).thenReturn("BTC");
        when(offer.getPaymentMethod()).thenReturn(new PaymentMethod("X", 0, Coin.ZERO));
        when(offer.getArbitratorNodeAddresses()).thenReturn(Collections.singletonList(arbitratorNodeAddress));
        when(offer.getMediatorNodeAddresses()).thenReturn(Collections.singletonList(mediatorNodeAddress));
        when(offer.getBuyerSecurityDeposit()).thenReturn(Coin.ZERO);
        doAnswer(invocation -> {
            ((ResultHandler) invocation.getArgument(1)).handleResult();
            return null;
        }).when(offer).checkOfferAvailability(any(), any(), any());
        when(tradeWalletService.createBtcTradingFeeTx(any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any())).thenAnswer(invocation -> {
            final Transaction transaction = mock(Transaction.class);
            when(transaction.getHashAsString()).thenReturn("transactionHashAsString");
            final TxBroadcaster.Callback callback = invocation.getArgument(8);
            callback.onSuccess(transaction);
            return transaction;
        });
        when(tradeWalletService.takerCreatesDepositsTxInputs(any(), any(), any(), any())).thenReturn(new InputsAndChangeOutput(Collections.singletonList(new RawTransactionInput(0, null, 0)), 0, null));
        final Coin txFee = Coin.ZERO;
        final Coin takerFee = Coin.ZERO;
        final Coin amount = Coin.SATOSHI;
        final Coin fundsNeededForTrade = Coin.ZERO;
        tradeManager.readPersisted();
        final TradeResultHandler tradeResultHandlerMock = mock(TradeResultHandler.class);
        tradeManager.onTakeOffer(amount, txFee, takerFee, true, 1, fundsNeededForTrade, offer, paymentAccountId, false, tradeResultHandlerMock, errorMessageHandler);
        verify(tradeResultHandlerMock).handleResult(any(Trade.class));
    }
}
