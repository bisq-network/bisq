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

package bisq.core.trade.validation;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Sig;
import bisq.common.util.Base64;

import java.security.PublicKey;

import static bisq.core.util.Validator.checkNonBlankString;
import static bisq.core.util.Validator.checkNonEmptyBytes;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class DsaSignatureValidation {

    /* --------------------------------------------------------------------- */
    // Base64 DSA signature
    /* --------------------------------------------------------------------- */

    public static String checkBase64DSASignature(String dsaSignatureBase64) {
        checkNonBlankString(dsaSignatureBase64, "dsaSignatureBase64");
        fromBase64DSASignature(dsaSignatureBase64);
        return dsaSignatureBase64;
    }

    public static byte[] fromBase64DSASignature(String dsaSignatureBase64) {
        checkNonBlankString(dsaSignatureBase64, "dsaSignatureBase64");
        return Base64.decode(dsaSignatureBase64);
    }


    /* --------------------------------------------------------------------- */
    // DSA signature
    /* --------------------------------------------------------------------- */

    public static byte[] checkDSASignature(byte[] dsaSignature,
                                           byte[] message,
                                           PublicKey signaturePubKey) {
        checkNonEmptyBytes(dsaSignature, "dsaSignature");
        checkNonEmptyBytes(message, "message");
        checkNotNull(signaturePubKey, "signaturePubKey must not be null");

        try {
            checkArgument(Sig.verify(signaturePubKey, message, dsaSignature), "Invalid dsaSignature");
            return dsaSignature;
        } catch (CryptoException e) {
            throw new IllegalArgumentException("Invalid dsaSignature", e);
        }
    }
}
