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

package bisq.core.btc;

public class BtcOptionKeys {
    public static final String BTC_NODES = "btcNodes";
    public static final String USE_TOR_FOR_BTC = "useTorForBtc";
    public static final String SOCKS5_DISCOVER_MODE = "socks5DiscoverMode";
    public static final String BASE_CURRENCY_NETWORK = "baseCurrencyNetwork";
    public static final String WALLET_DIR = "walletDir";
    public static final String USER_AGENT = "userAgent";
    public static final String USE_ALL_PROVIDED_NODES = "useAllProvidedNodes"; // We only use onion nodes if tor is enabled. That flag overrides that default behavior.
    public static final String NUM_CONNECTIONS_FOR_BTC = "numConnectionForBtc";
    public static final String REG_TEST_HOST = "bitcoinRegtestHost";
}
