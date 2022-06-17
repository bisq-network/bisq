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

package bisq.daemon.grpc;

import bisq.core.api.CoreApi;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;

import bisq.common.util.Tuple2;

import bisq.proto.grpc.AverageBsqTradePrice;
import bisq.proto.grpc.GetAverageBsqTradePriceReply;
import bisq.proto.grpc.GetAverageBsqTradePriceRequest;
import bisq.proto.grpc.MarketPriceReply;
import bisq.proto.grpc.MarketPriceRequest;

import io.grpc.ServerInterceptor;
import io.grpc.stub.StreamObserver;

import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.util.HashMap;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static bisq.daemon.grpc.interceptor.GrpcServiceRateMeteringConfig.getCustomRateMeteringInterceptor;
import static bisq.proto.grpc.PriceGrpc.PriceImplBase;
import static bisq.proto.grpc.PriceGrpc.getGetAverageBsqTradePriceMethod;
import static bisq.proto.grpc.PriceGrpc.getGetMarketPriceMethod;
import static java.util.concurrent.TimeUnit.SECONDS;



import bisq.daemon.grpc.interceptor.CallRateMeteringInterceptor;
import bisq.daemon.grpc.interceptor.GrpcCallRateMeter;

@Slf4j
class GrpcPriceService extends PriceImplBase {

    private final CoreApi coreApi;
    private final GrpcExceptionHandler exceptionHandler;

    @Inject
    public GrpcPriceService(CoreApi coreApi, GrpcExceptionHandler exceptionHandler) {
        this.coreApi = coreApi;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void getMarketPrice(MarketPriceRequest req,
                               StreamObserver<MarketPriceReply> responseObserver) {
        try {
            coreApi.getMarketPrice(req.getCurrencyCode(),
                    price -> {
                        var reply = MarketPriceReply.newBuilder().setPrice(price).build();
                        responseObserver.onNext(reply);
                        responseObserver.onCompleted();
                    });
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    @Override
    public void getAverageBsqTradePrice(GetAverageBsqTradePriceRequest req,
                                        StreamObserver<GetAverageBsqTradePriceReply> responseObserver) {
        try {
            var days = req.getDays();
            Tuple2<Price, Price> prices = coreApi.getAverageBsqTradePrice(days);
            var usdPrice = new BigDecimal(prices.first.toString())
                    .setScale(Fiat.SMALLEST_UNIT_EXPONENT, RoundingMode.HALF_UP);
            var btcPrice = new BigDecimal(prices.second.toString())
                    .setScale(Altcoin.SMALLEST_UNIT_EXPONENT, RoundingMode.HALF_UP);
            var proto = AverageBsqTradePrice.newBuilder()
                    .setUsdPrice(usdPrice.toString())
                    .setBtcPrice(btcPrice.toString())
                    .build();
            var reply = GetAverageBsqTradePriceReply.newBuilder()
                    .setPrice(proto)
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        } catch (Throwable cause) {
            exceptionHandler.handleException(log, cause, responseObserver);
        }
    }

    final ServerInterceptor[] interceptors() {
        Optional<ServerInterceptor> rateMeteringInterceptor = rateMeteringInterceptor();
        return rateMeteringInterceptor.map(serverInterceptor ->
                new ServerInterceptor[]{serverInterceptor}).orElseGet(() -> new ServerInterceptor[0]);
    }

    final Optional<ServerInterceptor> rateMeteringInterceptor() {
        return getCustomRateMeteringInterceptor(coreApi.getConfig().appDataDir, this.getClass())
                .or(() -> Optional.of(CallRateMeteringInterceptor.valueOf(
                        new HashMap<>() {{
                            put(getGetMarketPriceMethod().getFullMethodName(), new GrpcCallRateMeter(1, SECONDS));
                            put(getGetAverageBsqTradePriceMethod().getFullMethodName(), new GrpcCallRateMeter(1, SECONDS));
                        }}
                )));
    }
}
