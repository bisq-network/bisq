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

package bisq.core.support.dispute.messages;

import java.util.Date;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DisputeMessageTest {
    @Test
    public void senderSignaturePubKeyValidationIsNotRequiredBeforeActivation() {
        Date beforeActivation = new Date(DisputeMessage.SENDER_SIGNATURE_PUB_KEY_VALIDATION_ACTIVATION_DATE.getTime() - 1);

        assertFalse(DisputeMessage.isSenderSignaturePubKeyValidationRequired(beforeActivation, null));
        assertFalse(DisputeMessage.isSenderSignaturePubKeyValidationRequired(beforeActivation, beforeActivation));
    }

    @Test
    public void senderSignaturePubKeyValidationIsNotRequiredAfterActivationForPreActivationTrade() {
        Date beforeActivation = new Date(DisputeMessage.SENDER_SIGNATURE_PUB_KEY_VALIDATION_ACTIVATION_DATE.getTime() - 1);
        Date afterActivation = new Date(DisputeMessage.SENDER_SIGNATURE_PUB_KEY_VALIDATION_ACTIVATION_DATE.getTime() + 1);

        assertFalse(DisputeMessage.isSenderSignaturePubKeyValidationRequired(afterActivation, beforeActivation));
    }

    @Test
    public void senderSignaturePubKeyValidationIsRequiredAfterActivationWhenTradeDateIsMissing() {
        Date afterActivation = new Date(DisputeMessage.SENDER_SIGNATURE_PUB_KEY_VALIDATION_ACTIVATION_DATE.getTime() + 1);

        assertTrue(DisputeMessage.isSenderSignaturePubKeyValidationRequired(afterActivation, null));
    }

    @Test
    public void senderSignaturePubKeyValidationIsRequiredAfterActivationWhenTradeDateIsUnsetProtoDefault() {
        Date afterActivation = new Date(DisputeMessage.SENDER_SIGNATURE_PUB_KEY_VALIDATION_ACTIVATION_DATE.getTime() + 1);

        assertTrue(DisputeMessage.isSenderSignaturePubKeyValidationRequired(afterActivation, new Date(0)));
    }

    @Test
    public void senderSignaturePubKeyValidationIsNotRequiredAtActivationInstant() {
        Date activation = DisputeMessage.SENDER_SIGNATURE_PUB_KEY_VALIDATION_ACTIVATION_DATE;

        assertFalse(DisputeMessage.isSenderSignaturePubKeyValidationRequired(activation, null));
        assertFalse(DisputeMessage.isSenderSignaturePubKeyValidationRequired(activation, activation));
    }

    @Test
    public void senderSignaturePubKeyValidationIsRequiredAfterActivationForActivationOrLaterTrade() {
        Date activation = DisputeMessage.SENDER_SIGNATURE_PUB_KEY_VALIDATION_ACTIVATION_DATE;
        Date afterActivation = new Date(activation.getTime() + 1);

        assertTrue(DisputeMessage.isSenderSignaturePubKeyValidationRequired(afterActivation, activation));
        assertTrue(DisputeMessage.isSenderSignaturePubKeyValidationRequired(afterActivation, afterActivation));
    }
}
