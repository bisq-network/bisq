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

import bisq.network.NetworkOptionKeys;
import bisq.network.p2p.network.BridgeAddressProvider;
import bisq.network.p2p.network.LocalhostNetworkNode;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.network.NewTor;
import bisq.network.p2p.network.RunningTor;
import bisq.network.p2p.network.TorNetworkNode;

import bisq.common.proto.network.NetworkProtoResolver;

import javax.inject.Provider;
import javax.inject.Named;

import javax.inject.Inject;

import java.io.File;

public class NetworkNodeProvider implements Provider<NetworkNode> {

    private final NetworkNode networkNode;

    @Inject
    public NetworkNodeProvider(NetworkProtoResolver networkProtoResolver,
                               BridgeAddressProvider bridgeAddressProvider,
                               @Named(NetworkOptionKeys.USE_LOCALHOST_FOR_P2P) boolean useLocalhostForP2P,
                               @Named(NetworkOptionKeys.PORT_KEY) int port,
                               @Named(NetworkOptionKeys.TOR_DIR) File torDir,
                               @Named(NetworkOptionKeys.TORRC_FILE) String torrcFile,
                               @Named(NetworkOptionKeys.TORRC_OPTIONS) String torrcOptions,
                               @Named(NetworkOptionKeys.EXTERNAL_TOR_CONTROL_PORT) String controlPort,
                               @Named(NetworkOptionKeys.EXTERNAL_TOR_PASSWORD) String password,
                               @Named(NetworkOptionKeys.EXTERNAL_TOR_COOKIE_FILE) String cookieFile,
                               @Named(NetworkOptionKeys.TOR_STREAM_ISOLATION) boolean streamIsolation,
                               @Named(NetworkOptionKeys.EXTERNAL_TOR_USE_SAFECOOKIE) boolean useSafeCookieAuthentication ) {
        networkNode = useLocalhostForP2P ?
                new LocalhostNetworkNode(port, networkProtoResolver) :
                new TorNetworkNode(port, networkProtoResolver, streamIsolation,
                        !controlPort.isEmpty() ?
                                new RunningTor(torDir, Integer.parseInt(controlPort), password, cookieFile, useSafeCookieAuthentication) :
                                new NewTor(torDir, torrcFile, torrcOptions, bridgeAddressProvider.getBridgeAddresses()));
    }

    @Override
    public NetworkNode get() {
        return networkNode;
    }
}
