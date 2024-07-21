package bisq.restapi.endpoints;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.OfferBookService;
import bisq.core.trade.statistics.TradeStatistics3;
import bisq.core.trade.statistics.TradeStatisticsManager;

import bisq.common.util.MathUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;



import bisq.restapi.RestApi;
import bisq.restapi.RestApiMain;
import bisq.restapi.dto.JsonCurrency;
import bisq.restapi.dto.JsonOffer;
import bisq.restapi.dto.JsonTradeInfo;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

@Slf4j
@Path("/explorer/markets")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "EXPLORER API")
public class ExplorerMarketsApi {
    private static final long MONTH = TimeUnit.DAYS.toMillis(30);

    private final OfferBookService offerBookService;
    private final TradeStatisticsManager tradeStatisticsManager;

    public ExplorerMarketsApi(@Context Application application) {
        RestApi restApi = ((RestApiMain) application).getRestApi();
        offerBookService = restApi.getOfferBookService();
        tradeStatisticsManager = restApi.getTradeStatisticsManager();
    }

    // http://localhost:8081/api/v1/explorer/markets/get-currencies
    @GET
    @Path("get-currencies")
    public List<JsonCurrency> getBisqCurrencies() {
        ArrayList<JsonCurrency> fiatCurrencyList = CurrencyUtil.getMatureMarketCurrencies().stream()
                .map(e -> new JsonCurrency(e.getCode(), e.getName(), 8, "fiat"))
                .collect(Collectors.toCollection(ArrayList::new));
        ArrayList<JsonCurrency> cryptoCurrencyList = CurrencyUtil.getMainCryptoCurrencies().stream()
                .map(e -> new JsonCurrency(e.getCode(), e.getName(), 8, "crypto"))
                .collect(Collectors.toCollection(ArrayList::new));
        List<JsonCurrency> result = Stream.concat(fiatCurrencyList.stream(), cryptoCurrencyList.stream()).collect(Collectors.toList());
        log.info("client requested currencies, returning {} currencies", result.size());
        return result;
    }

    @GET
    @Path("get-offers")
    public List<JsonOffer> getBisqOffers() {
        List<JsonOffer> result = offerBookService.getOfferForJsonList().stream()
                .map(offerForJson -> new JsonOffer(
                        offerForJson.direction.name(),
                        offerForJson.currencyCode,
                        offerForJson.minAmount,
                        offerForJson.amount,
                        offerForJson.price,
                        offerForJson.date,
                        offerForJson.useMarketBasedPrice,
                        offerForJson.marketPriceMargin,
                        offerForJson.paymentMethod,
                        offerForJson.id,
                        offerForJson.currencyPair,
                        offerForJson.direction.name(),
                        offerForJson.priceDisplayString,
                        offerForJson.primaryMarketAmountDisplayString,
                        offerForJson.primaryMarketMinAmountDisplayString,
                        offerForJson.primaryMarketVolumeDisplayString,
                        offerForJson.primaryMarketMinVolumeDisplayString,
                        offerForJson.primaryMarketPrice,
                        offerForJson.primaryMarketAmount,
                        offerForJson.primaryMarketMinAmount,
                        offerForJson.primaryMarketVolume,
                        offerForJson.primaryMarketMinVolume)
                )
                .collect(Collectors.toList());
        log.info("client requested offers, returning {} offers", result.size());
        return result;
    }

    @GET
    @Path("get-trades/{newestTimestamp}/{oldestTimestamp}")
    public List<JsonTradeInfo> getBisqTrades(@PathParam("newestTimestamp") long newestTimestamp,
                                             @PathParam("oldestTimestamp") long oldestTimestamp) {
        log.info("newestTimestamp: {} oldestTimestamp: {}", newestTimestamp, oldestTimestamp);

        long to = new Date().getTime();
        long from = newestTimestamp > 0 ? newestTimestamp : to - MONTH;    // 30 days default
        ArrayList<JsonTradeInfo> result = new ArrayList<>();
        List<TradeStatistics3> tradeStatisticsList = tradeStatisticsManager.getTradeStatisticsList(from, to);
        log.info("requesting a fresh batch of trades {}", tradeStatisticsList.size());
        if (tradeStatisticsList.size() < 200 && oldestTimestamp > 0) {
            to = oldestTimestamp;
            from = to - MONTH;
            List<TradeStatistics3> additional = tradeStatisticsManager.getTradeStatisticsList(from, to);
            tradeStatisticsList.addAll(additional);
            log.info("requesting an additional older batch of trades {}", additional.size());
        }
        tradeStatisticsList.forEach(x -> {
            try {
                String currencyPair = Res.getBaseCurrencyCode() + "/" + x.getCurrency();
                // we use precision 4 for fiat based price but on the markets api we use precision 8 so we scale up by 10000
                long primaryMarketTradePrice = (long) MathUtils.scaleUpByPowerOf10(x.getTradePrice().getValue(), 4);
                long primaryMarketTradeAmount = x.getAmount();
                // we use precision 4 for fiat but on the markets api we use precision 8 so we scale up by 10000
                long primaryMarketTradeVolume = x.getTradeVolume() != null ?
                        (long) MathUtils.scaleUpByPowerOf10(x.getTradeVolume().getValue(), 4) : 0;

                if (CurrencyUtil.isCryptoCurrency(x.getCurrency())) {
                    currencyPair = x.getCurrency() + "/" + Res.getBaseCurrencyCode();
                    primaryMarketTradePrice = x.getTradePrice().getValue();
                    primaryMarketTradeAmount = x.getTradeVolume().getValue(); // getVolumeByAmount?
                    primaryMarketTradeVolume = x.getAmount();
                }
                JsonTradeInfo jsonTradeInfo = new JsonTradeInfo(x.getCurrency(), x.getPrice(), x.getAmount(),
                        x.getDateAsLong(), x.getPaymentMethodId(), currencyPair, primaryMarketTradePrice,
                        primaryMarketTradeAmount, primaryMarketTradeVolume);
                result.add(jsonTradeInfo);
            } catch (Throwable t) {
                log.error("Iterating tradeStatisticsList failed", t);
            }
        });
        log.info("client requested trades, returning {} trades", result.size());
        return result;
    }
}
