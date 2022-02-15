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

package bisq.core.api;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.bisq_v1.TakeOfferModel;
import bisq.core.offer.bsq_swap.BsqSwapTakeOfferModel;
import bisq.core.trade.ClosedTradableFormatter;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.bisq_v1.TradeResultHandler;
import bisq.core.trade.bisq_v1.TradeUtil;
import bisq.core.trade.bsq_swap.BsqSwapTradeManager;
import bisq.core.trade.model.Tradable;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.bisq_v1.BuyerProtocol;
import bisq.core.trade.protocol.bisq_v1.SellerProtocol;
import bisq.core.user.User;
import bisq.core.util.validation.BtcAddressValidator;

import bisq.common.handlers.ErrorMessageHandler;

import bisq.proto.grpc.GetTradesRequest;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.btc.model.AddressEntry.Context.TRADE_PAYOUT;
import static bisq.proto.grpc.GetTradesRequest.Category.CLOSED;
import static java.lang.String.format;

@Singleton
@Slf4j
class CoreTradesService {

    private final CoreContext coreContext;
    // Dependencies on core api services in this package must be kept to an absolute
    // minimum, but some trading functions require an unlocked wallet's key, so an
    // exception is made in this case.
    private final CoreWalletsService coreWalletsService;
    private final BtcWalletService btcWalletService;
    private final OfferUtil offerUtil;
    private final BsqSwapTradeManager bsqSwapTradeManager;
    private final ClosedTradableManager closedTradableManager;
    private final ClosedTradableFormatter closedTradableFormatter;
    private final FailedTradesManager failedTradesManager;
    private final TakeOfferModel takeOfferModel;
    private final BsqSwapTakeOfferModel bsqSwapTakeOfferModel;
    private final TradeManager tradeManager;
    private final TradeUtil tradeUtil;
    private final User user;

    @Inject
    public CoreTradesService(CoreContext coreContext,
                             CoreWalletsService coreWalletsService,
                             BtcWalletService btcWalletService,
                             OfferUtil offerUtil,
                             BsqSwapTradeManager bsqSwapTradeManager,
                             ClosedTradableManager closedTradableManager,
                             ClosedTradableFormatter closedTradableFormatter,
                             FailedTradesManager failedTradesManager,
                             TakeOfferModel takeOfferModel,
                             BsqSwapTakeOfferModel bsqSwapTakeOfferModel,
                             TradeManager tradeManager,
                             TradeUtil tradeUtil,
                             User user) {
        this.coreContext = coreContext;
        this.coreWalletsService = coreWalletsService;
        this.btcWalletService = btcWalletService;
        this.offerUtil = offerUtil;
        this.bsqSwapTradeManager = bsqSwapTradeManager;
        this.closedTradableManager = closedTradableManager;
        this.closedTradableFormatter = closedTradableFormatter;
        this.failedTradesManager = failedTradesManager;
        this.takeOfferModel = takeOfferModel;
        this.bsqSwapTakeOfferModel = bsqSwapTakeOfferModel;
        this.tradeManager = tradeManager;
        this.tradeUtil = tradeUtil;
        this.user = user;
    }

    // TODO We need to pass the intended trade amount, not default to the maximum.
    void takeBsqSwapOffer(Offer offer,
                          TradeResultHandler<BsqSwapTrade> tradeResultHandler,
                          ErrorMessageHandler errorMessageHandler) {
        coreWalletsService.verifyWalletsAreAvailable();
        coreWalletsService.verifyEncryptedWalletIsUnlocked();

        bsqSwapTakeOfferModel.initWithData(offer);
        bsqSwapTakeOfferModel.applyAmount(offer.getAmount());

        log.info("Initiating take {} offer, {}",
                offer.isBuyOffer() ? "buy" : "sell",
                bsqSwapTakeOfferModel);
        bsqSwapTakeOfferModel.onTakeOffer(tradeResultHandler,
                log::warn,
                errorMessageHandler,
                coreContext.isApiUser());
    }

