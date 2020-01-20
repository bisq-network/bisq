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

package bisq.cli.app;

import bisq.core.grpc.GetBalanceGrpc;
import bisq.core.grpc.GetBalanceRequest;
import bisq.core.grpc.GetOffersGrpc;
import bisq.core.grpc.GetOffersRequest;
import bisq.core.grpc.GetPaymentAccountsGrpc;
import bisq.core.grpc.GetPaymentAccountsRequest;
import bisq.core.grpc.GetTradeStatisticsGrpc;
import bisq.core.grpc.GetTradeStatisticsRequest;
import bisq.core.grpc.GetVersionGrpc;
import bisq.core.grpc.GetVersionRequest;
import bisq.core.grpc.PlaceOfferGrpc;
import bisq.core.grpc.PlaceOfferRequest;
import bisq.core.grpc.StopServerGrpc;
import bisq.core.grpc.StopServerRequest;
import bisq.core.payment.PaymentAccount;
import bisq.core.proto.network.CoreNetworkProtoResolver;
import bisq.core.proto.persistable.CorePersistenceProtoResolver;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import org.bitcoinj.core.Coin;

import java.time.Clock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;

/**
 * gRPC client.
 *
 * FIXME We get warning 'DEBUG io.grpc.netty.shaded.io.netty.util.internal.PlatformDependent0 - direct buffer constructor: unavailable
 * java.lang.UnsupportedOperationException: Reflective setAccessible(true) disabled' which is
 * related to Java 10 changes. Requests are working but we should find out why we get that warning
 */
@Slf4j
public class BisqCliMain {

    private final ManagedChannel channel;
    private final GetVersionGrpc.GetVersionBlockingStub getVersionStub;
    private final GetBalanceGrpc.GetBalanceBlockingStub getBalanceStub;
    private final StopServerGrpc.StopServerBlockingStub stopServerStub;
    private final GetTradeStatisticsGrpc.GetTradeStatisticsBlockingStub getTradeStatisticsStub;
    private final GetOffersGrpc.GetOffersBlockingStub getOffersStub;
    private final GetPaymentAccountsGrpc.GetPaymentAccountsBlockingStub getPaymentAccountsStub;
    private final PlaceOfferGrpc.PlaceOfferBlockingStub placeOfferBlockingStub;
    private final CorePersistenceProtoResolver corePersistenceProtoResolver;

    public static void main(String[] args) throws Exception {
        new BisqCliMain("localhost", 8888);
    }

    private BisqCliMain(String host, int port) {
        this(ManagedChannelBuilder.forAddress(host, port)
                // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
                // needing certificates.
                .usePlaintext(true).build());

        // Simple input scanner
        // TODO use some more sophisticated input processing with validation....
        try (Scanner scanner = new Scanner(System.in);) {
            while (true) {
                long startTs = System.currentTimeMillis();

                String[] tokens = scanner.nextLine().split(" ");
                if (tokens.length == 0) {
                    return;
                }
                String command = tokens[0];
                List<String> params = new ArrayList<>();
                if (tokens.length > 1) {
                    params.addAll(Arrays.asList(tokens));
                    params.remove(0);
                }
                String result = "";

                switch (command) {
                    case "getVersion":
                        result = getVersion();
                        break;
                    case "getBalance":
                        result = Coin.valueOf(getBalance()).toFriendlyString();
                        break;
                    case "getTradeStatistics":
                        List<bisq.core.trade.statistics.TradeStatistics2> tradeStatistics = getTradeStatistics().stream()
                                .map(bisq.core.trade.statistics.TradeStatistics2::fromProto)
                                .collect(Collectors.toList());

                        result = tradeStatistics.toString();
                        break;
                    case "getOffers":
                        List<bisq.core.offer.Offer> offers = getOffers().stream()
                                .map(bisq.core.offer.Offer::fromProto)
                                .collect(Collectors.toList());
                        result = offers.toString();
                        break;
                    case "getPaymentAccounts":
                        List<PaymentAccount> paymentAccounts = getPaymentAccounts().stream()
                                .map(proto -> PaymentAccount.fromProto(proto, corePersistenceProtoResolver))
                                .collect(Collectors.toList());
                        result = paymentAccounts.toString();
                        break;
                    case "placeOffer":
                        // test input: placeOffer CNY BUY 750000000 true -0.2251 1000000 500000 0.15 5a972121-c30a-4b0e-b519-b17b63795d16
                        // payment accountId and currency need to be adopted

                        // We expect 9 params
                        // TODO add basic input validation
                        try {
                            checkArgument(params.size() == 9);
                            String currencyCode = params.get(0);
                            String directionAsString = params.get(1);
                            long priceAsLong = Long.parseLong(params.get(2));
                            boolean useMarketBasedPrice = Boolean.parseBoolean(params.get(3));
                            double marketPriceMargin = Double.parseDouble(params.get(4));
                            long amountAsLong = Long.parseLong(params.get(5));
                            long minAmountAsLong = Long.parseLong(params.get(6));
                            double buyerSecurityDeposit = Double.parseDouble(params.get(7));
                            String paymentAccountId = params.get(8);
                            boolean success = placeOffer(currencyCode,
                                    directionAsString,
                                    priceAsLong,
                                    useMarketBasedPrice,
                                    marketPriceMargin,
                                    amountAsLong,
                                    minAmountAsLong,
                                    buyerSecurityDeposit,
                                    paymentAccountId);
                            result = String.valueOf(success);
                            break;
                        } catch (Throwable t) {
                            log.error(t.toString(), t);
                            break;
                        }
                    case "stop":
                        result = "Shut down client";
                        try {
                            shutdown();
                        } catch (InterruptedException e) {
                            log.error(e.toString(), e);
                        }
                        break;
                    case "stopServer":
                        stopServer();
                        result = "Server stopped";
                        break;
                    default:
                        result = format("Unknown command '%s'", command);
                }

                // First response is rather slow (300 ms) but following responses are fast (3-5 ms).
                log.info("Request took: {} ms", System.currentTimeMillis() - startTs);
                log.info(result);
            }
        }
    }

