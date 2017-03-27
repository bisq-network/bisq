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

package io.bisq.vo.alert;

import lombok.Value;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.util.Map;

@Value
@Immutable
public final class AlertVO {
    //TODO remove after refact.
    private static final long serialVersionUID = 1;

    private final String message;
    private final String version;
    private final boolean isUpdateInfo;
    @Nullable
    private final String signatureAsBase64;
    @Nullable
    private final byte[] storagePublicKeyBytes;
    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility 
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new 
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    private Map<String, String> extraDataMap;

    public AlertVO(String message,
                   boolean isUpdateInfo,
                   String version,
                   @Nullable byte[] storagePublicKeyBytes,
                   @Nullable String signatureAsBase64,
                   @Nullable Map<String, String> extraDataMap) {
        this.message = message;
        this.isUpdateInfo = isUpdateInfo;
        this.version = version;
        this.storagePublicKeyBytes = storagePublicKeyBytes;
        this.signatureAsBase64 = signatureAsBase64;
        this.extraDataMap = extraDataMap;
    }
}