    // TODO We need to pass the intended trade amount, not default to the maximum.
    void takeOffer(Offer offer,
                   String paymentAccountId,
                   String takerFeeCurrencyCode,
                   Consumer<Trade> resultHandler,
                   ErrorMessageHandler errorMessageHandler) {
        coreWalletsService.verifyWalletsAreAvailable();
        coreWalletsService.verifyEncryptedWalletIsUnlocked();

        offerUtil.maybeSetFeePaymentCurrencyPreference(takerFeeCurrencyCode);

        var paymentAccount = user.getPaymentAccount(paymentAccountId);
        if (paymentAccount == null)
            throw new IllegalArgumentException(format("payment account with id '%s' not found", paymentAccountId));

        var useSavingsWallet = true;

        takeOfferModel.initModel(offer, paymentAccount, useSavingsWallet);
        log.info("Initiating take {} offer, {}",
                offer.isBuyOffer() ? "buy" : "sell",
                takeOfferModel);
        //noinspection ConstantConditions
        tradeManager.onTakeOffer(offer.getAmount(),
                takeOfferModel.getTxFeeFromFeeService(),
                takeOfferModel.getTakerFee(),
                takeOfferModel.isCurrencyForTakerFeeBtc(),
                offer.getPrice().getValue(),
                takeOfferModel.getFundsNeededForTrade(),
                offer,
                paymentAccountId,
                useSavingsWallet,
                coreContext.isApiUser(),
                resultHandler::accept,
                errorMessageHandler
        );
    }

    void confirmPaymentStarted(String tradeId) {
        var trade = getTrade(tradeId);
        if (isFollowingBuyerProtocol(trade)) {
            var tradeProtocol = tradeManager.getTradeProtocol(trade);
            ((BuyerProtocol) tradeProtocol).onPaymentStarted(
                    () -> {
                    },
                    errorMessage -> {
                        throw new IllegalStateException(errorMessage);
                    }
            );
        } else {
            throw new IllegalStateException("you are the seller and not sending payment");
        }
    }

    void confirmPaymentReceived(String tradeId) {
        var trade = getTrade(tradeId);
        if (isFollowingBuyerProtocol(trade)) {
            throw new IllegalStateException("you are the buyer, and not receiving payment");
        } else {
            var tradeProtocol = tradeManager.getTradeProtocol(trade);
            ((SellerProtocol) tradeProtocol).onPaymentReceived(
                    () -> {
                    },
                    errorMessage -> {
                        throw new IllegalStateException(errorMessage);
                    }
            );
        }
    }

    void closeTrade(String tradeId) {
        coreWalletsService.verifyWalletsAreAvailable();
        coreWalletsService.verifyEncryptedWalletIsUnlocked();

        verifyTradeIsNotClosed(tradeId);
        var trade = getOpenTrade(tradeId).orElseThrow(() ->
                new IllegalArgumentException(format("trade with id '%s' not found", tradeId)));
        log.info("Closing trade {}", tradeId);
        tradeManager.onTradeCompleted(trade);
    }

    void withdrawFunds(String tradeId, String toAddress, String memo) {
        coreWalletsService.verifyWalletsAreAvailable();
        coreWalletsService.verifyEncryptedWalletIsUnlocked();

        verifyTradeIsNotClosed(tradeId);
        var trade = getOpenTrade(tradeId).orElseThrow(() ->
                new IllegalArgumentException(format("trade with id '%s' not found", tradeId)));

        verifyIsValidBTCAddress(toAddress);

        var fromAddressEntry = btcWalletService.getOrCreateAddressEntry(trade.getId(), TRADE_PAYOUT);
        verifyFundsNotWithdrawn(fromAddressEntry);

        var amount = trade.getPayoutAmount();
        var fee = getEstimatedTxFee(fromAddressEntry.getAddressString(), toAddress, amount);
        var receiverAmount = amount.subtract(fee);

        log.info(format("Withdrawing funds received from trade %s:"
                        + "%n From %s%n To %s%n Amt %s%n Tx Fee %s%n Receiver Amt %s%n Memo %s%n",
                tradeId,
                fromAddressEntry.getAddressString(),
                toAddress,
                amount.toFriendlyString(),
                fee.toFriendlyString(),
                receiverAmount.toFriendlyString(),
                memo));
        tradeManager.onWithdrawRequest(
                toAddress,
                amount,
                fee,
                coreWalletsService.getKey(),
                trade,
                memo.isEmpty() ? null : memo,
                () -> {
                },
                (errorMessage, throwable) -> {
                    log.error(errorMessage, throwable);
                    throw new IllegalStateException(errorMessage, throwable);
                });
    }

