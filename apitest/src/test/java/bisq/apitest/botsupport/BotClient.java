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

package bisq.apitest.botsupport;

import bisq.proto.grpc.AvailabilityResultWithDescription;
import bisq.proto.grpc.BalancesInfo;
import bisq.proto.grpc.GetPaymentAccountsRequest;
import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.TakeOfferReply;
import bisq.proto.grpc.TradeInfo;
import bisq.proto.grpc.TxInfo;

import protobuf.AvailabilityResult;
import protobuf.PaymentAccount;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.text.DecimalFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static bisq.apitest.botsupport.protocol.BotProtocol.BSQ;
import static bisq.apitest.botsupport.util.BotUtilities.capitalize;
import static bisq.cli.CurrencyFormat.formatBsqAmount;
import static bisq.cli.CurrencyFormat.formatMarketPrice;
import static java.lang.System.*;
import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static protobuf.OfferPayload.Direction.BUY;
import static protobuf.OfferPayload.Direction.SELL;



import bisq.apitest.botsupport.util.BotUtilities;
import bisq.cli.GrpcClient;

/**
 * Convenience GrpcClient wrapper for bots using gRPC services.
 */
@SuppressWarnings({"JavaDoc", "unused"})
@Slf4j
public class BotClient {

    private static final DecimalFormat FIXED_PRICE_FMT = new DecimalFormat("###########0");

    private static final int TAKE_OFFER_TIMEOUT_IN_SEC = 60;

    private final ListeningExecutorService takeOfferExecutor =
            BotUtilities.getListeningExecutorService("Take Offer With " + TAKE_OFFER_TIMEOUT_IN_SEC + "s Timeout",
                    1,
                    1,
                    TAKE_OFFER_TIMEOUT_IN_SEC);

    private final GrpcClient grpcClient;

    public BotClient(GrpcClient grpcClient) {
        this.grpcClient = grpcClient;
    }

    /**
     * TODO
     * @param address
     * @param amount
     */
    public void sendBsq(String address, String amount) {
        grpcClient.sendBsq(address, amount, "");
    }

    /**
     * TODO
     * @param address
     * @param amount
     * @param txFeeRate
     */
    public void sendBsq(String address, String amount, String txFeeRate) {
        grpcClient.sendBsq(address, amount, txFeeRate);
    }

    /**
     * TODO
     * @param address
     * @param amount
     */
    public void sendBtc(String address, String amount) {
        grpcClient.sendBtc(address, amount, "", "");
    }

    /**
     * TODO
     * @param address
     * @param amount
     * @param txFeeRate
     * @param memo
     */
    public void sendBtc(String address, String amount, String txFeeRate, String memo) {
        grpcClient.sendBtc(address, amount, txFeeRate, memo);
    }

    /**
     * TODO
     * @param trade
     */
    public void makeBsqPayment(TradeInfo trade) {
        var contract = trade.getContract();
        var bsqSats = trade.getOffer().getVolume();
        var sendAmountAsString = formatBsqAmount(bsqSats);
        var address = contract.getIsBuyerMakerAndSellerTaker()
                ? contract.getTakerPaymentAccountPayload().getAddress()
                : contract.getMakerPaymentAccountPayload().getAddress();
        log.info("Sending payment of {} BSQ to address {} for trade with id {}.",
                sendAmountAsString,
                address,
                trade.getTradeId());
        sendBsq(address, sendAmountAsString);
    }

    /**
     * Returns true if the specified amount of BSQ satoshis sent to an address, or throws
     * an exception.
     * @param address
     * @param amount
     * @return boolean
     */
    public boolean verifyBsqSentToAddress(String address, String amount) {
        return grpcClient.verifyBsqSentToAddress(address, amount);
    }

    /**
     * Returns current BSQ and BTC balance information.
     * @return BalancesInfo
     */
    public BalancesInfo getBalance() {
        return grpcClient.getBalances();
    }

    /**
     * Return the most recent BTC market price for the given currencyCode.
     * @param currencyCode
     * @return double
     */
    public double getCurrentBTCMarketPrice(String currencyCode) {
        return grpcClient.getBtcPrice(currencyCode);
    }

