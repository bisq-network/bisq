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

package bisq.core.provider;

import bisq.core.app.AppOptionKeys;
import bisq.core.app.BisqEnvironment;

import bisq.network.NetworkOptionKeys;

import com.google.inject.Inject;

import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class ProvidersRepository {
    private static final List<String> DEFAULT_NODES = Arrays.asList(
            "http://xc3nh4juf2hshy7e.onion/",   // @emzy
            "http://ceaanhbvluug4we6.onion/",   // @mrosseel
            "http://44mgyoe2b6oqiytt.onion/",   // @devinbileck
            "http://62nvujg5iou3vu3i.onion/",   // @alexej996
            "http://gztmprecgqjq64zh.onion/"    // @wiz
    );

    private final String providersFromProgramArgs;
    private final boolean useLocalhostForP2P;

    private List<String> providerList;
    @Getter
    private String baseUrl = "";
    @Getter
    @Nullable
    private List<String> bannedNodes;
    private int index = 0;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProvidersRepository(BisqEnvironment bisqEnvironment,
                               @Named(AppOptionKeys.PROVIDERS) String providers,
                               @Named(NetworkOptionKeys.USE_LOCALHOST_FOR_P2P) boolean useLocalhostForP2P) {

        this.providersFromProgramArgs = providers;
        this.useLocalhostForP2P = useLocalhostForP2P;

        Collections.shuffle(DEFAULT_NODES);

        applyBannedNodes(bisqEnvironment.getBannedPriceRelayNodes());
    }

    public void applyBannedNodes(@Nullable List<String> bannedNodes) {
        this.bannedNodes = bannedNodes;
        fillProviderList();
        selectNextProviderBaseUrl();

        if (bannedNodes == null)
            log.info("Selected provider baseUrl={}, providerList={}", baseUrl, providerList);
        else
            log.warn("We have banned provider nodes: bannedNodes={}, selected provider baseUrl={}, providerList={}",
                    bannedNodes, baseUrl, providerList);
    }

    public void selectNextProviderBaseUrl() {
        if (!providerList.isEmpty()) {
            if (index >= providerList.size())
                index = 0;

            baseUrl = providerList.get(index);
            index++;

            if (providerList.size() == 1 && BisqEnvironment.getBaseCurrencyNetwork().isMainnet())
                log.warn("We only have one provider");
        } else {
            baseUrl = "";
            log.warn("We do not have any providers. That can be if all providers are filtered or providersFromProgramArgs is set but empty. " +
                    "bannedNodes={}. providersFromProgramArgs={}", bannedNodes, providersFromProgramArgs);
        }
    }

    private void fillProviderList() {
        List<String> providers;
        if (providersFromProgramArgs == null || providersFromProgramArgs.isEmpty()) {
            if (useLocalhostForP2P) {
                // If we run in localhost mode we don't have the tor node running, so we need a clearnet host
                // Use localhost for using a locally running provider
                // providerAsString = Collections.singletonList("http://localhost:8080/");
                providers = Collections.singletonList("http://174.138.104.137:8080/"); // @miker
            } else {
                providers = DEFAULT_NODES;
            }
        } else {
            providers = Arrays.asList(StringUtils.deleteWhitespace(providersFromProgramArgs).split(","));
        }
        providerList = providers.stream()
                .filter(e -> bannedNodes == null ||
                        !bannedNodes.contains(e.replace("http://", "")
                                .replace("/", "")
                                .replace(".onion", "")))
                .collect(Collectors.toList());
    }
}
