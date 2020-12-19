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

import bisq.common.config.Config;

import com.google.inject.Inject;

import javax.inject.Named;

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
            "http://wizpriceje6q5tdrxkyiazsgu7irquiqjy2dptezqhrtu7l2qelqktid.onion/", // @wiz
            "http://emzypricpidesmyqg2hc6dkwitqzaxrqnpkdg3ae2wef5znncu2ambqd.onion/", // @emzy
            "http://aprcndeiwdrkbf4fq7iozxbd27dl72oeo76n7zmjwdi4z34agdrnheyd.onion/", // @mrosseel
            "http://devinpndvdwll4wiqcyq5e7itezmarg7rzicrvf6brzkwxdm374kmmyd.onion/", // @devinbileck
            "http://ro7nv73awqs3ga2qtqeqawrjpbxwarsazznszvr6whv7tes5ehffopid.onion/" // @alexej996
    );

    private final Config config;
    private final List<String> providersFromProgramArgs;
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
    public ProvidersRepository(Config config,
                               @Named(Config.PROVIDERS) List<String> providers,
                               @Named(Config.USE_LOCALHOST_FOR_P2P) boolean useLocalhostForP2P) {

        this.config = config;
        this.providersFromProgramArgs = providers;
        this.useLocalhostForP2P = useLocalhostForP2P;

        Collections.shuffle(DEFAULT_NODES);

        applyBannedNodes(config.bannedPriceRelayNodes);
    }

    public void applyBannedNodes(@Nullable List<String> bannedNodes) {
        this.bannedNodes = bannedNodes;
        fillProviderList();
        selectNextProviderBaseUrl();

        if (bannedNodes != null && !bannedNodes.isEmpty()) {
            log.info("Excluded provider nodes from filter: nodes={}, selected provider baseUrl={}, providerList={}",
                    bannedNodes, baseUrl, providerList);
        }
    }

    public void selectNextProviderBaseUrl() {
        if (!providerList.isEmpty()) {
            if (index >= providerList.size())
                index = 0;

            baseUrl = providerList.get(index);
            index++;

            if (providerList.size() == 1 && config.baseCurrencyNetwork.isMainnet())
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
                providers = Collections.singletonList("https://price.bisq.wiz.biz/"); // @wiz
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
