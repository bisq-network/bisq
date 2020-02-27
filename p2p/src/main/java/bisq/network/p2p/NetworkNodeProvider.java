/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.network.p2p;

import bisq.network.p2p.network.BridgeAddressProvider;
import bisq.network.p2p.network.LocalhostNetworkNode;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.network.NewTor;
import bisq.network.p2p.network.RunningTor;
import bisq.network.p2p.network.TorNetworkNode;

import bisq.common.config.Config;
import bisq.common.proto.network.NetworkProtoResolver;

import javax.inject.Provider;
import javax.inject.Named;

import javax.inject.Inject;

import java.io.File;

import javax.annotation.Nullable;

public class NetworkNodeProvider implements Provider<NetworkNode> {

    private final NetworkNode networkNode;

    @Inject
    public NetworkNodeProvider(NetworkProtoResolver networkProtoResolver,
                               BridgeAddressProvider bridgeAddressProvider,
                               @Named(Config.USE_LOCALHOST_FOR_P2P) boolean useLocalhostForP2P,
                               @Named(Config.NODE_PORT) int port,
                               @Named(Config.TOR_DIR) File torDir,
                               @Nullable @Named(Config.TORRC_FILE) File torrcFile,
                               @Named(Config.TORRC_OPTIONS) String torrcOptions,
                               @Named(Config.TOR_CONTROL_PORT) int controlPort,
                               @Named(Config.TOR_CONTROL_PASSWORD) String password,
                               @Nullable @Named(Config.TOR_CONTROL_COOKIE_FILE) File cookieFile,
                               @Named(Config.TOR_STREAM_ISOLATION) boolean streamIsolation,
                               @Named(Config.TOR_CONTROL_USE_SAFE_COOKIE_AUTH) boolean useSafeCookieAuthentication ) {
        networkNode = useLocalhostForP2P ?
                new LocalhostNetworkNode(port, networkProtoResolver) :
                new TorNetworkNode(port, networkProtoResolver, streamIsolation,
                        controlPort != Config.UNSPECIFIED_PORT ?
                                new RunningTor(torDir, controlPort, password, cookieFile, useSafeCookieAuthentication) :
                                new NewTor(torDir, torrcFile, torrcOptions, bridgeAddressProvider.getBridgeAddresses()));
    }

    @Override
    public NetworkNode get() {
        return networkNode;
    }
}
