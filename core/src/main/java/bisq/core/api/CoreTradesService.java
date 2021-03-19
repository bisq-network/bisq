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
import bisq.core.offer.takeoffer.TakeOfferModel;
import bisq.core.trade.Tradable;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.trade.TradeUtil;
import bisq.core.trade.closed.ClosedTradableManager;
import bisq.core.trade.protocol.BuyerProtocol;
import bisq.core.trade.protocol.SellerProtocol;
import bisq.core.user.User;
import bisq.core.util.validation.BtcAddressValidator;

import bisq.common.handlers.ErrorMessageHandler;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.Optional;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.btc.model.AddressEntry.Context.TRADE_PAYOUT;
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
    private final ClosedTradableManager closedTradableManager;
    private final TakeOfferModel takeOfferModel;
    private final TradeManager tradeManager;
    private final TradeUtil tradeUtil;
    private final User user;

    @Inject
    public CoreTradesService(CoreContext coreContext,
                             CoreWalletsService coreWalletsService,
                             BtcWalletService btcWalletService,
                             OfferUtil offerUtil,
                             ClosedTradableManager closedTradableManager,
                             TakeOfferModel takeOfferModel,
                             TradeManager tradeManager,
                             TradeUtil tradeUtil,
                             User user) {
        this.coreContext = coreContext;
        this.coreWalletsService = coreWalletsService;
        this.btcWalletService = btcWalletService;
        this.offerUtil = offerUtil;
        this.closedTradableManager = closedTradableManager;
        this.takeOfferModel = takeOfferModel;
        this.tradeManager = tradeManager;
        this.tradeUtil = tradeUtil;
        this.user = user;
    }

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
        //noinspection ConstantConditions
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

    void keepFunds(String tradeId) {
        coreWalletsService.verifyWalletsAreAvailable();
        coreWalletsService.verifyEncryptedWalletIsUnlocked();

        verifyTradeIsNotClosed(tradeId);
        var trade = getOpenTrade(tradeId).orElseThrow(() ->
                new IllegalArgumentException(format("trade with id '%s' not found", tradeId)));
        log.info("Keeping funds received from trade {}", tradeId);
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

    String getTradeRole(String tradeId) {
        coreWalletsService.verifyWalletsAreAvailable();
        coreWalletsService.verifyEncryptedWalletIsUnlocked();
        return tradeUtil.getRole(getTrade(tradeId));
    }

    Trade getTrade(String tradeId) {
        coreWalletsService.verifyWalletsAreAvailable();
        coreWalletsService.verifyEncryptedWalletIsUnlocked();
        return getOpenTrade(tradeId).orElseGet(() ->
                getClosedTrade(tradeId).orElseThrow(() ->
                        new IllegalArgumentException(format("trade with id '%s' not found", tradeId))
                ));
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
}
