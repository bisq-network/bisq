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
                //providers = "http://localhost:8080/";
                // providers = "http://localhost:8080/, http://37.139.14.34:8080/";
                providers = "http://37.139.14.34:8080/";
            } else {
                providers = "http://44mgyoe2b6oqiytt.onion/, http://5bmpx76qllutpcyp.onion/";
            }
        }

        providerArray = StringUtils.deleteWhitespace(providers).split(",");

        int index = new Random().nextInt(providerArray.length);
        baseUrl = providerArray[index];
        log.debug("baseUrl: " + baseUrl);
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
