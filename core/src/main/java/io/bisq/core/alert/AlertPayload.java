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

package io.bisq.core.alert;

import io.bisq.common.crypto.CryptoUtils;
import io.bisq.network.p2p.storage.payload.StoragePayload;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;
import java.security.PublicKey;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;


@Slf4j
@Immutable
public final class AlertPayload extends AlertProto implements StoragePayload {
    // lazy set
    private transient PublicKey ownerPubKey;

    public AlertPayload(AlertVO alertVO) {
        super(alertVO);
    }

    @Override
    public long getTTL() {
        return TimeUnit.DAYS.toMillis(30);
    }

    @Override
    public PublicKey getOwnerPubKey() {
        try {
            checkNotNull(getStoragePublicKeyBytes(), "alertVO.getStoragePublicKeyBytes() must not be null");
            if (ownerPubKey == null)
                ownerPubKey = CryptoUtils.getPubKeyFromBytes(getStoragePublicKeyBytes());
            return ownerPubKey;
        } catch (Throwable t) {
            log.error(t.toString());
            return null;
        }
    }
}
