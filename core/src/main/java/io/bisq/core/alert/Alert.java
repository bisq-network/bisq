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

import com.google.common.annotations.VisibleForTesting;
import io.bisq.common.app.Version;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

@EqualsAndHashCode
@ToString
@Slf4j
public final class Alert {

    @Delegate
    @Getter
    private AlertVO alertVO;

    public Alert(String message,
                 boolean isUpdateInfo,
                 String version) {
        //TODO builder pattern might be better
        // Or refactor client code so it is not using a half baked object 
        this.alertVO = new AlertVO(message, isUpdateInfo, version, null, null, null);
    }

    public Alert(AlertVO alertVO) {
        this.alertVO = alertVO;
    }

    public void setSigAndPubKey(String signatureAsBase64, PublicKey storagePublicKey) {
        this.alertVO = new AlertVO(alertVO.getMessage(),
                alertVO.isUpdateInfo(),
                alertVO.getVersion(),
                new X509EncodedKeySpec(storagePublicKey.getEncoded()).getEncoded(),
                signatureAsBase64,
                alertVO.getExtraDataMap());
    }

    public boolean isNewVersion() {
        return isNewVersion(Version.VERSION);
    }

    @VisibleForTesting
    protected boolean isNewVersion(String myVersion) {
        // We need to support different version usages (0.5, 0.5.1, 0.5.1.1.1, 0.5.10.1)
        // So we fill up the right part up to 8 digits 0.5 -> 05000000, 0.5.1.1.1 -> 05111000
        // that should handle all cases.
        // TODO make it more elegant and add tests :-)

        // In case the input comes in a corrupted format we don't want to screw up teh app
        try {
            String myVersionString = myVersion.replace(".", "");
            while (myVersionString.length() < 9)
                myVersionString += "0";
            int myVersionNum = Integer.valueOf(myVersionString);
            String alertVersionString = alertVO.getVersion().replace(".", "");
            while (alertVersionString.length() < 9)
                alertVersionString += "0";
            int alertVersionNum = Integer.valueOf(alertVersionString);
            return myVersionNum < alertVersionNum;
        } catch (Throwable t) {
            return false;
        }
    }
}
