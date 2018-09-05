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

package bisq.asset;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/**
 * Provides {@link Stream}-based access to {@link Asset} implementations registered in
 * the {@code META-INF/services/bisq.asset.Asset} provider-configuration file.
 *
 * @author Chris Beams
 * @since 0.7.0
 * @see ServiceLoader
 */
public class AssetRegistry {

    private static final List<Asset> registeredAssets = new ArrayList<>();

    static {
        for (Asset asset : ServiceLoader.load(Asset.class)) {
            registeredAssets.add(asset);
        }
    }

    public Stream<Asset> stream() {
        return registeredAssets.stream();
    }
}
