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

package bisq.apitest.scenario.bot;

import bisq.proto.grpc.BalancesInfo;
import bisq.proto.grpc.ConfirmPaymentReceivedRequest;
import bisq.proto.grpc.ConfirmPaymentStartedRequest;
import bisq.proto.grpc.CreateOfferRequest;
import bisq.proto.grpc.CreatePaymentAccountRequest;
import bisq.proto.grpc.GetBalancesRequest;
import bisq.proto.grpc.GetOffersRequest;
import bisq.proto.grpc.GetPaymentAccountsRequest;
import bisq.proto.grpc.GetTradeRequest;
import bisq.proto.grpc.KeepFundsRequest;
import bisq.proto.grpc.MarketPriceRequest;
import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.TakeOfferRequest;
import bisq.proto.grpc.TradeInfo;

import protobuf.PaymentAccount;

import java.text.DecimalFormat;

import java.util.List;
import java.util.function.BiPredicate;

import lombok.extern.slf4j.Slf4j;

import static org.apache.commons.lang3.StringUtils.capitalize;



import bisq.cli.GrpcStubs;

/**
 * Convenience for test bots making gRPC calls.
 *
 * Although this duplicates code in the method package, I anticipate
 * this entire bot package will move to the cli subproject.
 */
@SuppressWarnings({"JavaDoc", "unused"})
@Slf4j
public class BotClient {

    private static final DecimalFormat FIXED_PRICE_FMT = new DecimalFormat("###########0");

    private final GrpcStubs grpcStubs;

    public BotClient(GrpcStubs grpcStubs) {
        this.grpcStubs = grpcStubs;
    }

    /**
     * Returns current BSQ and BTC balance information.
     * @return BalancesInfo
     */
    public BalancesInfo getBalance() {
        var req = GetBalancesRequest.newBuilder().build();
        return grpcStubs.walletsService.getBalances(req).getBalances();
    }

    /**
     * Return the most recent BTC market price for the given currencyCode.
     * @param currencyCode
     * @return double
     */
    public double getCurrentBTCMarketPrice(String currencyCode) {
        var request = MarketPriceRequest.newBuilder().setCurrencyCode(currencyCode).build();
        return grpcStubs.priceService.getMarketPrice(request).getPrice();
    }