    TradeModel getTradeModel(String tradeId) {
        coreWalletsService.verifyWalletsAreAvailable();
        coreWalletsService.verifyEncryptedWalletIsUnlocked();

        Optional<Trade> openTrade = getOpenTrade(tradeId);
        if (openTrade.isPresent())
            return openTrade.get();

        Optional<Trade> closedTrade = getClosedTrade(tradeId);
        if (closedTrade.isPresent())
            return closedTrade.get();

        return tradeManager.findBsqSwapTradeById(tradeId).orElseThrow(() ->
                new IllegalArgumentException(format("trade with id '%s' not found", tradeId)));
    }

    String getTradeRole(TradeModel tradeModel) {
        coreWalletsService.verifyWalletsAreAvailable();
        coreWalletsService.verifyEncryptedWalletIsUnlocked();
        var isBsqSwapTrade = tradeModel instanceof BsqSwapTrade;
        try {
            return isBsqSwapTrade
                    ? tradeUtil.getRole((BsqSwapTrade) tradeModel)
                    : tradeUtil.getRole((Trade) tradeModel);
        } catch (Exception ex) {
            log.error("Role not found for trade with Id {}.", tradeModel.getId(), ex);
            return "Not Available";
        }
    }

    Trade getTrade(String tradeId) {
        coreWalletsService.verifyWalletsAreAvailable();
        coreWalletsService.verifyEncryptedWalletIsUnlocked();
        return getOpenTrade(tradeId).orElseGet(() ->
                getClosedTrade(tradeId).orElseThrow(() ->
                        new IllegalArgumentException(format("trade with id '%s' not found", tradeId))
                ));
    }

    List<TradeModel> getOpenTrades() {
        coreWalletsService.verifyWalletsAreAvailable();
        coreWalletsService.verifyEncryptedWalletIsUnlocked();
        return tradeManager.getTrades().stream().collect(Collectors.toList());
    }

    List<TradeModel> getTradeHistory(GetTradesRequest.Category category) {
        coreWalletsService.verifyWalletsAreAvailable();
        coreWalletsService.verifyEncryptedWalletIsUnlocked();
        if (category.equals(CLOSED)) {
            var closedTrades = closedTradableManager.getClosedTrades().stream()
                    .map(t -> (TradeModel) t)
                    .collect(Collectors.toList());
            closedTrades.addAll(bsqSwapTradeManager.getBsqSwapTrades());
            return closedTrades;
        } else {
            var failedV1Trades = failedTradesManager.getTrades();
            return failedV1Trades.stream().collect(Collectors.toList());
        }
    }

    void failTrade(String tradeId) {
        // TODO Recommend API users call this method with extra care because
        //  the API lacks methods for diagnosing trade problems, and does not support
        //  interaction with mediators.  Users may accidentally fail valid trades,
        //  although they can easily be un-failed with the 'unfailtrade' method.
        coreWalletsService.verifyWalletsAreAvailable();
        coreWalletsService.verifyEncryptedWalletIsUnlocked();

        var trade = getTrade(tradeId);
        tradeManager.onMoveInvalidTradeToFailedTrades(trade);
        log.info("Trade {} changed to failed trade.", tradeId);
    }

