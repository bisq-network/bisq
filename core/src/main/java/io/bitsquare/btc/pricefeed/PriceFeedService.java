package io.bitsquare.btc.pricefeed;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import io.bitsquare.app.AppOptionKeys;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.handlers.FaultHandler;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.http.HttpClient;
import io.bitsquare.network.NetworkOptionKeys;
import javafx.beans.property.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Named;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

public class PriceFeedService {
    private static final Logger log = LoggerFactory.getLogger(PriceFeedService.class);
    private HttpClient httpClient;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum Type {
        ASK("Ask"),
        BID("Bid"),
        LAST("Last");

        public final String name;

        Type(String name) {
            this.name = name;
        }
    }

    private static final long PERIOD_SEC = 60;

    private final Map<String, MarketPrice> cache = new HashMap<>();
    private PriceProvider priceProvider;
    private Consumer<Double> priceConsumer;
    private FaultHandler faultHandler;
    private Type type;
    private String currencyCode;
    private final StringProperty currencyCodeProperty = new SimpleStringProperty();
    private final ObjectProperty<Type> typeProperty = new SimpleObjectProperty<>();
    private final IntegerProperty currenciesUpdateFlag = new SimpleIntegerProperty(0);
    private long epochInSecondAtLastRequest;
    private Map<String, Long> timeStampMap = new HashMap<>();
    private String baseUrl;
    private final String[] priceFeedProviderArray;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PriceFeedService(HttpClient httpClient,
                            @Named(AppOptionKeys.PRICE_FEED_PROVIDERS) String priceFeedProviders,
                            @Named(NetworkOptionKeys.USE_LOCALHOST) boolean useLocalhost) {
        this.httpClient = httpClient;
        if (priceFeedProviders.isEmpty()) {
            if (useLocalhost) {
                // If we run in localhost mode we don't have the tor node running, so we need a clearnet host
                priceFeedProviders = "http://95.85.11.205:8080/";

                // Use localhost for using a locally running priceprovider
                // priceFeedProviders = "http://localhost:8080/"; 
            } else {
                priceFeedProviders = "http://t4wlzy7l6k4hnolg.onion/, http://g27szt7aw2vrtowe.onion/";
            }
        }
        priceFeedProviderArray = priceFeedProviders.replace(" ", "").split(",");
        int index = new Random().nextInt(priceFeedProviderArray.length);
        baseUrl = priceFeedProviderArray[index];
        log.info("baseUrl for PriceFeedService: " + baseUrl);
        this.priceProvider = new PriceProvider(httpClient, baseUrl);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void init(Consumer<Double> resultHandler, FaultHandler faultHandler) {
        this.priceConsumer = resultHandler;
        this.faultHandler = faultHandler;

        request();
    }

    private void request() {
        requestAllPrices(priceProvider, () -> {
            applyPriceToConsumer();
            // after first response we know the providers timestamp and want to request quickly after next expected update
            long delay = Math.max(40, Math.min(90, PERIOD_SEC - (Instant.now().getEpochSecond() - epochInSecondAtLastRequest) + 2 + new Random().nextInt(5)));
            UserThread.runAfter(this::request, delay);
        }, (errorMessage, throwable) -> {
            // Try other provider if more then 1 is available
            if (priceFeedProviderArray.length > 1) {
                String newBaseUrl;
                do {
                    int index = new Random().nextInt(priceFeedProviderArray.length);
                    newBaseUrl = priceFeedProviderArray[index];
                }
                while (baseUrl.equals(newBaseUrl));
                baseUrl = newBaseUrl;

                log.info("Try new baseUrl after error: " + baseUrl);
                this.priceProvider = new PriceProvider(httpClient, baseUrl);
                request();
            } else {
                UserThread.runAfter(this::request, 120);
            }
            this.faultHandler.handleFault(errorMessage, throwable);
        });
    }

    @Nullable
    public MarketPrice getMarketPrice(String currencyCode) {
        if (cache.containsKey(currencyCode))
            return cache.get(currencyCode);
        else
            return null;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setType(Type type) {
        this.type = type;
        typeProperty.set(type);
        applyPriceToConsumer();
    }

    public void setCurrencyCode(String currencyCode) {
        if (this.currencyCode == null || !this.currencyCode.equals(currencyCode)) {
            this.currencyCode = currencyCode;
            currencyCodeProperty.set(currencyCode);
            applyPriceToConsumer();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Type getType() {
        return type;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public StringProperty currencyCodeProperty() {
        return currencyCodeProperty;
    }

    public ObjectProperty<Type> typeProperty() {
        return typeProperty;
    }

    public IntegerProperty currenciesUpdateFlagProperty() {
        return currenciesUpdateFlag;
    }

    public Date getLastRequestTimeStampBtcAverage() {
        return new Date(epochInSecondAtLastRequest * 1000);
    }

    public Date getLastRequestTimeStampPoloniex() {
        Long ts = timeStampMap.get("btcAverageTs");
        if (ts != null) {
            Date date = new Date(ts * 1000);
            return date;
        } else
            return new Date();
    }

    public Date getLastRequestTimeStampCoinmarketcap() {
        Long ts = timeStampMap.get("coinmarketcapTs");
        if (ts != null) {
            Date date = new Date(ts * 1000);
            return date;
        } else
            return new Date();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyPriceToConsumer() {
        if (priceConsumer != null && currencyCode != null && type != null) {
            if (cache.containsKey(currencyCode)) {
                try {
                    MarketPrice marketPrice = cache.get(currencyCode);
                    priceConsumer.accept(marketPrice.getPrice(type));
                } catch (Throwable t) {
                    log.warn("Error at applyPriceToConsumer " + t.getMessage());
                }

            } else {
                String errorMessage = "We don't have a price for " + currencyCode;
                log.debug(errorMessage);
                faultHandler.handleFault(errorMessage, new PriceRequestException(errorMessage));
            }
        }
        currenciesUpdateFlag.setValue(currenciesUpdateFlag.get() + 1);
    }

    private void requestAllPrices(PriceProvider provider, Runnable resultHandler, FaultHandler faultHandler) {
        Log.traceCall();
        PriceRequest priceRequest = new PriceRequest();
        SettableFuture<Tuple2<Map<String, Long>, Map<String, MarketPrice>>> future = priceRequest.requestAllPrices(provider);
        Futures.addCallback(future, new FutureCallback<Tuple2<Map<String, Long>, Map<String, MarketPrice>>>() {
            @Override
            public void onSuccess(@Nullable Tuple2<Map<String, Long>, Map<String, MarketPrice>> result) {
                UserThread.execute(() -> {
                    checkNotNull(result, "Result must not be null at requestAllPrices");
                    timeStampMap = result.first;
                    epochInSecondAtLastRequest = timeStampMap.get("btcAverageTs");
                    cache.putAll(result.second);
                    resultHandler.run();
                });
            }

            @Override
            public void onFailure(Throwable throwable) {
                UserThread.execute(() -> faultHandler.handleFault("Could not load marketPrices", throwable));
            }
        });
    }
}