    /**
     * Return the most recent BTC market price for the given currencyCode as a string.
     * @param currencyCode
     * @return String
     */
    public String getCurrentBTCMarketPriceAsString(String currencyCode) {
        return formatMarketPrice(getCurrentBTCMarketPrice(currencyCode));
    }

    /**
     * Return the most recent BTC market price for the given currencyCode as an
     * integer string.
     * @param currencyCode
     * @return String
     */
    public String getCurrentBTCMarketPriceAsIntegerString(String currencyCode) {
        return FIXED_PRICE_FMT.format(getCurrentBTCMarketPrice(currencyCode));
    }

    /**
     * Return all BUY and SELL offers for the given currencyCode.
     * @param currencyCode
     * @return List<OfferInfo>
     */
    public List<OfferInfo> getOffers(String currencyCode) {
        var buyOffers = getBuyOffers(currencyCode);
        if (buyOffers.size() > 0) {
            return buyOffers;
        } else {
            return getSellOffers(currencyCode);
        }
    }

    /**
     * Return BUY offers for the given currencyCode.
     * @param currencyCode
     * @return List<OfferInfo>
     */
    public List<OfferInfo> getBuyOffers(String currencyCode) {
        return grpcClient.getOffers(BUY.name(), currencyCode);
    }

    /**
     * Return user created BUY offers for the given currencyCode.
     * @param currencyCode
     * @return List<OfferInfo>
     */
    public List<OfferInfo> getMyBuyOffers(String currencyCode) {
        return grpcClient.getMyOffers(BUY.name(), currencyCode);
    }


    /**
     * Return SELL offers for the given currencyCode.
     * @param currencyCode
     * @return List<OfferInfo>
     */
    public List<OfferInfo> getSellOffers(String currencyCode) {
        return grpcClient.getOffers(SELL.name(), currencyCode);
    }

    /**
     * Return user created BUY offers for the given currencyCode.
     * @param currencyCode
     * @return List<OfferInfo>
     */
    public List<OfferInfo> getMySellOffers(String currencyCode) {
        return grpcClient.getMyOffers(SELL.name(), currencyCode);
    }


    /**
     * Return all available BUY and SELL offers for the given currencyCode,
     * sorted by creation date.
     * @param currencyCode
     * @return List<OfferInfo>
     */
    public List<OfferInfo> getOffersSortedByDate(String currencyCode) {
        ArrayList<OfferInfo> offers = new ArrayList<>();
        offers.addAll(getBuyOffers(currencyCode));
        offers.addAll(getSellOffers(currencyCode));
        return grpcClient.sortOffersByDate(offers);
    }

    /**
     * Return all user created BUY and SELL offers for the given currencyCode,
     * sorted by creation date.
     * @param currencyCode
     * @return List<OfferInfo>
     */
    public List<OfferInfo> getMyOffersSortedByDate(String currencyCode) {
        ArrayList<OfferInfo> offers = new ArrayList<>();
        offers.addAll(getMyBuyOffers(currencyCode));
        offers.addAll(getMySellOffers(currencyCode));
        return grpcClient.sortOffersByDate(offers);
    }

    // TODO be more specific, i.e., (base) currencyCode=BTC,  counterCurrencyCode=BSQ ?
    public final Predicate<String> iHaveCurrentOffers = (currencyCode) ->
            !getMyBuyOffers(currencyCode).isEmpty() || !getMySellOffers(currencyCode).isEmpty();

    public final BiPredicate<String, String> iHaveCurrentOffersWithDirection = (direction, currencyCode) -> {
        if (direction.equalsIgnoreCase(BUY.name()) || direction.equalsIgnoreCase(SELL.name())) {
            return direction.equals(BUY.name())
                    ? !getMyBuyOffers(currencyCode).isEmpty()
                    : !getMySellOffers(currencyCode).isEmpty();
        } else {
            throw new IllegalStateException(direction + " is not a valid offer direction");
        }
    };

