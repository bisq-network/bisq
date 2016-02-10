package io.bitsquare.btc.pricefeed;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import io.bitsquare.app.Log;
import io.bitsquare.btc.pricefeed.providers.BitcoinAveragePriceProvider;
import io.bitsquare.btc.pricefeed.providers.PriceProvider;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.handlers.FaultHandler;
import io.bitsquare.common.util.Utilities;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class MarketPriceFeed {
    private static final Logger log = LoggerFactory.getLogger(MarketPriceFeed.class);

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

    // TODO 
    // https://poloniex.com/public?command=returnTicker  33 kb
    private static long PERIOD = 30;

    private final ScheduledThreadPoolExecutor executorService = Utilities.getScheduledThreadPoolExecutor("MarketPriceFeed", 5, 10, 120L);
    private final Map<String, MarketPrice> cache = new HashMap<>();
    private Consumer<Double> priceConsumer;
    private FaultHandler faultHandler;
    private PriceProvider fiatPriceProvider = new BitcoinAveragePriceProvider();
    private Type type;
    private String currencyCode;
    transient private final StringProperty currencyCodeProperty = new SimpleStringProperty();
    transient private final ObjectProperty<Type> typeProperty = new SimpleObjectProperty<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MarketPriceFeed() {
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void init(Consumer<Double> resultHandler, FaultHandler faultHandler) {
        this.priceConsumer = resultHandler;
        this.faultHandler = faultHandler;

        requestAllPrices(fiatPriceProvider, () -> {
            applyPrice();
            executorService.scheduleAtFixedRate(() -> requestPrice(fiatPriceProvider), PERIOD, PERIOD, TimeUnit.SECONDS);
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setType(Type type) {
        this.type = type;
        typeProperty.set(type);
        applyPrice();
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
        currencyCodeProperty.set(currencyCode);
        applyPrice();
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyPrice() {
        if (priceConsumer != null && currencyCode != null && type != null) {
            if (cache.containsKey(currencyCode)) {
                MarketPrice marketPrice = cache.get(currencyCode);
                log.debug("applyPrice type=" + type);
                priceConsumer.accept(marketPrice.getPrice(type));
            } else {
                String errorMessage = "We don't have a price for currencyCode " + currencyCode;
                log.debug(errorMessage);
                faultHandler.handleFault(errorMessage, new PriceRequestException(errorMessage));
            }
        }
    }

    private void requestPrice(PriceProvider provider) {
        Log.traceCall();
        GetPriceRequest getPriceRequest = new GetPriceRequest();
        SettableFuture<MarketPrice> future = getPriceRequest.requestPrice(currencyCode, provider);
        Futures.addCallback(future, new FutureCallback<MarketPrice>() {
            public void onSuccess(MarketPrice marketPrice) {
                UserThread.execute(() -> {
                    cache.put(marketPrice.currencyCode, marketPrice);
                    log.debug("marketPrice updated " + marketPrice);
                    priceConsumer.accept(marketPrice.getPrice(type));
                });
            }

            public void onFailure(@NotNull Throwable throwable) {
                log.debug("Could not load marketPrice\n" + throwable.getMessage());
            }
        });
    }

    private void requestAllPrices(PriceProvider provider, Runnable resultHandler) {
        Log.traceCall();
        GetPriceRequest getPriceRequest = new GetPriceRequest();
        SettableFuture<Map<String, MarketPrice>> future = getPriceRequest.requestAllPrices(provider);
        Futures.addCallback(future, new FutureCallback<Map<String, MarketPrice>>() {
            public void onSuccess(Map<String, MarketPrice> marketPriceMap) {
                UserThread.execute(() -> {
                    cache.putAll(marketPriceMap);
                    resultHandler.run();
                });
            }

            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> faultHandler.handleFault("Could not load marketPrices", throwable));
            }
        });
    }
}
