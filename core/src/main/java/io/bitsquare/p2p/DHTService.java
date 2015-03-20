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

import java.security.PublicKey;

import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.FuturePut;
import net.tomp2p.dht.FutureRemove;
import net.tomp2p.peers.Number160;
import net.tomp2p.storage.Data;

public interface DHTService extends P2PService {

    FuturePut putData(Number160 locationKey, Data data);

    FutureGet getData(Number160 locationKey);

    FuturePut putDataToMyProtectedDomain(Number160 locationKey, Data data);

    FutureGet getDataOfProtectedDomain(Number160 locationKey, PublicKey publicKey);

    FuturePut addProtectedDataToMap(Number160 locationKey, Data data);

    FutureRemove removeProtectedDataFromMap(Number160 locationKey, Data data);

    FutureGet getMap(Number160 locationKey);
}