    /**
     * Create and return a new Offer using a market based price.
     * @param paymentAccount
     * @param direction
     * @param currencyCode
     * @param amountInSatoshis
     * @param minAmountInSatoshis
     * @param priceMarginAsPercent
     * @param securityDepositAsPercent
     * @param feeCurrency
     * @return OfferInfo
     */
    public OfferInfo createOfferAtMarketBasedPrice(PaymentAccount paymentAccount,
                                                   String direction,
                                                   String currencyCode,
                                                   long amountInSatoshis,
                                                   long minAmountInSatoshis,
                                                   double priceMarginAsPercent,
                                                   double securityDepositAsPercent,
                                                   String feeCurrency) {
        return grpcClient.createMarketBasedPricedOffer(direction,
                currencyCode,
                amountInSatoshis,
                minAmountInSatoshis,
                priceMarginAsPercent,
                securityDepositAsPercent,
                paymentAccount.getId(),
                feeCurrency);
    }

    /**
     * Create and return a new Offer using a fixed price.
     * @param paymentAccount
     * @param direction
     * @param currencyCode
     * @param amountInSatoshis
     * @param minAmountInSatoshis
     * @param fixedOfferPriceAsString
     * @param securityDepositAsPercent
     * @param feeCurrency
     * @return OfferInfo
     */
    public OfferInfo createOfferAtFixedPrice(PaymentAccount paymentAccount,
                                             String direction,
                                             String currencyCode,
                                             long amountInSatoshis,
                                             long minAmountInSatoshis,
                                             String fixedOfferPriceAsString,
                                             double securityDepositAsPercent,
                                             String feeCurrency) {
        return grpcClient.createFixedPricedOffer(direction,
                currencyCode,
                amountInSatoshis,
                minAmountInSatoshis,
                fixedOfferPriceAsString,
                securityDepositAsPercent,
                paymentAccount.getId(),
                feeCurrency);
    }

    /**
     * TODO
     * @param offer
     */
    public void cancelOffer(OfferInfo offer) {
        grpcClient.cancelOffer(offer.getId());
    }

    /**
     * TODO
     * @param offerId
     * @param paymentAccount
     * @param feeCurrency
     * @return
     */
    public TradeInfo takeOffer(String offerId, PaymentAccount paymentAccount, String feeCurrency) {
        return grpcClient.takeOffer(offerId, paymentAccount.getId(), feeCurrency);
    }

