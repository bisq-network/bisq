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
 * Abstract base class for Ethereum-based {@link Token}s that implement the
 * <a href="https://theethereum.wiki/w/index.php/ERC20_Token_Standard">ERC-20 Token
 * Standard</a>.
 *
 * @author Chris Beams
 * @since 0.7.0
 */
public abstract class Erc20Token extends Token {

    public Erc20Token(String name, String tickerSymbol) {
        super(name, tickerSymbol, new EtherAddressValidator());
    }
}
