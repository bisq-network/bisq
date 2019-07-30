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

package bisq.core.account.sign;


import bisq.core.account.witness.AccountAgeWitness;
import bisq.core.arbitration.ArbitratorManager;

import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.crypto.Sig;
import bisq.common.util.Utilities;

import org.bitcoinj.core.ECKey;

import com.google.common.base.Charsets;

import java.security.KeyPair;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import java.util.Date;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SignedWitnessServiceTest {
    private SignedWitnessService service;
    private byte[] account1DataHash;
    private byte[] account2DataHash;
    private byte[] account3DataHash;
    private long account1CreationTime;
    private long account2CreationTime;
    private long account3CreationTime;
    private AccountAgeWitness aew1;
    private AccountAgeWitness aew2;
    private AccountAgeWitness aew3;
    private ECKey arbitrator1Key;
    private KeyPair peer1KeyPair;
    private KeyPair peer2KeyPair;
    private KeyPair peer3KeyPair;
    private String account1DataHashAsHexString;
    private String account2DataHashAsHexString;
    private String account3DataHashAsHexString;
    private String signature1String;
    private byte[] signature1;
    private byte[] signature2;
    private byte[] signature3;
    private byte[] signer1PubKey;
    private byte[] signer2PubKey;
    private byte[] signer3PubKey;
    private byte[] witnessOwner1PubKey;
    private byte[] witnessOwner2PubKey;
    private byte[] witnessOwner3PubKey;
    private long date1;
    private long date2;
    private long date3;
    private long tradeAmount1;
    private long tradeAmount2;
    private long tradeAmount3;

    @Before
    public void setup() throws Exception {
        AppendOnlyDataStoreService appendOnlyDataStoreService = mock(AppendOnlyDataStoreService.class);
        ArbitratorManager arbitratorManager = mock(ArbitratorManager.class);
        when(arbitratorManager.isPublicKeyInList(any())).thenReturn(true);
        service = new SignedWitnessService(null, null, null, arbitratorManager, null, appendOnlyDataStoreService);
        account1DataHash = org.bitcoinj.core.Utils.sha256hash160(new byte[]{1});
        account2DataHash = org.bitcoinj.core.Utils.sha256hash160(new byte[]{2});
        account3DataHash = org.bitcoinj.core.Utils.sha256hash160(new byte[]{3});
        account1CreationTime = getTodayMinusNDays(96);
        account2CreationTime = getTodayMinusNDays(66);
        account3CreationTime = getTodayMinusNDays(36);
        aew1 = new AccountAgeWitness(account1DataHash, account1CreationTime);
        aew2 = new AccountAgeWitness(account2DataHash, account2CreationTime);
        aew3 = new AccountAgeWitness(account3DataHash, account3CreationTime);
        arbitrator1Key = new ECKey();
        peer1KeyPair = Sig.generateKeyPair();
        peer2KeyPair = Sig.generateKeyPair();
        peer3KeyPair = Sig.generateKeyPair();
        account1DataHashAsHexString = Utilities.encodeToHex(account1DataHash);
        account2DataHashAsHexString = Utilities.encodeToHex(account2DataHash);
        account3DataHashAsHexString = Utilities.encodeToHex(account3DataHash);
        signature1String = arbitrator1Key.signMessage(account1DataHashAsHexString);
        signature1 = signature1String.getBytes(Charsets.UTF_8);
        signature2 = Sig.sign(peer1KeyPair.getPrivate(), account2DataHashAsHexString.getBytes(Charsets.UTF_8));
        date1 = getTodayMinusNDays(95);
        date2 = getTodayMinusNDays(64);
        date3 = getTodayMinusNDays(33);
        signature3 = Sig.sign(peer2KeyPair.getPrivate(), account3DataHashAsHexString.getBytes(Charsets.UTF_8));
        signer1PubKey = arbitrator1Key.getPubKey();
        signer2PubKey = Sig.getPublicKeyBytes(peer1KeyPair.getPublic());
        signer3PubKey = Sig.getPublicKeyBytes(peer2KeyPair.getPublic());
        witnessOwner1PubKey = Sig.getPublicKeyBytes(peer1KeyPair.getPublic());
        witnessOwner2PubKey = Sig.getPublicKeyBytes(peer2KeyPair.getPublic());
        witnessOwner3PubKey = Sig.getPublicKeyBytes(peer3KeyPair.getPublic());
        tradeAmount1 = 1000;
        tradeAmount2 = 1001;
        tradeAmount3 = 1001;
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testIsValidAccountAgeWitnessOk() {
        testIsValidAccountAgeWitness(false, false, false, false);
    }

    @Test
    public void testIsValidAccountAgeWitnessArbitratorSignatureProblem() {
        testIsValidAccountAgeWitness(true, false, false, false);
    }

    @Test
    public void testIsValidAccountAgeWitnessPeerSignatureProblem() {
        testIsValidAccountAgeWitness(false, true, false, false);
    }

    @Test
    public void testIsValidAccountAgeWitnessDateTooSoonProblem() {
        testIsValidAccountAgeWitness(false, false, true, false);
    }

    @Test
    public void testIsValidAccountAgeWitnessDateTooLateProblem() {
        testIsValidAccountAgeWitness(false, false, false, true);
    }

    private void testIsValidAccountAgeWitness(boolean signature1Problem, boolean signature2Problem, boolean date3TooSoonProblem, boolean date3TooLateProblem) {


        if (signature1Problem) {
            signature1 = new byte[]{1, 2, 3};
        }
        if (signature2Problem) {
            signature2 = new byte[]{1, 2, 3};
        }
        if (date3TooSoonProblem) {
            date3 = getTodayMinusNDays(63);
        } else if (date3TooLateProblem) {
            date3 = getTodayMinusNDays(3);
        }

        SignedWitness sw1 = new SignedWitness(true, account1DataHash, signature1, signer1PubKey, witnessOwner1PubKey, date1, tradeAmount1);
        SignedWitness sw2 = new SignedWitness(false, account2DataHash, signature2, signer2PubKey, witnessOwner2PubKey, date2, tradeAmount2);
        SignedWitness sw3 = new SignedWitness(false, account3DataHash, signature3, signer3PubKey, witnessOwner3PubKey, date3, tradeAmount3);

        service.addToMap(sw1);
        service.addToMap(sw2);
        service.addToMap(sw3);

        Assert.assertEquals(!signature1Problem, service.isValidAccountAgeWitness(aew1));
        Assert.assertEquals(!signature1Problem && !signature2Problem, service.isValidAccountAgeWitness(aew2));
        Assert.assertEquals(!signature1Problem && !signature2Problem && !date3TooSoonProblem && !date3TooLateProblem, service.isValidAccountAgeWitness(aew3));
    }


    @Test
    public void testIsValidAccountAgeWitnessEndlessLoop() throws Exception {
        byte[] account1DataHash = org.bitcoinj.core.Utils.sha256hash160(new byte[]{1});
        byte[] account2DataHash = org.bitcoinj.core.Utils.sha256hash160(new byte[]{2});
        byte[] account3DataHash = org.bitcoinj.core.Utils.sha256hash160(new byte[]{3});
        long account1CreationTime = getTodayMinusNDays(96);
        long account2CreationTime = getTodayMinusNDays(66);
        long account3CreationTime = getTodayMinusNDays(36);
        AccountAgeWitness aew1 = new AccountAgeWitness(account1DataHash, account1CreationTime);
        AccountAgeWitness aew2 = new AccountAgeWitness(account2DataHash, account2CreationTime);
        AccountAgeWitness aew3 = new AccountAgeWitness(account3DataHash, account3CreationTime);

        KeyPair peer1KeyPair = Sig.generateKeyPair();
        KeyPair peer2KeyPair = Sig.generateKeyPair();
        KeyPair peer3KeyPair = Sig.generateKeyPair();


        String account1DataHashAsHexString = Utilities.encodeToHex(account1DataHash);
        String account2DataHashAsHexString = Utilities.encodeToHex(account2DataHash);
        String account3DataHashAsHexString = Utilities.encodeToHex(account3DataHash);

        byte[] signature1 = Sig.sign(peer3KeyPair.getPrivate(), account1DataHashAsHexString.getBytes(Charsets.UTF_8));
        byte[] signature2 = Sig.sign(peer1KeyPair.getPrivate(), account2DataHashAsHexString.getBytes(Charsets.UTF_8));
        byte[] signature3 = Sig.sign(peer2KeyPair.getPrivate(), account3DataHashAsHexString.getBytes(Charsets.UTF_8));

        byte[] signer1PubKey = Sig.getPublicKeyBytes(peer3KeyPair.getPublic());
        byte[] signer2PubKey = Sig.getPublicKeyBytes(peer1KeyPair.getPublic());
        byte[] signer3PubKey = Sig.getPublicKeyBytes(peer2KeyPair.getPublic());
        byte[] witnessOwner1PubKey = Sig.getPublicKeyBytes(peer1KeyPair.getPublic());
        byte[] witnessOwner2PubKey = Sig.getPublicKeyBytes(peer2KeyPair.getPublic());
        byte[] witnessOwner3PubKey = Sig.getPublicKeyBytes(peer3KeyPair.getPublic());
        long date1 = getTodayMinusNDays(95);
        long date2 = getTodayMinusNDays(64);
        long date3 = getTodayMinusNDays(33);

        long tradeAmount1 = 1000;
        long tradeAmount2 = 1001;
        long tradeAmount3 = 1001;

        SignedWitness sw1 = new SignedWitness(false, account1DataHash, signature1, signer1PubKey, witnessOwner1PubKey, date1, tradeAmount1);
        SignedWitness sw2 = new SignedWitness(false, account2DataHash, signature2, signer2PubKey, witnessOwner2PubKey, date2, tradeAmount2);
        SignedWitness sw3 = new SignedWitness(false, account3DataHash, signature3, signer3PubKey, witnessOwner3PubKey, date3, tradeAmount3);

        service.addToMap(sw1);
        service.addToMap(sw2);
        service.addToMap(sw3);

        Assert.assertFalse(service.isValidAccountAgeWitness(aew3));
    }


    @Test
    public void testIsValidAccountAgeWitnessLongLoop() throws Exception {
        AccountAgeWitness aew = null;
        KeyPair signerKeyPair = Sig.generateKeyPair();
        KeyPair signedKeyPair = Sig.generateKeyPair();
        int iterations = 1002;
        for (int i = 0; i < iterations; i++) {
            byte[] accountDataHash = org.bitcoinj.core.Utils.sha256hash160(String.valueOf(i).getBytes(Charsets.UTF_8));
            long accountCreationTime = getTodayMinusNDays((iterations - i) * (SignedWitnessService.CHARGEBACK_SAFETY_DAYS + 1));
            aew = new AccountAgeWitness(accountDataHash, accountCreationTime);
            String accountDataHashAsHexString = Utilities.encodeToHex(accountDataHash);
            byte[] signature;
            byte[] signerPubKey;
            if (i == 0) {
                // use arbitrator key
                ECKey arbitratorKey = new ECKey();
                signedKeyPair = Sig.generateKeyPair();
                String signature1String = arbitratorKey.signMessage(accountDataHashAsHexString);
                signature = signature1String.getBytes(Charsets.UTF_8);
                signerPubKey = arbitratorKey.getPubKey();
            } else {
                signerKeyPair = signedKeyPair;
                signedKeyPair = Sig.generateKeyPair();
                signature = Sig.sign(signedKeyPair.getPrivate(), accountDataHashAsHexString.getBytes(Charsets.UTF_8));
                signerPubKey = Sig.getPublicKeyBytes(signerKeyPair.getPublic());
            }
            byte[] witnessOwnerPubKey = Sig.getPublicKeyBytes(signedKeyPair.getPublic());
            long date = getTodayMinusNDays((iterations - i) * (SignedWitnessService.CHARGEBACK_SAFETY_DAYS + 1));
            long tradeAmount = 1000;
            SignedWitness sw = new SignedWitness(i == 0, accountDataHash, signature, signerPubKey, witnessOwnerPubKey, date, tradeAmount);
            service.addToMap(sw);
        }
        Assert.assertFalse(service.isValidAccountAgeWitness(aew));
    }


    private long getTodayMinusNDays(long days) {
        return Instant.ofEpochMilli(new Date().getTime()).minus(days, ChronoUnit.DAYS).toEpochMilli();
    }

}

