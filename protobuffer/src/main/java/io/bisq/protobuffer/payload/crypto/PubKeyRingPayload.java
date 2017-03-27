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

package io.bisq.protobuffer.payload.crypto;

import io.bisq.protobuffer.crypto.PubKeyRingProto;
import io.bisq.protobuffer.payload.Payload;
import io.bisq.vo.crypto.PubKeyRingVO;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;


@EqualsAndHashCode(callSuper = true)
@Slf4j
public final class PubKeyRingPayload extends PubKeyRingProto implements Payload {
    public PubKeyRingPayload(PubKeyRingVO pubKeyRingVO) {
        super(pubKeyRingVO);
    }
}
