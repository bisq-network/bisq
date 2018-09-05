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
 * Abstract base class for {@link Asset}s with their own dedicated blockchain, such as
 * {@link bisq.asset.coins.Bitcoin} itself or one of its many derivatives, competitors and
 * alternatives, often called "altcoins", such as {@link bisq.asset.coins.Litecoin},
 * {@link bisq.asset.coins.Ether}, {@link bisq.asset.coins.Monero} and
 * {@link bisq.asset.coins.Zcash}.
 * <p>
 * In addition to the usual {@code Asset} properties, a {@code Coin} maintains information
 * about which {@link Network} it may be used on. By default, coins are constructed with
 * the assumption they are for use on that coin's "main network", or "main blockchain",
 * i.e. that they are "real" coins for use in a production environment. In testing
 * scenarios, however, a coin may be constructed for use only on "testnet" or "regtest"
 * networks.
 *
 * @author Chris Beams
 * @since 0.7.0
 */
public abstract class Coin extends AbstractAsset {

    public enum Network { MAINNET, TESTNET, REGTEST }

    private final Network network;

    public Coin(String name, String tickerSymbol, AddressValidator addressValidator) {
        this(name, tickerSymbol, addressValidator, Network.MAINNET);
    }

    public Coin(String name, String tickerSymbol, AddressValidator addressValidator, Network network) {
        super(name, tickerSymbol, addressValidator);
        this.network = network;
    }

    public Network getNetwork() {
        return network;
    }
}
