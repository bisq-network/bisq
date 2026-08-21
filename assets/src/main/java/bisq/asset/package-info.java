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

/**
 * Bisq's family of abstractions representing different ("crypto")
 * {@link bisq.asset.Asset} types such as {@link bisq.asset.Coin},
 * {@link bisq.asset.Token} and {@link bisq.asset.Erc20Token}, as well as concrete
 * implementations of each, such as {@link bisq.asset.coins.Bitcoin} itself, altcoins like
 * {@link bisq.asset.coins.Litecoin} and {@link bisq.asset.coins.Ether} and tokens like
 * {@link bisq.asset.tokens.DaiStablecoin}.
 * <p>
 * The purpose of this package is to provide everything necessary for registering
 * ("listing") new assets and managing / accessing those assets within, e.g. the Bisq
 * Desktop UI.
 * <p>
 * Note that everything within this package is intentionally designed to be simple and
 * low-level with no dependencies on any other Bisq packages or components.
 *
 * @author Chris Beams
 * @since 0.7.0
 */

package bisq.asset;
