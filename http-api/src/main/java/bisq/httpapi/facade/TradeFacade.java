package bisq.httpapi.facade;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.trade.BuyerAsMakerTrade;
import bisq.core.trade.SellerAsMakerTrade;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.trade.protocol.BuyerAsMakerProtocol;
import bisq.core.trade.protocol.BuyerAsTakerProtocol;
import bisq.core.trade.protocol.SellerAsMakerProtocol;
import bisq.core.trade.protocol.SellerAsTakerProtocol;
import bisq.core.trade.protocol.TradeProtocol;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import javax.inject.Inject;

import javafx.collections.ObservableList;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static bisq.httpapi.facade.FacadeUtil.failFuture;



import bisq.httpapi.exceptions.NotFoundException;
import javax.validation.ValidationException;

public class TradeFacade {

    private final BtcWalletService btcWalletService;
    private final TradeManager tradeManager;

    @Inject
    public TradeFacade(BtcWalletService btcWalletService, TradeManager tradeManager) {
        this.btcWalletService = btcWalletService;
        this.tradeManager = tradeManager;
    }

    public List<Trade> getTradeList() {
        final ObservableList<Trade> tradableList = tradeManager.getTradableList();
        if (null != tradableList) return tradableList.sorted();
        return Collections.emptyList();
    }

    public Trade getTrade(String tradeId) {
        final String safeTradeId = (null == tradeId) ? "" : tradeId;
        final Optional<Trade> tradeOptional = getTradeList().stream().filter(item -> safeTradeId.equals(item.getId())).findAny();
        if (!tradeOptional.isPresent()) {
            throw new NotFoundException("Trade not found: " + tradeId);
        }
        return tradeOptional.get();
    }

    public CompletableFuture<Void> paymentStarted(String tradeId) {
        final CompletableFuture<Void> futureResult = new CompletableFuture<>();
        Trade trade;
        try {
            trade = getTrade(tradeId);
        } catch (NotFoundException e) {
            return failFuture(futureResult, e);
        }

        if (!Trade.State.DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN.equals(trade.getState())) {
            return failFuture(futureResult, new ValidationException("Trade is not in the correct state to start payment: " + trade.getState()));
        }
        TradeProtocol tradeProtocol = trade.getTradeProtocol();
        ResultHandler resultHandler = () -> futureResult.complete(null);
        ErrorMessageHandler errorResultHandler = message -> futureResult.completeExceptionally(new RuntimeException(message));

        if (trade instanceof BuyerAsMakerTrade) {
            ((BuyerAsMakerProtocol) tradeProtocol).onFiatPaymentStarted(resultHandler, errorResultHandler);
        } else {
            ((BuyerAsTakerProtocol) tradeProtocol).onFiatPaymentStarted(resultHandler, errorResultHandler);
        }
        return futureResult;
    }

    public CompletableFuture<Void> paymentReceived(String tradeId) {
        final CompletableFuture<Void> futureResult = new CompletableFuture<>();
        Trade trade;
        try {
            trade = getTrade(tradeId);
        } catch (NotFoundException e) {
            return failFuture(futureResult, e);
        }

        if (!Trade.State.SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG.equals(trade.getState())) {
            return failFuture(futureResult, new ValidationException("Trade is not in the correct state to receive payment: " + trade.getState()));
        }
        TradeProtocol tradeProtocol = trade.getTradeProtocol();

        if (!(tradeProtocol instanceof SellerAsTakerProtocol || tradeProtocol instanceof SellerAsMakerProtocol)) {
            return failFuture(futureResult, new ValidationException("Trade is not in the correct state to receive payment: " + trade.getState()));
        }

        ResultHandler resultHandler = () -> futureResult.complete(null);
        ErrorMessageHandler errorResultHandler = message -> futureResult.completeExceptionally(new RuntimeException(message));

//        TODO I think we should check instance of tradeProtocol here instead of trade
        if (trade instanceof SellerAsMakerTrade) {
            ((SellerAsMakerProtocol) tradeProtocol).onFiatPaymentReceived(resultHandler, errorResultHandler);
        } else {
            ((SellerAsTakerProtocol) tradeProtocol).onFiatPaymentReceived(resultHandler, errorResultHandler);
        }
        return futureResult;
    }

    public void moveFundsToBisqWallet(String tradeId) {
        final Trade trade = getTrade(tradeId);
        final Trade.State tradeState = trade.getState();
        if (!Trade.State.SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG.equals(tradeState) && !Trade.State.BUYER_RECEIVED_PAYOUT_TX_PUBLISHED_MSG.equals(tradeState))
            throw new ValidationException("Trade is not in the correct state to transfer funds out: " + tradeState);
        btcWalletService.swapTradeEntryToAvailableEntry(trade.getId(), AddressEntry.Context.TRADE_PAYOUT);
        // TODO do we need to handle this ui stuff? --> handleTradeCompleted();
        tradeManager.addTradeToClosedTrades(trade);
    }
}