    /**
     * Construct client for accessing server using the existing channel.
     */
    private BisqCliMain(ManagedChannel channel) {
        this.channel = channel;

        getVersionStub = GetVersionGrpc.newBlockingStub(channel);
        getBalanceStub = GetBalanceGrpc.newBlockingStub(channel);
        getTradeStatisticsStub = GetTradeStatisticsGrpc.newBlockingStub(channel);
        getOffersStub = GetOffersGrpc.newBlockingStub(channel);
        getPaymentAccountsStub = GetPaymentAccountsGrpc.newBlockingStub(channel);
        placeOfferBlockingStub = PlaceOfferGrpc.newBlockingStub(channel);
        stopServerStub = StopServerGrpc.newBlockingStub(channel);

        CoreNetworkProtoResolver coreNetworkProtoResolver = new CoreNetworkProtoResolver(Clock.systemDefaultZone());
        //TODO
        corePersistenceProtoResolver = new CorePersistenceProtoResolver(null, coreNetworkProtoResolver, null, null);
    }

    private String getVersion() {
        GetVersionRequest request = GetVersionRequest.newBuilder().build();
        try {
            return getVersionStub.getVersion(request).getVersion();
        } catch (StatusRuntimeException e) {
            return "RPC failed: " + e.getStatus();
        }
    }

    private long getBalance() {
        GetBalanceRequest request = GetBalanceRequest.newBuilder().build();
        try {
            return getBalanceStub.getBalance(request).getBalance();
        } catch (StatusRuntimeException e) {
            log.warn("RPC failed: {}", e.getStatus());
            return -1;
        }
    }

    private List<protobuf.TradeStatistics2> getTradeStatistics() {
        GetTradeStatisticsRequest request = GetTradeStatisticsRequest.newBuilder().build();
        try {
            return getTradeStatisticsStub.getTradeStatistics(request).getTradeStatisticsList();
        } catch (StatusRuntimeException e) {
            log.warn("RPC failed: {}", e.getStatus());
            return null;
        }
    }

    private List<protobuf.Offer> getOffers() {
        GetOffersRequest request = GetOffersRequest.newBuilder().build();
        try {
            return getOffersStub.getOffers(request).getOffersList();
        } catch (StatusRuntimeException e) {
            log.warn("RPC failed: {}", e.getStatus());
            return null;
        }
    }

    private List<protobuf.PaymentAccount> getPaymentAccounts() {
        GetPaymentAccountsRequest request = GetPaymentAccountsRequest.newBuilder().build();
        try {
            return getPaymentAccountsStub.getPaymentAccounts(request).getPaymentAccountsList();
        } catch (StatusRuntimeException e) {
            log.warn("RPC failed: {}", e.getStatus());
            return null;
        }
    }

    private boolean placeOffer(String currencyCode,
                               String directionAsString,
                               long priceAsLong,
                               boolean useMarketBasedPrice,
                               double marketPriceMargin,
                               long amountAsLong,
                               long minAmountAsLong,
                               double buyerSecurityDeposit,
                               String paymentAccountId) {
        PlaceOfferRequest request = PlaceOfferRequest.newBuilder()
                .setCurrencyCode(currencyCode)
                .setDirection(directionAsString)
                .setPrice(priceAsLong)
                .setUseMarketBasedPrice(useMarketBasedPrice)
                .setMarketPriceMargin(marketPriceMargin)
                .setAmount(amountAsLong)
                .setMinAmount(minAmountAsLong)
                .setBuyerSecurityDeposit(buyerSecurityDeposit)
                .setPaymentAccountId(paymentAccountId)
                .build();
        try {
            return placeOfferBlockingStub.placeOffer(request).getResult();
        } catch (StatusRuntimeException e) {
            log.warn("RPC failed: {}", e.getStatus());
            return false;
        }
    }

    private void stopServer() {
        StopServerRequest request = StopServerRequest.newBuilder().build();
        try {
            stopServerStub.stopServer(request);
        } catch (StatusRuntimeException e) {
            log.warn("RPC failed: {}", e.getStatus());
        }
    }

    private void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
        System.exit(0);
    }
}
