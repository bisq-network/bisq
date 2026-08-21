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
 * Abstract base class for {@link Asset}s that do not have their own dedicated blockchain,
 * but are rather based on or derived from another blockchain. Contrast with {@link Coin}.
 * Note that this is essentially a "marker" base class in the sense that it (currently)
 * exposes no additional information or functionality beyond that found in
 * {@link AbstractAsset}, but it is nevertheless useful in distinguishing between major
 * different {@code Asset} types.
 *
 * @author Chris Beams
 * @since 0.7.0
 * @see Erc20Token
 */
public abstract class Token extends AbstractAsset {

    public Token(String name, String tickerSymbol, AddressValidator addressValidator) {
        super(name, tickerSymbol, addressValidator);
    }
}
