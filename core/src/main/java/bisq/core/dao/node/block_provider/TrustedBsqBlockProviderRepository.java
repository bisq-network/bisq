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

package bisq.core.dao.node.block_provider;

import bisq.core.filter.DenyList;

import bisq.network.p2p.NodeAddress;

import bisq.common.config.Config;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class TrustedBsqBlockProviderRepository {
    private static final String POSTFIX = ".trusted_bsq_block_providers";

    private final Config config;
    private final DenyList denyList;
    private Set<TrustedBsqBlockProvider> trustedBsqBlockProviders = Set.of();
    private boolean initialized;

    @Inject
    public TrustedBsqBlockProviderRepository(Config config, DenyList denyList) {
        this.config = config;
        this.denyList = denyList;
    }

    public TrustedBsqBlockProviderRepository(Config config) {
        this(config, DenyList.empty());
    }


    public synchronized Collection<TrustedBsqBlockProvider> getTrustedBsqBlockProviders() {
        if (!initialized) {
            lazyInitialize();
        }

        return trustedBsqBlockProviders;
    }

    public boolean isTrustedBsqBlockProvider(TrustedBsqBlockProvider provider) {
        return getTrustedBsqBlockProviders().contains(provider);
    }


    private void lazyInitialize() {
        try {
            List<String> providersFromConfig = config.bsqBlockProviders;
            Set<TrustedBsqBlockProvider> providers;

            if (providersFromConfig.isEmpty()) {
                String baseCurrencyNetwork = config.getBaseCurrencyNetwork().name().toLowerCase(Locale.ENGLISH);
                String fileName = baseCurrencyNetwork + POSTFIX;
                providers = fromResources(fileName);
            } else {
                providers = providersFromConfig.stream()
                        .map(TrustedBsqBlockProvider::fromConfig)
                        .collect(Collectors.toSet());
                log.info("We got overwritten our hard coded TrustedBsqBlockProviders from config with:\n" +
                        "{}", providersFromConfig);
            }
            Set<NodeAddress> deniedNodeAddresses = denyList.getBannedSeedNodes().stream()
                    .map(NodeAddress::new)
                    .collect(Collectors.toSet());
            providers = providers.stream()
                    .filter(Objects::nonNull)
                    .filter(provider -> !deniedNodeAddresses.contains(provider.getNodeAddress()))
                    .collect(Collectors.toSet());
            trustedBsqBlockProviders = Set.copyOf(providers);
            initialized = true;
        } catch (Exception t) {
            log.error("Failed to initialize trustedBsqBlockProviders", t);
            throw t;
        }
    }

    private static Set<TrustedBsqBlockProvider> fromResources(String fileName) {
        Optional<BufferedReader> file = readFromResources(fileName);
        if (file.isEmpty()) {
            return Set.of();
        }

        try (BufferedReader reader = file.get()) {
            return reader.lines()
                    .map(TrustedBsqBlockProviderRepository::removeComment)
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .map(TrustedBsqBlockProvider::fromResources)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load TrustedBsqBlockProviders resource " + fileName, e);
        }
    }

    private static Optional<BufferedReader> readFromResources(String fileName) {
        InputStream fileInputStream = TrustedBsqBlockProviderRepository.class.getClassLoader().getResourceAsStream(fileName);
        if (fileInputStream == null) {
            return Optional.empty();
        }
        return Optional.of(new BufferedReader(new InputStreamReader(fileInputStream)));
    }

    private static String removeComment(String line) {
        int commentIndex = line.indexOf('#');
        return commentIndex >= 0 ? line.substring(0, commentIndex) : line;
    }

}
