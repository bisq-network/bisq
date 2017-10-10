package io.bisq.core.payment;

import io.bisq.common.crypto.CryptoException;
import io.bisq.common.crypto.Sig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.bitcoinj.core.Sha256Hash;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.GregorianCalendar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
@Slf4j
public class AccountAgeWitnessServiceTest {

    private PublicKey publicKey;
    private KeyPair keypair;
    private AccountAgeWitnessService service;

    @Before
    public void setup() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, CryptoException {
        service = new AccountAgeWitnessService(null, null, null);
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
        assertTrue(service.isTradeDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate));
        tradeDate = new GregorianCalendar(2017, 9, 23).getTime();
        assertTrue(service.isTradeDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate));
        tradeDate = new GregorianCalendar(2017, 9, 22, 0, 0, 1).getTime();
        assertTrue(service.isTradeDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate));
        tradeDate = new GregorianCalendar(2017, 9, 22).getTime();
        assertFalse(service.isTradeDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate));
        tradeDate = new GregorianCalendar(2017, 9, 21).getTime();
        assertFalse(service.isTradeDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate));
    }

    @Test
    public void testVerifySigPubKey() {
        byte[] sigPubKey = Sig.getPublicKeyBytes(publicKey);
        assertFalse(service.verifySigPubKey(new byte[0], publicKey));
        assertFalse(service.verifySigPubKey(new byte[1], publicKey));
        assertTrue(service.verifySigPubKey(sigPubKey, publicKey));
    }

    @Test
    public void testVerifySignature() throws CryptoException {
        byte[] ageWitnessInputData = "test".getBytes(Charset.forName("UTF-8"));
        byte[] salt = "salt".getBytes(Charset.forName("UTF-8"));
        final byte[] combined = ArrayUtils.addAll(ageWitnessInputData, salt);
        byte[] hash = Sha256Hash.hash(combined);
        byte[] signature = Sig.sign(keypair.getPrivate(), hash);
        assertTrue(service.verifySignature(publicKey, hash, signature));
        assertFalse(service.verifySignature(publicKey, new byte[0], new byte[0]));
        assertFalse(service.verifySignature(publicKey, hash, "sig2".getBytes(Charset.forName("UTF-8"))));
        assertFalse(service.verifySignature(publicKey, "hash2".getBytes(Charset.forName("UTF-8")), signature));
    }

    @Test
    public void testVerifySignatureOfNonce() throws CryptoException {
        int nonce = 1234;
        byte[] nonceAsBytes = BigInteger.valueOf(nonce).toByteArray();
        byte[] signature = Sig.sign(keypair.getPrivate(), nonceAsBytes);
        assertTrue(service.verifySignatureOfNonce(publicKey, nonce, signature));
        assertFalse(service.verifySignatureOfNonce(publicKey, nonce, "sig2".getBytes(Charset.forName("UTF-8"))));
        assertFalse(service.verifySignatureOfNonce(publicKey, 0, new byte[0]));
        assertFalse(service.verifySignatureOfNonce(publicKey, 9999, signature));
    }


}
