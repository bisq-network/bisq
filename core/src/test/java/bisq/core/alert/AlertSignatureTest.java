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

import bisq.common.app.DevEnv;

import org.bitcoinj.core.ECKey;

import java.security.PublicKey;

import java.nio.charset.StandardCharsets;

import java.math.BigInteger;

import java.util.Objects;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import static org.bitcoinj.core.Utils.HEX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertSignatureTest {
    private static final ECKey ALERT_KEY =
            ECKey.fromPrivate(new BigInteger(1, HEX.decode(DevEnv.getDEV_PRIVILEGE_PRIV_KEY())));

    // The vectors encode the field table of the specification. The signed bytes must not change, as nodes of
    // different versions verify the same alert signature.
    @Test
    void messageWithAllFieldsMatchesVersionOneVector() throws Exception {
        TreeMap<String, String> extraDataMap = new TreeMap<>();
        extraDataMap.put("futureKey", "futureValue");
        extraDataMap.put("emptyValue", "");
        extraDataMap.put(AlertSignature.EXTRA_DATA_KEY, "excluded");
        Alert alert = new Alert("Bisq v1.10.10 is released.", true, true, "1.10.10", extraDataMap);
        alert.setSigAndPubKey("message-signature", ownerPubKey(new byte[]{1, 2, 3, 4}));

        assertEquals(vector("alert-signature-v1-all-fields.txt"), AlertSignature.getMessage(alert));
    }

    // Empty strings, false flags and an empty extra data map are omitted
    @Test
    void messageWithEmptyFieldsMatchesVersionOneVector() throws Exception {
        Alert alert = new Alert("notice", false, false, "");
        alert.setSigAndPubKey("m", ownerPubKey(new byte[]{1}));

        assertEquals(vector("alert-signature-v1-empty-fields.txt"), AlertSignature.getMessage(alert));
    }

    @Test
    void signingRequiresTheMessageSignatureAndTheOwnerKey() {
        Alert unsignedAlert = new Alert("notice", false, false, "");

        assertThrows(NullPointerException.class, () -> AlertSignature.sign(unsignedAlert, ALERT_KEY));
    }

    @Test
    void signedAlertVerifiesOnlyWithTheAlertKey() {
        Alert alert = signedAlert();

        assertTrue(AlertSignature.verify(alert, DevEnv.getDEV_PRIVILEGE_PUB_KEY()));
        assertFalse(AlertSignature.verify(alert, HEX.encode(new ECKey().getPubKey())));
    }

    @Test
    void verifyReturnsFalseForMalformedInput() {
        Alert withoutAlertSignature = new Alert("notice", false, false, "");
        Alert withNullMessage = new Alert(null, false, false, "");
        withNullMessage.setAlertSignature("signature");
        Alert withInvalidSignature = new Alert("notice", false, false, "");
        withInvalidSignature.setAlertSignature("not a signature");

        assertFalse(AlertSignature.verify(withoutAlertSignature, DevEnv.getDEV_PRIVILEGE_PUB_KEY()));
        assertFalse(AlertSignature.verify(withNullMessage, DevEnv.getDEV_PRIVILEGE_PUB_KEY()));
        assertFalse(AlertSignature.verify(withInvalidSignature, DevEnv.getDEV_PRIVILEGE_PUB_KEY()));
        assertFalse(AlertSignature.verify(signedAlert(), "not a key"));
    }

    private static Alert signedAlert() {
        Alert alert = new Alert("notice", false, false, "");
        alert.setSigAndPubKey("message-signature", ownerPubKey(new byte[]{1, 2, 3, 4}));
        alert.setAlertSignature(AlertSignature.sign(alert, ALERT_KEY));
        return alert;
    }

    private static PublicKey ownerPubKey(byte[] encoded) {
        PublicKey ownerPubKey = mock(PublicKey.class);
        when(ownerPubKey.getEncoded()).thenReturn(encoded);
        return ownerPubKey;
    }

    private String vector(String resource) throws Exception {
        try (var stream = getClass().getResourceAsStream(resource)) {
            return new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }
}
