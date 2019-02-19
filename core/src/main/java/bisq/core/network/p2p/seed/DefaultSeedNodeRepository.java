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

import java.net.URL;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public class DefaultSeedNodeRepository implements SeedNodeRepository {
    private static final Pattern pattern = Pattern.compile("^([a-z0-9]+\\.onion:\\d+)");
    private static final String ENDING = ".seednodes";
    private static final Collection<NodeAddress> cache = new HashSet<>();
    private final BisqEnvironment bisqEnvironment;
    private final String seedNodes;

    @Inject
    public DefaultSeedNodeRepository(BisqEnvironment environment,
                                     @Nullable @Named(NetworkOptionKeys.SEED_NODES_KEY) String seedNodes) {
        bisqEnvironment = environment;
        this.seedNodes = seedNodes;
    }

    private void reload() {

        // see if there are any seed nodes configured manually
        if(seedNodes != null && !seedNodes.isEmpty()) {
            cache.clear();
            Arrays.stream(seedNodes.split(",")).forEach(s -> cache.add(new NodeAddress(s)));

            return;
        }

        // else, we fetch the seed nodes from our resources
        try {
            // read appropriate file
            final URL file = DefaultSeedNodeRepository.class.getClassLoader().getResource(BisqEnvironment.getBaseCurrencyNetwork().getNetwork().toLowerCase() + ENDING);
            final BufferedReader seedNodeFile = new BufferedReader(new FileReader(file.getFile()));

            // only clear if we have a fresh data source (otherwise, an exception would prevent us from getting here)
            cache.clear();

            // refill the cache
            seedNodeFile.lines().forEach(s -> {
                final Matcher matcher = pattern.matcher(s);
                if(matcher.find())
                    cache.add(new NodeAddress(matcher.group(1)));
            });

            // filter
            cache.removeAll(bisqEnvironment.getBannedSeedNodes().stream().map(s -> new NodeAddress(s)).collect(Collectors.toSet()));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public Collection<NodeAddress> getSeedNodeAddresses() {
        if(cache.isEmpty())
            reload();

        return cache;
    }

    public boolean isSeedNode(NodeAddress nodeAddress) {
        if(cache.isEmpty())
            reload();
        return cache.contains(nodeAddress);
    }
}
