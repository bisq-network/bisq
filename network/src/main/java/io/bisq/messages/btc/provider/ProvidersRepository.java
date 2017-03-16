package io.bisq.messages.btc.provider;

import com.google.inject.Inject;
import io.bisq.app.AppOptionKeys;
import io.bisq.network.NetworkOptionKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.util.Random;

public class ProvidersRepository {
    private static final Logger log = LoggerFactory.getLogger(ProvidersRepository.class);

    private final String[] providerArray;
    private String baseUrl;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProvidersRepository(@Named(AppOptionKeys.PROVIDERS) String providers,
                               @Named(NetworkOptionKeys.USE_LOCALHOST) boolean useLocalhost) {
        if (providers.isEmpty()) {
            if (useLocalhost) {
                // If we run in localhost mode we don't have the tor node running, so we need a clearnet host
                // providers = "http://95.85.11.205:8080/";

                // Use localhost for using a locally running priceprovider
                providers = "http://localhost:8080/, http://51.15.47.83:8080/";
                //providers = "http://localhost:8080/";
            } else {
                // TODO atm we dont have it running as HS...
                providers = "http://localhost:8080/, http://51.15.47.83:8080/";
                //providers = "http://t4wlzy7l6k4hnolg.onion/, http://g27szt7aw2vrtowe.onion/";
            }
        }
        providerArray = providers.replace(" ", "").split(",");
        int index = new Random().nextInt(providerArray.length);
        baseUrl = providerArray[index];
        log.info("baseUrl for PriceFeedService: " + baseUrl);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean hasMoreProviders() {
        return providerArray.length > 1;
    }

    public void setNewRandomBaseUrl() {
        String newBaseUrl;
        do {
            int index = new Random().nextInt(providerArray.length);
            newBaseUrl = providerArray[index];
        }
        while (baseUrl.equals(newBaseUrl));
        baseUrl = newBaseUrl;
        log.info("Try new baseUrl after error: " + baseUrl);
    }
}