    /**
     * Return the most recent BTC market price for the given currencyCode as an integer string.
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
        var buyOffersRequest = GetOffersRequest.newBuilder()
                .setCurrencyCode(currencyCode)
                .setDirection("BUY").build();
        return grpcStubs.offersService.getOffers(buyOffersRequest).getOffersList();
    }

    /**
     * Return SELL offers for the given currencyCode.
     * @param currencyCode
     * @return List<OfferInfo>
     */
    public List<OfferInfo> getSellOffers(String currencyCode) {
        var buyOffersRequest = GetOffersRequest.newBuilder()
                .setCurrencyCode(currencyCode)
                .setDirection("SELL").build();
        return grpcStubs.offersService.getOffers(buyOffersRequest).getOffersList();
    }

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
        var req = CreateOfferRequest.newBuilder()
                .setPaymentAccountId(paymentAccount.getId())
                .setDirection(direction)
                .setCurrencyCode(currencyCode)
                .setAmount(amountInSatoshis)
                .setMinAmount(minAmountInSatoshis)
                .setUseMarketBasedPrice(true)
                .setMarketPriceMargin(priceMarginAsPercent)
                .setPrice("0")
                .setBuyerSecurityDeposit(securityDepositAsPercent)
                .setMakerFeeCurrencyCode(feeCurrency)
                .build();
        return grpcStubs.offersService.createOffer(req).getOffer();
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
        var req = CreateOfferRequest.newBuilder()
                .setPaymentAccountId(paymentAccount.getId())
                .setDirection(direction)
                .setCurrencyCode(currencyCode)
                .setAmount(amountInSatoshis)
                .setMinAmount(minAmountInSatoshis)
                .setUseMarketBasedPrice(false)
                .setMarketPriceMargin(0)
                .setPrice(fixedOfferPriceAsString)
                .setBuyerSecurityDeposit(securityDepositAsPercent)
                .setMakerFeeCurrencyCode(feeCurrency)
                .build();
        return grpcStubs.offersService.createOffer(req).getOffer();
    }

    public TradeInfo takeOffer(String offerId, PaymentAccount paymentAccount, String feeCurrency) {
        var req = TakeOfferRequest.newBuilder()
                .setOfferId(offerId)
                .setPaymentAccountId(paymentAccount.getId())
                .setTakerFeeCurrencyCode(feeCurrency)
                .build();
        return grpcStubs.tradesService.takeOffer(req).getTrade();
    }

    /**
     * Returns a persisted Trade with the given tradeId, or throws an exception.
     * @param tradeId
     * @return TradeInfo
     */
    public TradeInfo getTrade(String tradeId) {
        var req = GetTradeRequest.newBuilder().setTradeId(tradeId).build();
        return grpcStubs.tradesService.getTrade(req).getTrade();
    }

    /**
     * Predicate returns true if the given exception indicates the trade with the given
     * tradeId exists, but the trade's contract has not been fully prepared.
     */
    public final BiPredicate<Exception, String> tradeContractIsNotReady = (exception, tradeId) -> {
        if (exception.getMessage().contains("no contract was found")) {
            log.warn("Trade {} exists but is not fully prepared: {}.",
                    tradeId,
                    toCleanGrpcExceptionMessage(exception));
            return true;
        } else {
            return false;
        }
    };

    /**
     * Returns a trade's contract as a Json string, or null if the trade exists
     * but the contract is not ready.
     * @param tradeId
     * @return String
     */
    public String getTradeContract(String tradeId) {
        try {
            var trade = getTrade(tradeId);
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
        return getTrade(tradeId).getIsPayoutPublished();
    }

    /**
     * Returns true if the trade's taker deposit fee transaction has been confirmed.
     * @param tradeId a valid trade id
     * @return boolean
     */
    public boolean isTakerDepositFeeTxConfirmed(String tradeId) {
        return getTrade(tradeId).getIsDepositConfirmed();
    }

    /**
     * Returns true if the trade's 'start payment' message has been sent by the buyer.
     * @param tradeId a valid trade id
     * @return boolean
     */
    public boolean isTradePaymentStartedSent(String tradeId) {
        return getTrade(tradeId).getIsFiatSent();
    }

    /**
     * Returns true if the trade's 'payment received' message has been sent by the seller.
     * @param tradeId a valid trade id
     * @return boolean
     */
    public boolean isTradePaymentReceivedConfirmationSent(String tradeId) {
        return getTrade(tradeId).getIsFiatReceived();
    }

    /**
     * Returns true if the trade's payout transaction has been published.
     * @param tradeId a valid trade id
     * @return boolean
     */
    public boolean isTradePayoutTxPublished(String tradeId) {
        return getTrade(tradeId).getIsPayoutPublished();
    }

    /**
     * Sends a 'confirm payment started message' for a trade with the given tradeId,
     * or throws an exception.
     * @param tradeId
     */
    public void sendConfirmPaymentStartedMessage(String tradeId) {
        var req = ConfirmPaymentStartedRequest.newBuilder().setTradeId(tradeId).build();
        //noinspection ResultOfMethodCallIgnored
        grpcStubs.tradesService.confirmPaymentStarted(req);
    }

    /**
     * Sends a 'confirm payment received message' for a trade with the given tradeId,
     * or throws an exception.
     * @param tradeId
     */
    public void sendConfirmPaymentReceivedMessage(String tradeId) {
        var req = ConfirmPaymentReceivedRequest.newBuilder().setTradeId(tradeId).build();
        //noinspection ResultOfMethodCallIgnored
        grpcStubs.tradesService.confirmPaymentReceived(req);
    }

    /**
     * Sends a 'keep funds in wallet message' for a trade with the given tradeId,
     * or throws an exception.
     * @param tradeId
     */
    public void sendKeepFundsMessage(String tradeId) {
        var req = KeepFundsRequest.newBuilder().setTradeId(tradeId).build();
        //noinspection ResultOfMethodCallIgnored
        grpcStubs.tradesService.keepFunds(req);
    }

    /**
     * Create and save a new PaymentAccount with details in the given json.
     * @param json
     * @return PaymentAccount
     */
    public PaymentAccount createNewPaymentAccount(String json) {
        var req = CreatePaymentAccountRequest.newBuilder()
                .setPaymentAccountForm(json)
                .build();
        var paymentAccountsService = grpcStubs.paymentAccountsService;
        return paymentAccountsService.createPaymentAccount(req).getPaymentAccount();
    }

    /**
     * Returns a persisted PaymentAccount with the given paymentAccountId, or throws
     * an exception.
     * @param paymentAccountId The id of the PaymentAccount being looked up.
     * @return PaymentAccount
     */
    public PaymentAccount getPaymentAccount(String paymentAccountId) {
        var req = GetPaymentAccountsRequest.newBuilder().build();
        return grpcStubs.paymentAccountsService.getPaymentAccounts(req)
                .getPaymentAccountsList()
                .stream()
                .filter(a -> (a.getId().equals(paymentAccountId)))
                .findFirst()
                .orElseThrow(() ->
                        new PaymentAccountNotFoundException("Could not find a payment account with id "
                                + paymentAccountId + "."));
    }

    /**
     * Returns a persisted PaymentAccount with the given accountName, or throws
     * an exception.
     * @param accountName
     * @return PaymentAccount
     */
    public PaymentAccount getPaymentAccountWithName(String accountName) {
        var req = GetPaymentAccountsRequest.newBuilder().build();
        return grpcStubs.paymentAccountsService.getPaymentAccounts(req)
                .getPaymentAccountsList()
                .stream()
                .filter(a -> (a.getAccountName().equals(accountName)))
                .findFirst()
                .orElseThrow(() ->
                        new PaymentAccountNotFoundException("Could not find a payment account with name "
                                + accountName + "."));
    }

    public String toCleanGrpcExceptionMessage(Exception ex) {
        return capitalize(ex.getMessage().replaceFirst("^[A-Z_]+: ", ""));
    }
}
