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

package bisq.core.account.witness;

import bisq.core.account.sign.SignedWitnessService;
import bisq.core.payment.ChargeBackRisk;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Sig;

import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;

import java.io.IOException;

import java.util.Date;
import java.util.GregorianCalendar;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

// Restricted default Java security policy on Travis does not allow long keys, so test fails.
// Using Utilities.removeCryptographyRestrictions(); did not work.
@Ignore
public class AccountAgeWitnessServiceTest {
    private PublicKey publicKey;
    private KeyPair keypair;
    private AccountAgeWitnessService service;

    @Before
    public void setup() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, CryptoException {
        SignedWitnessService signedWitnessService = mock(SignedWitnessService.class);
        ChargeBackRisk chargeBackRisk = mock(ChargeBackRisk.class);
        service = new AccountAgeWitnessService(null, null, null, signedWitnessService, chargeBackRisk, null, null, null);
        keypair = Sig.generateKeyPair();
        publicKey = keypair.getPublic();
    }

    @After
    public void tearDown() throws IOException {
    }

    @Test
    public void testIsTradeDateAfterReleaseDate() throws CryptoException {
        Date ageWitnessReleaseDate = new GregorianCalendar(2017, 9, 23).getTime();
        Date tradeDate = new GregorianCalendar(2017, 10, 1).getTime();
        assertTrue(service.isDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate, errorMessage -> {
        }));
        tradeDate = new GregorianCalendar(2017, 9, 23).getTime();
        assertTrue(service.isDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate, errorMessage -> {
        }));
        tradeDate = new GregorianCalendar(2017, 9, 22, 0, 0, 1).getTime();
        assertTrue(service.isDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate, errorMessage -> {
        }));
        tradeDate = new GregorianCalendar(2017, 9, 22).getTime();
        assertFalse(service.isDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate, errorMessage -> {
        }));
        tradeDate = new GregorianCalendar(2017, 9, 21).getTime();
        assertFalse(service.isDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate, errorMessage -> {
        }));
    }

    @Test
    public void testVerifySignatureOfNonce() throws CryptoException {
        byte[] nonce = new byte[]{0x01};
        byte[] signature = Sig.sign(keypair.getPrivate(), nonce);
        assertTrue(service.verifySignature(publicKey, nonce, signature, errorMessage -> {
        }));
        assertFalse(service.verifySignature(publicKey, nonce, new byte[]{0x02}, errorMessage -> {
        }));
        assertFalse(service.verifySignature(publicKey, new byte[]{0x03}, signature, errorMessage -> {
        }));
        assertFalse(service.verifySignature(publicKey, new byte[]{0x02}, new byte[]{0x04}, errorMessage -> {
        }));
    }
}
