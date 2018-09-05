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

package bisq.network;

public class NetworkOptionKeys {
    public static final String TOR_DIR = "torDir";
    public static final String USE_LOCALHOST_FOR_P2P = "useLocalhostForP2P";
    public static final String MAX_CONNECTIONS = "maxConnections";
    public static final String PORT_KEY = "nodePort";
    public static final String NETWORK_ID = "networkId";
    public static final String SEED_NODES_KEY = "seedNodes";
    public static final String MY_ADDRESS = "myAddress";
    public static final String BAN_LIST = "banList";
    //SOCKS_5_PROXY_BTC_ADDRESS used in network module so dont move it to BtcOptionKeys
    public static final String SOCKS_5_PROXY_BTC_ADDRESS = "socks5ProxyBtcAddress";
    public static final String SOCKS_5_PROXY_HTTP_ADDRESS = "socks5ProxyHttpAddress";
}
