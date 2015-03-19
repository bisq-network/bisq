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

package io.bitsquare.network;

import java.security.PublicKey;

import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.FuturePut;
import net.tomp2p.dht.FutureRemove;
import net.tomp2p.peers.Number160;
import net.tomp2p.storage.Data;

public interface DHTService extends P2PService {

    FuturePut putDomainProtectedData(Number160 locationKey, Data data);

    FuturePut putData(Number160 locationKey, Data data);

    FutureGet getDomainProtectedData(Number160 locationKey, PublicKey publicKey);

    FutureGet getData(Number160 locationKey);

    FuturePut addProtectedData(Number160 locationKey, Data data);

    FutureRemove removeFromDataMap(Number160 locationKey, Data data);

    FutureGet getDataMap(Number160 locationKey);
}
