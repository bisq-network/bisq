/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.p2p;

import io.bitsquare.p2p.tomp2p.BootstrappedPeerBuilder;

import java.security.KeyPair;

import java.util.concurrent.Executor;

import javafx.beans.property.ReadOnlyIntegerProperty;

import rx.Observable;

public interface ClientNode {
    BootstrappedPeerBuilder.ConnectionType getConnectionType();

    String getClientNodeInfo();

    Node getBootstrapNode();

    Observable<BootstrappedPeerBuilder.State> bootstrap(int networkId, KeyPair keyPair);

    ReadOnlyIntegerProperty numPeersProperty();

    void setExecutor(Executor executor);
}
