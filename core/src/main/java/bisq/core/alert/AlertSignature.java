/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.alert;

import bisq.core.crypto.LowRSigningKey;

import bisq.common.encoding.canonical.Canonical;
import bisq.common.encoding.canonical.CanonicalEncoder;
import bisq.common.encoding.canonical.CanonicalSchema;
import bisq.common.encoding.canonical.TreeMapIterator;
import bisq.common.util.Hex;

import org.bitcoinj.core.ECKey;

import java.util.Map;
import java.util.TreeMap;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The signature of the alert key over all fields of an alert, except this signature itself. It is kept in the extra
 * data map of the alert, next to the message signature of the alert.
 */
final class AlertSignature {
    static final String DOMAIN = "bisq-alert-v1";
    static final String EXTRA_DATA_KEY = "alertSignature";
    private static final int MAX_SIGNATURE_LENGTH = 200;

    private AlertSignature() {
    }

    // The message signature and the owner key are signed, so they must be set before
    static String sign(Alert alert, ECKey alertSigningKey) {
        checkNotNull(alert.getSignatureAsBase64(), "The message signature must be set before the alert is signed");
        checkNotNull(alert.getOwnerPubKeyBytes(), "The owner key must be set before the alert is signed");
        return LowRSigningKey.from(alertSigningKey).signMessage(getMessage(alert));
    }

    static boolean verify(Alert alert, String pubKeyAsHex) {
        String signature = alert.getAlertSignature();
        if (signature == null || signature.length() > MAX_SIGNATURE_LENGTH) {
            return false;
        }
        try {
            ECKey.fromPublicOnly(Hex.decode(pubKeyAsHex)).verifyMessage(getMessage(alert), signature);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static String getMessage(Alert alert) {
        TreeMap<String, String> extraData = alert.getExtraDataMap() == null ?
                new TreeMap<>() :
                new TreeMap<>(alert.getExtraDataMap());
        extraData.remove(EXTRA_DATA_KEY);
        AlertStatement statement = new AlertStatement(DOMAIN,
                alert.getMessage(),
                alert.getVersion(),
                alert.isUpdateInfo(),
                alert.isPreReleaseInfo(),
                alert.getSignatureAsBase64(),
                alert.getOwnerPubKeyBytes(),
                extraData);
        return Hex.encode(statement.encodeCanonical());
    }

    private record AlertStatement(String domain,
                                  String message,
                                  String version,
                                  boolean isUpdateInfo,
                                  boolean isPreReleaseInfo,
                                  String messageSignature,
                                  byte[] ownerPubKeyBytes,
                                  Map<String, String> extraData) implements Canonical {
        private static final CanonicalSchema<AlertStatement> SCHEMA =
                CanonicalSchema.<AlertStatement>newBuilder()
                        .string(1, AlertStatement::domain)
                        .string(2, AlertStatement::message)
                        .string(3, AlertStatement::version)
                        .bool(4, AlertStatement::isUpdateInfo)
                        .bool(5, AlertStatement::isPreReleaseInfo)
                        .string(6, AlertStatement::messageSignature)
                        .bytes(7, AlertStatement::ownerPubKeyBytes)
                        .mapStringToString(8, AlertStatement::extraData, TreeMapIterator.naturalOrder())
                        .build();

        @Override
        public byte[] encodeCanonical(CanonicalEncoder canonicalEncoder) {
            return canonicalEncoder.encode(this, SCHEMA);
        }
    }
}
