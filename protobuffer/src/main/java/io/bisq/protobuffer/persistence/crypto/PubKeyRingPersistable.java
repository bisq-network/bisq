/*
 * This file is part of bisq.
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

package io.bisq.protobuffer.persistence.crypto;

import io.bisq.common.app.Version;
import io.bisq.protobuffer.crypto.PubKeyRingProto;
import io.bisq.protobuffer.persistence.PersistableNew;
import io.bisq.vo.crypto.PubKeyRingVO;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

// TODO remove Serializable, apply final and Value , serialVersionUID
@EqualsAndHashCode(callSuper = true)
@Slf4j
public final class PubKeyRingPersistable extends PubKeyRingProto implements PersistableNew {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public PubKeyRingPersistable(PubKeyRingVO pubKeyRingVO) {
        super(pubKeyRingVO);
    }
}