    /**
     * TODO
     * @param offerId
     * @param paymentAccount
     * @param feeCurrency
     * @param resultHandler
     * @param errorHandler
     */
    public void tryToTakeOffer(String offerId,
                               PaymentAccount paymentAccount,
                               String feeCurrency,
                               Consumer<TakeOfferReply> resultHandler,
                               Consumer<Throwable> errorHandler) {
        long startTime = currentTimeMillis();
        ListenableFuture<TakeOfferReply> future = takeOfferExecutor.submit(() ->
                grpcClient.getTakeOfferReply(offerId, paymentAccount.getId(), feeCurrency));
        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(TakeOfferReply result) {
                resultHandler.accept(result);

                if (result.hasTrade()) {
                    log.info("Offer {} taken in {} ms.",
                            requireNonNull(result.getTrade()).getOffer().getId(),
                            currentTimeMillis() - startTime);
                } else if (result.hasFailureReason()) {
                    var failureReason = result.getFailureReason();
                    log.warn("Offer {} could not be taken after {} ms.\n"
                                    + "\tReason: {} Description: {}",
                            offerId,
                            currentTimeMillis() - startTime,
                            failureReason.getAvailabilityResult(),
                            failureReason.getDescription());
                } else {
                    throw new IllegalStateException(
                            "programmer error: takeoffer request did not return a trade"
                                    + " or availability reason, and did not throw an exception");
                }
            }

            @Override
            public void onFailure(Throwable t) {
                errorHandler.accept(t);
            }
        }, MoreExecutors.directExecutor());
    }

    public boolean takeOfferFailedForOneOfTheseReasons(AvailabilityResultWithDescription failureReason,
                                                       AvailabilityResult... reasons) {
        if (failureReason == null)
            throw new IllegalArgumentException(
                    "AvailabilityResultWithDescription failureReason argument cannot be null.");

        if (reasons == null || reasons.length == 0)
            throw new IllegalArgumentException(
                    "AvailabilityResult reasons argument cannot be null or empty.");

        return asList(reasons).contains(failureReason.getAvailabilityResult());
    }

    /**
     * Returns a persisted Trade with the given tradeId, or throws an exception.
     * @param tradeId
     * @return TradeInfo
     */
    public TradeInfo getTrade(String tradeId) {
        return grpcClient.getTrade(tradeId);
    }

    /**
     * Predicate returns true if the given exception indicates the trade with the given
     * tradeId exists, but the trade's contract has not been fully prepared.
     */
    public final BiPredicate<Exception, String> tradeContractIsNotReady = (exception, tradeId) -> {
        if (exception.getMessage().contains("no contract was found")) {
            logTradeContractIsNotReadyWarning(tradeId, exception);
            return true;
        } else {
            return false;
        }
    };

    public void logTradeContractIsNotReadyWarning(String tradeId, Exception exception) {
        log.warn("Trade {} exists but is not fully prepared: {}.",
                tradeId,
                toCleanGrpcExceptionMessage(exception));
    }

    /**
     * Returns a trade's contract as a Json string, or null if the trade exists
     * but the contract is not ready.
     * @param tradeId
     * @return String
     */
    public String getTradeContract(String tradeId) {
        try {
            var trade = grpcClient.getTrade(tradeId);
            return trade.getContractAsJson();
        } catch (Exception ex) {
            if (tradeContractIsNotReady.test(ex, tradeId))
                return null;
            else
                throw ex;
        }
    }

    /**
     * Returns true if the trade's taker deposit fee transaction has been published.
     * @param tradeId a valid trade id
     * @return boolean
     */
    public boolean isTakerDepositFeeTxPublished(String tradeId) {
        return grpcClient.getTrade(tradeId).getIsPayoutPublished();
    }

    /**
     * Returns true if the trade's taker deposit fee transaction has been confirmed.
     * @param tradeId a valid trade id
     * @return boolean
     */
    public boolean isTakerDepositFeeTxConfirmed(String tradeId) {
        return grpcClient.getTrade(tradeId).getIsDepositConfirmed();
    }

    /**
     * Returns true if the trade's 'start payment' message has been sent by the buyer.
     * @param tradeId a valid trade id
     * @return boolean
     */
    public boolean isTradePaymentStartedSent(String tradeId) {
        return grpcClient.getTrade(tradeId).getIsFiatSent();
    }

    /**
     * Returns true if the trade's 'payment received' message has been sent by the seller.
     * @param tradeId a valid trade id
     * @return boolean
     */
    public boolean isTradePaymentReceivedConfirmationSent(String tradeId) {
        return grpcClient.getTrade(tradeId).getIsFiatReceived();
    }

    /**
     * Returns true if the trade's payout transaction has been published.
     * @param tradeId a valid trade id
     * @return boolean
     */
    public boolean isTradePayoutTxPublished(String tradeId) {
        return grpcClient.getTrade(tradeId).getIsPayoutPublished();
    }

    /**
     * Sends a 'confirm payment started message' for a trade with the given tradeId,
     * or throws an exception.
     * @param tradeId
     */
    public void sendConfirmPaymentStartedMessage(String tradeId) {
        grpcClient.confirmPaymentStarted(tradeId);
    }

    /**
     * Sends a 'confirm payment received message' for a trade with the given tradeId,
     * or throws an exception.
     * @param tradeId
     */
    public void sendConfirmPaymentReceivedMessage(String tradeId) {
        grpcClient.confirmPaymentReceived(tradeId);
    }

    /**
     * Sends a 'keep funds in wallet message' for a trade with the given tradeId,
     * or throws an exception.
     * @param tradeId
     */
    public void sendKeepFundsMessage(String tradeId) {
        grpcClient.keepFunds(tradeId);
    }

    /**
     * Create and save a new PaymentAccount with details in the given json.
     * @param json
     * @return PaymentAccount
     */
    public PaymentAccount createNewPaymentAccount(String json) {
        return grpcClient.createPaymentAccount(json);
    }

    /**
     * Returns a user's persisted PaymentAccount with the given paymentAccountId, or throws
     * an exception.
     * @param paymentAccountId The id of the PaymentAccount being looked up.
     * @return PaymentAccount
     */
    public PaymentAccount getPaymentAccount(String paymentAccountId) {
        return grpcClient.getPaymentAccounts().stream()
                .filter(a -> (a.getId().equals(paymentAccountId)))
                .findFirst()
                .orElseThrow(() ->
                        new PaymentAccountNotFoundException("Could not find a payment account with id "
                                + paymentAccountId + "."));
    }

    /**
     * Returns user's persisted PaymentAccounts.
     * @return List<PaymentAccount>
     */
    public List<PaymentAccount> getPaymentAccounts() {
        return grpcClient.getPaymentAccounts();
    }

    /**
     * Returns a persisted PaymentAccount with the given accountName, or throws
     * an exception.
     * @param accountName
     * @return PaymentAccount
     */
    public PaymentAccount getPaymentAccountWithName(String accountName) {
        var req = GetPaymentAccountsRequest.newBuilder().build();
        return grpcClient.getPaymentAccounts().stream()
                .filter(a -> (a.getAccountName().equals(accountName)))
                .findFirst()
                .orElseThrow(() ->
                        new PaymentAccountNotFoundException("Could not find a payment account with name "
                                + accountName + "."));
    }

    /**
     * TODO
     * @return PaymentAccount
     */
    public PaymentAccount createCryptoCurrencyPaymentAccount(String accountName, boolean tradeInstant) {
        String unusedBsqAddress = grpcClient.getUnusedBsqAddress();
        return grpcClient.createCryptoCurrencyPaymentAccount(accountName, BSQ, unusedBsqAddress, tradeInstant);
    }

    /**
     * TODO
     * @return PaymentAccount
     */
    public PaymentAccount createReceiverBsqPaymentAccount() {
        String unusedBsqAddress = grpcClient.getUnusedBsqAddress();
        String accountName = "Receiver BSQ Account " + unusedBsqAddress.substring(0, 8) + " ...";
        return grpcClient.createCryptoCurrencyPaymentAccount(accountName, BSQ, unusedBsqAddress, true);
    }

    /**
     * TODO
     * @return
     */
    public PaymentAccount createSenderBsqPaymentAccount() {
        String unusedBsqAddress = grpcClient.getUnusedBsqAddress();
        String accountName = "Sender BSQ Account " + unusedBsqAddress.substring(0, 8) + " ...";
        return grpcClient.createCryptoCurrencyPaymentAccount(accountName, BSQ, unusedBsqAddress, true);
    }

    /**
     * TODO
     * @return List<PaymentAccount>
     */
    public List<PaymentAccount> getReceiverBsqPaymentAccounts() {
        return getPaymentAccounts().stream()
                .filter(a -> a.getPaymentAccountPayload().hasInstantCryptoCurrencyAccountPayload())
                .filter(a -> a.getSelectedTradeCurrency().getCode().equals(BSQ))
                .filter(a -> a.getAccountName().startsWith("Receiver BSQ Account"))
                .collect(Collectors.toList());
    }

    /**
     * TODO
     * @return List<PaymentAccount>
     */
    public List<PaymentAccount> getSenderBsqPaymentAccounts() {
        return getPaymentAccounts().stream()
                .filter(a -> a.getPaymentAccountPayload().hasInstantCryptoCurrencyAccountPayload())
                .filter(a -> a.getSelectedTradeCurrency().getCode().equals(BSQ))
                .filter(a -> a.getAccountName().startsWith("Sender BSQ Account"))
                .collect(Collectors.toList());
    }

    /**
     * Returns a persisted Transaction with the given txId, or throws an exception.
     * @param txId
     * @return TxInfo
     */
    public TxInfo getTransaction(String txId) {
        return grpcClient.getTransaction(txId);
    }

    public String toCleanGrpcExceptionMessage(Exception ex) {
        return capitalize(ex.getMessage().replaceFirst("^[A-Z_]+: ", ""));
    }
}
