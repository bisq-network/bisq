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

package bisq.core.network.p2p.seed;

import bisq.core.app.BisqEnvironment;

import bisq.network.NetworkOptionKeys;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.seed.SeedNodeRepository;

import javax.inject.Inject;
import javax.inject.Named;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

// If a new BaseCurrencyNetwork type gets added we need to add the resource file for it as well!
@Slf4j
public class DefaultSeedNodeRepository implements SeedNodeRepository {
    //TODO add support for localhost addresses
    private static final Pattern pattern = Pattern.compile("^([a-z0-9]+\\.onion:\\d+)");
    private static final String ENDING = ".seednodes";
    private static final Collection<NodeAddress> cache = new HashSet<>();
    private final BisqEnvironment bisqEnvironment;
    @Nullable
    private final String seedNodes;

    @Inject
    public DefaultSeedNodeRepository(BisqEnvironment environment,
                                     @Nullable @Named(NetworkOptionKeys.SEED_NODES_KEY) String seedNodes) {
        bisqEnvironment = environment;
        this.seedNodes = seedNodes;
    }

    private void reload() {
        try {
            // see if there are any seed nodes configured manually
            if (seedNodes != null && !seedNodes.isEmpty()) {
                cache.clear();
                Arrays.stream(seedNodes.split(",")).forEach(s -> cache.add(new NodeAddress(s)));

                return;
            }

            // else, we fetch the seed nodes from our resources
            InputStream fileInputStream = DefaultSeedNodeRepository.class.getClassLoader().getResourceAsStream(BisqEnvironment.getBaseCurrencyNetwork().name().toLowerCase() + ENDING);
            BufferedReader seedNodeFile = new BufferedReader(new InputStreamReader(fileInputStream));

            // only clear if we have a fresh data source (otherwise, an exception would prevent us from getting here)
            cache.clear();

            // refill the cache
            seedNodeFile.lines().forEach(line -> {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find())
                    cache.add(new NodeAddress(matcher.group(1)));

                // Maybe better include in regex...
                if (line.startsWith("localhost"))
                    cache.add(new NodeAddress(line));
            });

            // filter
            cache.removeAll(bisqEnvironment.getBannedSeedNodes().stream().map(NodeAddress::new).collect(Collectors.toSet()));

            log.info("Seed nodes: {}", cache);
        } catch (Throwable t) {
            log.error(t.toString());
            t.printStackTrace();
            throw t;
        }
    }

    public Collection<NodeAddress> getSeedNodeAddresses() {
        if (cache.isEmpty())
            reload();

        return cache;
    }

    public boolean isSeedNode(NodeAddress nodeAddress) {
        if (cache.isEmpty())
            reload();
        return cache.contains(nodeAddress);
    }
}
