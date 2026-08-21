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

/**
 * Interface representing a given ("crypto") asset in its most abstract form, having a
 * {@link #getName() name}, eg "Bitcoin", a {@link #getTickerSymbol() ticker symbol},
 * eg "BTC", and an address validation function. Together, these properties represent
 * the minimum information and functionality required to register and trade an asset on
 * the Bisq network.
 * <p>
 * Implementations typically extend either the {@link Coin} or {@link Token} base
 * classes, and must be registered in the {@code META-INF/services/bisq.asset.Asset} file
 * in order to be available in the {@link AssetRegistry} at runtime.
 *
 * @author Chris Beams
 * @since 0.7.0
 * @see AbstractAsset
 * @see Coin
 * @see Token
 * @see Erc20Token
 * @see AssetRegistry
 */
public interface Asset {

    String getName();

    String getTickerSymbol();

    AddressValidationResult validateAddress(String address);
}
