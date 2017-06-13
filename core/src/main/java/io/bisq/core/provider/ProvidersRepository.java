package io.bisq.core.provider;

import com.google.inject.Inject;
import io.bisq.core.app.AppOptionKeys;
import io.bisq.network.NetworkOptionKeys;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Named;
import java.util.Random;

@Slf4j
public class ProvidersRepository {
    private final String[] providerArray;
    private String baseUrl;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProvidersRepository(@Named(AppOptionKeys.PROVIDERS) String providers,
                               @Named(NetworkOptionKeys.USE_LOCALHOST_FOR_P2P) boolean useLocalhostForP2P) {
        if (providers.isEmpty()) {
            if (useLocalhostForP2P) {
                // If we run in localhost mode we don't have the tor node running, so we need a clearnet host
                // Use localhost for using a locally running provider
                providers = "http://localhost:8080/";
                //providers = "http://localhost:8080/, http://146.185.175.243:8080/";
                // providers = "http://146.185.175.243:8080/";
            } else {
                providers = "http://kijf4m2pqd54tbck.onion/";
            }
        }

        providerArray = StringUtils.deleteWhitespace(providers).split(",");

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