    void unFailTrade(String tradeId) {
        coreWalletsService.verifyWalletsAreAvailable();
        coreWalletsService.verifyEncryptedWalletIsUnlocked();

        failedTradesManager.getTradeById(tradeId).ifPresentOrElse(failedTrade -> {
            verifyCanUnfailTrade(failedTrade);
            failedTradesManager.removeTrade(failedTrade);
            tradeManager.addFailedTradeToPendingTrades(failedTrade);
            log.info("Failed trade {} changed to open trade.", tradeId);
        }, () -> {
            throw new IllegalArgumentException(format("failed trade '%s' not found", tradeId));
        });
    }

    List<OpenOffer> getCanceledOpenOffers() {
        return closedTradableManager.getCanceledOpenOffers();
    }

    String getClosedTradeStateAsString(Tradable tradable) {
        return closedTradableFormatter.getStateAsString(tradable);
    }

    private Optional<Trade> getOpenTrade(String tradeId) {
        return tradeManager.getTradeById(tradeId);
    }

    private Optional<Trade> getClosedTrade(String tradeId) {
        Optional<Tradable> tradable = closedTradableManager.getTradableById(tradeId);
        return tradable.filter((t) -> t instanceof Trade).map(value -> (Trade) value);
    }

    private boolean isFollowingBuyerProtocol(Trade trade) {
        return tradeManager.getTradeProtocol(trade) instanceof BuyerProtocol;
    }

    private Coin getEstimatedTxFee(String fromAddress, String toAddress, Coin amount) {
        // TODO This and identical logic should be refactored into TradeUtil.
        try {
            return btcWalletService.getFeeEstimationTransaction(fromAddress,
                    toAddress,
                    amount,
                    TRADE_PAYOUT).getFee();
        } catch (Exception ex) {
            log.error("", ex);
            throw new IllegalStateException(format("could not estimate tx fee: %s", ex.getMessage()));
        }
    }

    // Throws a RuntimeException trade is already closed.
    private void verifyTradeIsNotClosed(String tradeId) {
        if (getClosedTrade(tradeId).isPresent())
            throw new IllegalArgumentException(format("trade '%s' is already closed", tradeId));
    }

    // Throws a RuntimeException if address is not valid.
    private void verifyIsValidBTCAddress(String address) {
        try {
            new BtcAddressValidator().validate(address);
        } catch (Throwable t) {
            log.error("", t);
            throw new IllegalArgumentException(format("'%s' is not a valid btc address", address));
        }
    }

    // Throws a RuntimeException if address has a zero balance.
    private void verifyFundsNotWithdrawn(AddressEntry fromAddressEntry) {
        Coin fromAddressBalance = btcWalletService.getBalanceForAddress(fromAddressEntry.getAddress());
        if (fromAddressBalance.isZero())
            throw new IllegalStateException(format("funds already withdrawn from address '%s'",
                    fromAddressEntry.getAddressString()));
    }

    // Throws a RuntimeException if failed trade cannot be changed to OPEN for any reason.
    private void verifyCanUnfailTrade(Trade failedTrade) {
        if (tradeUtil.getTradeAddresses(failedTrade) == null)
            throw new IllegalStateException(
                    format("cannot change failed trade to open because no trade addresses found for '%s'",
                            failedTrade.getId()));

        if (!failedTradesManager.hasDepositTx(failedTrade))
            throw new IllegalStateException(
                    format("cannot change failed trade to open, no deposit tx found for '%s'",
                            failedTrade.getId()));

        if (!failedTradesManager.hasDelayedPayoutTxBytes(failedTrade))
            throw new IllegalStateException(
                    format("cannot change failed trade to open, no delayed payout tx found for '%s'",
                            failedTrade.getId()));

        failedTradesManager.getBlockingTradeIds(failedTrade).ifPresent(tradeIds -> {
            throw new IllegalStateException(
                    format("cannot change failed trade '%s' to open at this time,"
                                    + "%ntry again after completing trade(s):%n\t%s",
                            failedTrade.getId(),
                            String.join(", ", tradeIds)));
        });
    }
}
