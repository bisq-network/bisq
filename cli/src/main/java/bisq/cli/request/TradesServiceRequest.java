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

package bisq.cli.request;

import bisq.proto.grpc.CloseTradeRequest;
import bisq.proto.grpc.ConfirmPaymentReceivedRequest;
import bisq.proto.grpc.ConfirmPaymentStartedRequest;
import bisq.proto.grpc.FailTradeRequest;
import bisq.proto.grpc.GetTradeRequest;
import bisq.proto.grpc.GetTradesRequest;
import bisq.proto.grpc.TakeOfferReply;
import bisq.proto.grpc.TakeOfferRequest;
import bisq.proto.grpc.TradeInfo;
import bisq.proto.grpc.UnFailTradeRequest;
import bisq.proto.grpc.WithdrawFundsRequest;

import java.util.List;

import static bisq.proto.grpc.GetTradesRequest.Category.CLOSED;
import static bisq.proto.grpc.GetTradesRequest.Category.FAILED;



import bisq.cli.GrpcStubs;

public class TradesServiceRequest {

    private final GrpcStubs grpcStubs;

    public TradesServiceRequest(GrpcStubs grpcStubs) {
        this.grpcStubs = grpcStubs;
    }

    public TakeOfferReply getTakeOfferReply(String offerId, String paymentAccountId, String takerFeeCurrencyCode) {
        var request = TakeOfferRequest.newBuilder()
                .setOfferId(offerId)
                .setPaymentAccountId(paymentAccountId)
                .setTakerFeeCurrencyCode(takerFeeCurrencyCode)
                .build();
        return grpcStubs.tradesService.takeOffer(request);
    }

    public TradeInfo takeBsqSwapOffer(String offerId) {
        var reply = getTakeOfferReply(offerId, "", "");
        if (reply.hasTrade())
            return reply.getTrade();
        else
            throw new IllegalStateException(reply.getFailureReason().getDescription());
    }

    public TradeInfo takeOffer(String offerId, String paymentAccountId, String takerFeeCurrencyCode) {
        var reply = getTakeOfferReply(offerId, paymentAccountId, takerFeeCurrencyCode);
        if (reply.hasTrade())
            return reply.getTrade();
        else
            throw new IllegalStateException(reply.getFailureReason().getDescription());
    }

    public TradeInfo getTrade(String tradeId) {
        var request = GetTradeRequest.newBuilder()
                .setTradeId(tradeId)
                .build();
        return grpcStubs.tradesService.getTrade(request).getTrade();
    }

    public List<TradeInfo> getOpenTrades() {
        var request = GetTradesRequest.newBuilder()
                .build();
        return grpcStubs.tradesService.getTrades(request).getTradesList();
    }

    public List<TradeInfo> getTradeHistory(GetTradesRequest.Category category) {
        if (!category.equals(CLOSED) && !category.equals(FAILED))
            throw new IllegalStateException("unrecognized gettrades category parameter " + category.name());

        var request = GetTradesRequest.newBuilder()
                .setCategory(category)
                .build();
        return grpcStubs.tradesService.getTrades(request).getTradesList();
    }

    public void confirmPaymentStarted(String tradeId) {
        var request = ConfirmPaymentStartedRequest.newBuilder()
                .setTradeId(tradeId)
                .build();
        //noinspection ResultOfMethodCallIgnored
        grpcStubs.tradesService.confirmPaymentStarted(request);
    }

    public void confirmPaymentReceived(String tradeId) {
        var request = ConfirmPaymentReceivedRequest.newBuilder()
                .setTradeId(tradeId)
                .build();
        //noinspection ResultOfMethodCallIgnored
        grpcStubs.tradesService.confirmPaymentReceived(request);
    }

    public void closeTrade(String tradeId) {
        var request = CloseTradeRequest.newBuilder()
                .setTradeId(tradeId)
                .build();
        //noinspection ResultOfMethodCallIgnored
        grpcStubs.tradesService.closeTrade(request);
    }

    public void withdrawFunds(String tradeId, String address, String memo) {
        var request = WithdrawFundsRequest.newBuilder()
                .setTradeId(tradeId)
                .setAddress(address)
                .setMemo(memo)
                .build();
        //noinspection ResultOfMethodCallIgnored
        grpcStubs.tradesService.withdrawFunds(request);
    }

    public void failTrade(String tradeId) {
        var request = FailTradeRequest.newBuilder()
                .setTradeId(tradeId)
                .build();
        //noinspection ResultOfMethodCallIgnored
        grpcStubs.tradesService.failTrade(request);
    }

    public void unFailTrade(String tradeId) {
        var request = UnFailTradeRequest.newBuilder()
                .setTradeId(tradeId)
                .build();
        //noinspection ResultOfMethodCallIgnored
        grpcStubs.tradesService.unFailTrade(request);
    }
}
