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

package bisq.core.btc.wallet.validation;

import org.bitcoinj.core.ECKey;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DelayedPayoutTxSignatureValidationTest {
    @Test
    void checkCanonicalDelayedPayoutTxSignatureAcceptsCanonicalSignature() {
        byte[] signature = canonicalSignature();

        ECKey.ECDSASignature checkedSignature =
                DelayedPayoutTxSignatureValidation.checkCanonicalDelayedPayoutTxSignature(signature, "buyer");

        assertEquals(BigInteger.ONE, checkedSignature.r);
        assertEquals(BigInteger.ONE, checkedSignature.s);
    }

    @Test
    void checkCanonicalDelayedPayoutTxSignatureRejectsHighSBuyerSignature() {
        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxSignatureValidation.checkCanonicalDelayedPayoutTxSignature(highSSignature(),
                        "buyer"));
    }

    @Test
    void checkCanonicalDelayedPayoutTxSignatureRejectsHighSSellerSignature() {
        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxSignatureValidation.checkCanonicalDelayedPayoutTxSignature(highSSignature(),
                        "seller"));
    }

    private static byte[] canonicalSignature() {
        return new ECKey.ECDSASignature(BigInteger.ONE, BigInteger.ONE).encodeToDER();
    }

    private static byte[] highSSignature() {
        return new ECKey.ECDSASignature(BigInteger.ONE, ECKey.CURVE.getN().subtract(BigInteger.ONE)).encodeToDER();
    }
}
