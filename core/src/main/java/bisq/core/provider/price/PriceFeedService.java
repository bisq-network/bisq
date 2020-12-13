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

package bisq.core.provider.price;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.TradeCurrency;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.provider.PriceHttpClient;
import bisq.core.provider.ProvidersRepository;
import bisq.core.trade.statistics.TradeStatistics3;
import bisq.core.user.Preferences;

import bisq.network.http.HttpClient;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.handlers.FaultHandler;
import bisq.common.util.MathUtils;
import bisq.common.util.Tuple2;

import com.google.inject.Inject;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.Instant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class PriceFeedService {
    private final HttpClient httpClient;
    private final ProvidersRepository providersRepository;
    private final Preferences preferences;

    private static final long PERIOD_SEC = 60;

    private final Map<String, MarketPrice> cache = new HashMap<>();
    private PriceProvider priceProvider;
    @Nullable
    private Consumer<Double> priceConsumer;
    @Nullable
    private FaultHandler faultHandler;
    private String currencyCode;
    private final StringProperty currencyCodeProperty = new SimpleStringProperty();
    private final IntegerProperty updateCounter = new SimpleIntegerProperty(0);
    private long epochInMillisAtLastRequest;
    private long retryDelay = 1;
    private long requestTs;
    @Nullable
    private String baseUrlOfRespondingProvider;
    @Nullable
    private Timer requestTimer;
    @Nullable
    private PriceRequest priceRequest;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PriceFeedService(PriceHttpClient httpClient,
                            @SuppressWarnings("SameParameterValue") ProvidersRepository providersRepository,
                            @SuppressWarnings("SameParameterValue") Preferences preferences) {
        this.httpClient = httpClient;
        this.providersRepository = providersRepository;
        this.preferences = preferences;

        // Do not use Guice for PriceProvider as we might create multiple instances
        this.priceProvider = new PriceProvider(httpClient, providersRepository.getBaseUrl());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void shutDown() {
        if (requestTimer != null) {
            requestTimer.stop();
            requestTimer = null;
        }
        if (priceRequest != null) {
            priceRequest.shutDown();
        }
    }

    public void setCurrencyCodeOnInit() {
        if (getCurrencyCode() == null) {
            TradeCurrency preferredTradeCurrency = preferences.getPreferredTradeCurrency();
            String code = preferredTradeCurrency != null ? preferredTradeCurrency.getCode() : "USD";
            setCurrencyCode(code);
        }
    }

    public void initialRequestPriceFeed() {
        request(false);
    }

    public boolean hasPrices() {
        return !cache.isEmpty();
    }

    public void requestPriceFeed(Consumer<Double> resultHandler, FaultHandler faultHandler) {
        this.priceConsumer = resultHandler;
        this.faultHandler = faultHandler;

        request(true);
    }

    public String getProviderNodeAddress() {
        return httpClient.getBaseUrl();
    }

    private void request(boolean repeatRequests) {
        if (requestTs == 0)
            log.debug("request from provider {}",
                    providersRepository.getBaseUrl());
        else
            log.debug("request from provider {} {} sec. after last request",
                    providersRepository.getBaseUrl(),
                    (System.currentTimeMillis() - requestTs) / 1000d);

        requestTs = System.currentTimeMillis();

        baseUrlOfRespondingProvider = null;

        requestAllPrices(priceProvider, () -> {
            baseUrlOfRespondingProvider = priceProvider.getBaseUrl();

            // At applyPriceToConsumer we also check if price is not exceeding max. age for price data.
            boolean success = applyPriceToConsumer();
            if (success) {
                MarketPrice marketPrice = cache.get(currencyCode);
                if (marketPrice != null)
                    log.debug("Received new {} from provider {} after {} sec.",
                            marketPrice,
                            baseUrlOfRespondingProvider,
                            (System.currentTimeMillis() - requestTs) / 1000d);
                else
                    log.debug("Received new data from provider {} after {} sec. " +
                                    "Requested market price for currency {} was not provided. " +
                                    "That is expected if currency is not listed at provider.",
                            baseUrlOfRespondingProvider,
                            (System.currentTimeMillis() - requestTs) / 1000d,
                            currencyCode);
            } else {
                log.warn("applyPriceToConsumer was not successful. We retry with a new provider.");
                retryWithNewProvider();
            }
        }, (errorMessage, throwable) -> {
            if (throwable instanceof PriceRequestException) {
                String baseUrlOfFaultyRequest = ((PriceRequestException) throwable).priceProviderBaseUrl;
                String baseUrlOfCurrentRequest = priceProvider.getBaseUrl();
                if (baseUrlOfCurrentRequest.equals(baseUrlOfFaultyRequest)) {
                    log.warn("We received an error: baseUrlOfCurrentRequest={}, baseUrlOfFaultyRequest={}, error={}",
                            baseUrlOfCurrentRequest, baseUrlOfFaultyRequest, throwable.toString());
                    retryWithNewProvider();
                } else {
                    log.debug("We received an error from an earlier request. We have started a new request already so we ignore that error. " +
                                    "baseUrlOfCurrentRequest={}, baseUrlOfFaultyRequest={}",
                            baseUrlOfCurrentRequest, baseUrlOfFaultyRequest);
                }
            } else {
                log.warn("We received an error with throwable={}", throwable.toString());
                retryWithNewProvider();
            }

            if (faultHandler != null)
                faultHandler.handleFault(errorMessage, throwable);
        });

        if (repeatRequests) {
            if (requestTimer != null)
                requestTimer.stop();

            long delay = PERIOD_SEC + new Random().nextInt(5);
            requestTimer = UserThread.runAfter(() -> {
                // If we have not received a result from the last request. We try a new provider.
                if (baseUrlOfRespondingProvider == null) {
                    final String oldBaseUrl = priceProvider.getBaseUrl();
                    setNewPriceProvider();
                    log.warn("We did not received a response from provider {}. " +
                            "We select the new provider {} and use that for a new request.", oldBaseUrl, priceProvider.getBaseUrl());
                }
                request(true);
            }, delay);
        }
    }

    private void retryWithNewProvider() {
        // We increase retry delay each time until we reach PERIOD_SEC to not exceed requests.
        UserThread.runAfter(() -> {
            retryDelay = Math.min(retryDelay + 5, PERIOD_SEC);

            String oldBaseUrl = priceProvider.getBaseUrl();
            setNewPriceProvider();
            log.warn("We received an error at the request from provider {}. " +
                    "We select the new provider {} and use that for a new request. retryDelay was {} sec.", oldBaseUrl, priceProvider.getBaseUrl(), retryDelay);

            request(true);
        }, retryDelay);
    }

    private void setNewPriceProvider() {
        providersRepository.selectNextProviderBaseUrl();
        if (!providersRepository.getBaseUrl().isEmpty())
            priceProvider = new PriceProvider(httpClient, providersRepository.getBaseUrl());
        else
            log.warn("We cannot create a new priceProvider because new base url is empty.");
    }

    @Nullable
    public MarketPrice getMarketPrice(String currencyCode) {
        return cache.getOrDefault(currencyCode, null);
    }

    private void setBisqMarketPrice(String currencyCode, Price price) {
        if (!cache.containsKey(currencyCode) || !cache.get(currencyCode).isExternallyProvidedPrice()) {
            cache.put(currencyCode, new MarketPrice(currencyCode,
                    MathUtils.scaleDownByPowerOf10(price.getValue(), CurrencyUtil.isCryptoCurrency(currencyCode) ? 8 : 4),
                    0,
                    false));
            updateCounter.set(updateCounter.get() + 1);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setCurrencyCode(String currencyCode) {
        if (this.currencyCode == null || !this.currencyCode.equals(currencyCode)) {
            this.currencyCode = currencyCode;
            currencyCodeProperty.set(currencyCode);
            if (priceConsumer != null)
                applyPriceToConsumer();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getCurrencyCode() {
        return currencyCode;
    }

    public StringProperty currencyCodeProperty() {
        return currencyCodeProperty;
    }

    public ReadOnlyIntegerProperty updateCounterProperty() {
        return updateCounter;
    }

    public Date getLastRequestTimeStamp() {
        return new Date(epochInMillisAtLastRequest);
    }

    public void applyLatestBisqMarketPrice(Set<TradeStatistics3> tradeStatisticsSet) {
        // takes about 10 ms for 5000 items
        Map<String, List<TradeStatistics3>> mapByCurrencyCode = new HashMap<>();
        tradeStatisticsSet.forEach(e -> {
            List<TradeStatistics3> list;
            String currencyCode = e.getCurrency();
            if (mapByCurrencyCode.containsKey(currencyCode)) {
                list = mapByCurrencyCode.get(currencyCode);
            } else {
                list = new ArrayList<>();
                mapByCurrencyCode.put(currencyCode, list);
            }
            list.add(e);
        });

        mapByCurrencyCode.values().stream()
                .filter(list -> !list.isEmpty())
                .forEach(list -> {
                    list.sort(Comparator.comparing(TradeStatistics3::getDate));
                    TradeStatistics3 tradeStatistics = list.get(list.size() - 1);
                    setBisqMarketPrice(tradeStatistics.getCurrency(), tradeStatistics.getTradePrice());
                });
    }

    public Optional<Price> getBsqPrice() {
        MarketPrice bsqMarketPrice = getMarketPrice("BSQ");
        if (bsqMarketPrice != null) {
            long bsqPriceAsLong = MathUtils.roundDoubleToLong(MathUtils.scaleUpByPowerOf10(bsqMarketPrice.getPrice(), Altcoin.SMALLEST_UNIT_EXPONENT));
            return Optional.of(Price.valueOf("BSQ", bsqPriceAsLong));
        } else {
            return Optional.empty();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean applyPriceToConsumer() {
        boolean result = false;
        String errorMessage = null;
        if (currencyCode != null) {
            String baseUrl = priceProvider.getBaseUrl();
            if (cache.containsKey(currencyCode)) {
                try {
                    MarketPrice marketPrice = cache.get(currencyCode);
                    if (marketPrice.isExternallyProvidedPrice()) {
                        if (marketPrice.isRecentPriceAvailable()) {
                            if (priceConsumer != null)
                                priceConsumer.accept(marketPrice.getPrice());
                            result = true;
                        } else {
                            errorMessage = "Price for currency " + currencyCode + " is outdated by " +
                                    (Instant.now().getEpochSecond() - marketPrice.getTimestampSec()) / 60 + " minutes. " +
                                    "Max. allowed age of price is " + MarketPrice.MARKET_PRICE_MAX_AGE_SEC / 60 + " minutes. " +
                                    "priceProvider=" + baseUrl + ". " +
                                    "marketPrice= " + marketPrice;
                        }
                    } else {
                        if (baseUrlOfRespondingProvider == null)
                            log.debug("Market price for currency " + currencyCode + " was not delivered by provider " +
                                    baseUrl + ". That is expected at startup.");
                        else
                            log.debug("Market price for currency " + currencyCode + " is not provided by the provider " +
                                    baseUrl + ". That is expected for currencies not listed at providers.");
                        result = true;
                    }
                } catch (Throwable t) {
                    errorMessage = "Exception at applyPriceToConsumer for currency " + currencyCode +
                            ". priceProvider=" + baseUrl + ". Exception=" + t;
                }
            } else {
                log.debug("We don't have a price for currency " + currencyCode + ". priceProvider=" + baseUrl +
                        ". That is expected for currencies not listed at providers.");
                result = true;
            }
        } else {
            errorMessage = "We don't have a currency yet set. That should never happen";
        }

        if (errorMessage != null) {
            log.warn(errorMessage);
            if (faultHandler != null)
                faultHandler.handleFault(errorMessage, new PriceRequestException(errorMessage));
        }

        updateCounter.set(updateCounter.get() + 1);

        return result;
    }

    private void requestAllPrices(PriceProvider provider, Runnable resultHandler, FaultHandler faultHandler) {
        if (httpClient.hasPendingRequest()) {
            log.warn("We have a pending request open. We ignore that request. httpClient {}", httpClient);
            return;
        }

        priceRequest = new PriceRequest();
        SettableFuture<Tuple2<Map<String, Long>, Map<String, MarketPrice>>> future = priceRequest.requestAllPrices(provider);
        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable Tuple2<Map<String, Long>, Map<String, MarketPrice>> result) {
                UserThread.execute(() -> {
                    checkNotNull(result, "Result must not be null at requestAllPrices");
                    // Each currency rate has a different timestamp, depending on when
                    // the priceNode aggregate rate was calculated
                    // However, the request timestamp is when the pricenode was queried
                    epochInMillisAtLastRequest = System.currentTimeMillis();

                    Map<String, MarketPrice> priceMap = result.second;

                    cache.putAll(priceMap);

                    resultHandler.run();
                });
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> faultHandler.handleFault("Could not load marketPrices", throwable));
            }
        }, MoreExecutors.directExecutor());
    }
}
