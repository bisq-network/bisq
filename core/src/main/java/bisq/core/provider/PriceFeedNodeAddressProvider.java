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

import bisq.core.filter.DenyList;

import bisq.common.config.Config;

import com.google.inject.Inject;

import javax.inject.Named;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class PriceFeedNodeAddressProvider {
    public static final List<String> DEFAULT_NODES = Arrays.asList(
            "http://ro7nv73awqs3ga2qtqeqawrjpbxwarsazznszvr6whv7tes5ehffopid.onion/", // @alexej996
            "http://runbtcpn7gmbj5rgqeyfyvepqokrijem6rbw7o5wgqbguimuoxrmcdyd.onion"   // @runbtc
    );

    private final Config config;
    private final List<String> providersFromProgramArgs;
    private final boolean useLocalhostForP2P;
    private final DenyList denyList;

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
    public PriceFeedNodeAddressProvider(Config config,
                                        @Named(Config.PROVIDERS) List<String> providers,
                                        @Named(Config.USE_LOCALHOST_FOR_P2P) boolean useLocalhostForP2P,
                                        DenyList denyList) {

        this.config = config;
        this.providersFromProgramArgs = providers;
        this.useLocalhostForP2P = useLocalhostForP2P;
        this.denyList = denyList;

        Collections.shuffle(DEFAULT_NODES);

        applyBannedNodes(config.ignoreNetworkFilter ? null : config.bannedPriceRelayNodes);
    }

    public void applyBannedNodes(@Nullable List<String> bannedNodes) {
        this.bannedNodes = mergeWithDenyList(bannedNodes);
        fillProviderList();
        selectNextProviderBaseUrl();

        if (!this.bannedNodes.isEmpty()) {
            log.info("Excluded provider nodes from policy: nodes={}, selected provider baseUrl={}, providerList={}",
                    this.bannedNodes, baseUrl, providerList);
        }
    }

    private List<String> mergeWithDenyList(@Nullable List<String> bannedNodes) {
        Set<String> merged = new LinkedHashSet<>(denyList.getBannedPriceRelayNodes());
        if (bannedNodes != null) {
            merged.addAll(bannedNodes);
        }
        return List.copyOf(merged);
    }

    public void selectNextProviderBaseUrl() {
        if (!providerList.isEmpty()) {
            if (index >= providerList.size())
                index = 0;

            baseUrl = providerList.get(index);
            index++;

            if (providerList.size() == 1 && config.getBaseCurrencyNetwork().isMainnet())
                log.warn("We only have one provider");
        } else {
            baseUrl = "";
            log.warn("We do not have any providers. That can be if all providers are filtered or providersFromProgramArgs is set but empty. " +
                    "bannedNodes={}. providersFromProgramArgs={}", bannedNodes, providersFromProgramArgs);
        }
    }

    private void fillProviderList() {
        List<String> providers;
        if (providersFromProgramArgs.isEmpty()) {
            if (useLocalhostForP2P) {
                // If we run in localhost mode we don't have the tor node running, so we need a clearnet host
                // Use localhost for using a locally running provider
                // providerAsString = Collections.singletonList("http://localhost:8080/");
                providers = Collections.singletonList("http://172.86.75.7:8080/");
            } else {
                providers = DEFAULT_NODES;
            }
        } else {
            providers = providersFromProgramArgs;
        }
        providerList = providers.stream()
                .filter(e -> bannedNodes == null ||
                        !bannedNodes.contains(e.replace("http://", "")
                                .replace("/", "")
                                .replace(".onion", "")))
                .map(e -> e.endsWith("/") ? e : e + "/")
                .map(e -> e.startsWith("http") ? e : "http://" + e)
                .collect(Collectors.toList());
    }
}
