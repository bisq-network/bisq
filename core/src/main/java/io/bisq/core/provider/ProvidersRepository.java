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
import io.bisq.core.app.BisqEnvironment;
import io.bisq.network.NetworkOptionKeys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import javax.inject.Named;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
public class ProvidersRepository {
    private static final String NODES = "http://xc3nh4juf2hshy7e.onion/, " + // @emzy
            "http://ceaanhbvluug4we6.onion/, " +// @mrosseel
            "http://rb2l2qale2pqzjyo.onion/";  // @sqrrm

    // Old nodes before v 0.6.0: "http://44mgyoe2b6oqiytt.onion/, http://5bmpx76qllutpcyp.onion/"; @ManfredKarrer

    private final String providersFromProgramArgs;
    private final boolean useLocalhostForP2P;

    private List<String> providerList;
    @Getter
    private String baseUrl = "";


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProvidersRepository(BisqEnvironment bisqEnvironment,
                               @Named(AppOptionKeys.PROVIDERS) String providers,
                               @Named(NetworkOptionKeys.USE_LOCALHOST_FOR_P2P) boolean useLocalhostForP2P) {

        this.providersFromProgramArgs = providers;
        this.useLocalhostForP2P = useLocalhostForP2P;

        applyBannedNodes(bisqEnvironment.getBannedPriceRelayNodes());
    }

    public void applyBannedNodes(@Nullable List<String> bannedNodes) {
        String providerAsString;
        if (providersFromProgramArgs == null || providersFromProgramArgs.isEmpty()) {
            if (useLocalhostForP2P) {
                // If we run in localhost mode we don't have the tor node running, so we need a clearnet host
                // Use localhost for using a locally running provider
                // providerAsString = "http://localhost:8080/";
                providerAsString = "http://174.138.104.137:8080/"; // @mrosseel
            } else {
                providerAsString = NODES;
            }
        } else {
            providerAsString = providersFromProgramArgs;
        }

        providerList = Arrays.asList(StringUtils.deleteWhitespace(providerAsString).split(","))
                .stream()
                .filter(e -> bannedNodes == null || !bannedNodes.contains(e.replace("http://", "").replace("/", "").replace(".onion", "")))
                .collect(Collectors.toList());

        if (!providerList.isEmpty())
            baseUrl = providerList.get(new Random().nextInt(providerList.size()));

        if (bannedNodes == null)
            log.info("selected baseUrl={}, providerList={}", baseUrl, providerList);
        else
            log.warn("We received banned provider nodes: bannedNodes={}, selected baseUrl={}, providerList={}",
                    bannedNodes, baseUrl, providerList);

    }

    public void selectNewRandomBaseUrl() {
        int counter = 0;
        String newBaseUrl = "";
        do {
            if (!providerList.isEmpty())
                newBaseUrl = providerList.get(new Random().nextInt(providerList.size()));
            counter++;
        }
        while (counter < 100 && baseUrl.equals(newBaseUrl));
        baseUrl = newBaseUrl;
        log.info("Use new provider baseUrl: " + baseUrl);
    }

    public boolean hasMoreProviders() {
        return !providerList.isEmpty();
    }
}
