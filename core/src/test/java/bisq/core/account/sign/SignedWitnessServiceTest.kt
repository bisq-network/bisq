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

package bisq.core.account.sign


import bisq.common.crypto.Sig
import bisq.common.util.Utilities
import bisq.core.account.witness.AccountAgeWitness
import bisq.core.arbitration.ArbitratorManager
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService
import com.google.common.base.Charsets
import com.nhaarman.mockitokotlin2.whenever
import org.bitcoinj.core.ECKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import java.security.KeyPair
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class SignedWitnessServiceTest {
    val appendOnlyDataStoreService = mock(AppendOnlyDataStoreService::class.java)
    val arbitratorManager = mock(ArbitratorManager::class.java)
    val signedWitnessService = SignedWitnessService(null, null, null, arbitratorManager, null, appendOnlyDataStoreService)
    private var account1DataHash: ByteArray? = null
    private var account2DataHash: ByteArray? = null
    private var account3DataHash: ByteArray? = null
    private var aew1: AccountAgeWitness? = null
    private var aew2: AccountAgeWitness? = null
    private var aew3: AccountAgeWitness? = null
    private var signature1: ByteArray? = null
    private var signature2: ByteArray? = null
    private var signature3: ByteArray? = null
    private var signer1PubKey: ByteArray? = null
    private var signer2PubKey: ByteArray? = null
    private var signer3PubKey: ByteArray? = null
    private var witnessOwner1PubKey: ByteArray? = null
    private var witnessOwner2PubKey: ByteArray? = null
    private var witnessOwner3PubKey: ByteArray? = null
    private var date1: Long = 0
    private var date2: Long = 0
    private var date3: Long = 0
    private var tradeAmount1: Long = 0
    private var tradeAmount2: Long = 0
    private var tradeAmount3: Long = 0

    @Before
    @Throws(Exception::class)
    fun setup() {
        whenever(arbitratorManager.isPublicKeyInList(ArgumentMatchers.any())).thenReturn(true)
        account1DataHash = org.bitcoinj.core.Utils.sha256hash160(byteArrayOf(1))
        account2DataHash = org.bitcoinj.core.Utils.sha256hash160(byteArrayOf(2))
        account3DataHash = org.bitcoinj.core.Utils.sha256hash160(byteArrayOf(3))
        val account1CreationTime = getTodayMinusNDays(96)
        val account2CreationTime = getTodayMinusNDays(66)
        val account3CreationTime = getTodayMinusNDays(36)
        aew1 = AccountAgeWitness(account1DataHash, account1CreationTime)
        aew2 = AccountAgeWitness(account2DataHash, account2CreationTime)
        aew3 = AccountAgeWitness(account3DataHash, account3CreationTime)
        val arbitrator1Key = ECKey()
        val peer1KeyPair = Sig.generateKeyPair()
        val peer2KeyPair = Sig.generateKeyPair()
        val peer3KeyPair = Sig.generateKeyPair()
        signature1 = arbitrator1Key.signMessage(Utilities.encodeToHex(account1DataHash)).toByteArray(Charsets.UTF_8)
        signature2 = Sig.sign(peer1KeyPair.private, Utilities.encodeToHex(account2DataHash).toByteArray(Charsets.UTF_8))
        signature3 = Sig.sign(peer2KeyPair.private, Utilities.encodeToHex(account3DataHash).toByteArray(Charsets.UTF_8))
        date1 = getTodayMinusNDays(95)
        date2 = getTodayMinusNDays(64)
        date3 = getTodayMinusNDays(33)
        signer1PubKey = arbitrator1Key.pubKey
        signer2PubKey = Sig.getPublicKeyBytes(peer1KeyPair.public)
        signer3PubKey = Sig.getPublicKeyBytes(peer2KeyPair.public)
        witnessOwner1PubKey = Sig.getPublicKeyBytes(peer1KeyPair.public)
        witnessOwner2PubKey = Sig.getPublicKeyBytes(peer2KeyPair.public)
        witnessOwner3PubKey = Sig.getPublicKeyBytes(peer3KeyPair.public)
        tradeAmount1 = 1000
        tradeAmount2 = 1001
        tradeAmount3 = 1001
    }

    @Test
    fun testIsValidAccountAgeWitnessOk() {
        val sw1 = SignedWitness(true, account1DataHash, signature1, signer1PubKey, witnessOwner1PubKey, date1, tradeAmount1)
        val sw2 = SignedWitness(false, account2DataHash, signature2, signer2PubKey, witnessOwner2PubKey, date2, tradeAmount2)
        val sw3 = SignedWitness(false, account3DataHash, signature3, signer3PubKey, witnessOwner3PubKey, date3, tradeAmount3)

        signedWitnessService.addToMap(sw1)
        signedWitnessService.addToMap(sw2)
        signedWitnessService.addToMap(sw3)

        assertTrue(signedWitnessService.isValidAccountAgeWitness(aew1))
        assertTrue(signedWitnessService.isValidAccountAgeWitness(aew2))
        assertTrue(signedWitnessService.isValidAccountAgeWitness(aew3))
    }

    @Test
    fun testIsValidAccountAgeWitnessArbitratorSignatureProblem() {
        signature1 = byteArrayOf(1, 2, 3)

        val sw1 = SignedWitness(true, account1DataHash, signature1, signer1PubKey, witnessOwner1PubKey, date1, tradeAmount1)
        val sw2 = SignedWitness(false, account2DataHash, signature2, signer2PubKey, witnessOwner2PubKey, date2, tradeAmount2)
        val sw3 = SignedWitness(false, account3DataHash, signature3, signer3PubKey, witnessOwner3PubKey, date3, tradeAmount3)

        signedWitnessService.addToMap(sw1)
        signedWitnessService.addToMap(sw2)
        signedWitnessService.addToMap(sw3)

        assertFalse(signedWitnessService.isValidAccountAgeWitness(aew1))
        assertFalse(signedWitnessService.isValidAccountAgeWitness(aew2))
        assertFalse(signedWitnessService.isValidAccountAgeWitness(aew3))
    }

    @Test
    fun testIsValidAccountAgeWitnessPeerSignatureProblem() {
        signature2 = byteArrayOf(1, 2, 3)

        val sw1 = SignedWitness(true, account1DataHash, signature1, signer1PubKey, witnessOwner1PubKey, date1, tradeAmount1)
        val sw2 = SignedWitness(false, account2DataHash, signature2, signer2PubKey, witnessOwner2PubKey, date2, tradeAmount2)
        val sw3 = SignedWitness(false, account3DataHash, signature3, signer3PubKey, witnessOwner3PubKey, date3, tradeAmount3)

        signedWitnessService.addToMap(sw1)
        signedWitnessService.addToMap(sw2)
        signedWitnessService.addToMap(sw3)

        assertTrue(signedWitnessService.isValidAccountAgeWitness(aew1))
        assertFalse(signedWitnessService.isValidAccountAgeWitness(aew2))
        assertFalse(signedWitnessService.isValidAccountAgeWitness(aew3))
    }

    @Test
    fun testIsValidAccountAgeWitnessDateTooSoonProblem() {
        date3 = getTodayMinusNDays(63)

        val sw1 = SignedWitness(true, account1DataHash, signature1, signer1PubKey, witnessOwner1PubKey, date1, tradeAmount1)
        val sw2 = SignedWitness(false, account2DataHash, signature2, signer2PubKey, witnessOwner2PubKey, date2, tradeAmount2)
        val sw3 = SignedWitness(false, account3DataHash, signature3, signer3PubKey, witnessOwner3PubKey, date3, tradeAmount3)

        signedWitnessService.addToMap(sw1)
        signedWitnessService.addToMap(sw2)
        signedWitnessService.addToMap(sw3)

        assertTrue(signedWitnessService.isValidAccountAgeWitness(aew1))
        assertTrue(signedWitnessService.isValidAccountAgeWitness(aew2))
        assertFalse(signedWitnessService.isValidAccountAgeWitness(aew3))
    }

    @Test
    fun testIsValidAccountAgeWitnessDateTooLateProblem() {
        date3 = getTodayMinusNDays(3)

        val sw1 = SignedWitness(true, account1DataHash, signature1, signer1PubKey, witnessOwner1PubKey, date1, tradeAmount1)
        val sw2 = SignedWitness(false, account2DataHash, signature2, signer2PubKey, witnessOwner2PubKey, date2, tradeAmount2)
        val sw3 = SignedWitness(false, account3DataHash, signature3, signer3PubKey, witnessOwner3PubKey, date3, tradeAmount3)

        signedWitnessService.addToMap(sw1)
        signedWitnessService.addToMap(sw2)
        signedWitnessService.addToMap(sw3)

        assertTrue(signedWitnessService.isValidAccountAgeWitness(aew1))
        assertTrue(signedWitnessService.isValidAccountAgeWitness(aew2))
        assertFalse(signedWitnessService.isValidAccountAgeWitness(aew3))
    }


    @Test
    fun testIsValidAccountAgeWitnessEndlessLoop() {
        val account1DataHash = org.bitcoinj.core.Utils.sha256hash160(byteArrayOf(1))
        val account2DataHash = org.bitcoinj.core.Utils.sha256hash160(byteArrayOf(2))
        val account3DataHash = org.bitcoinj.core.Utils.sha256hash160(byteArrayOf(3))
        val account1CreationTime = getTodayMinusNDays(96)
        val account2CreationTime = getTodayMinusNDays(66)
        val account3CreationTime = getTodayMinusNDays(36)
        val aew1 = AccountAgeWitness(account1DataHash, account1CreationTime)
        val aew2 = AccountAgeWitness(account2DataHash, account2CreationTime)
        val aew3 = AccountAgeWitness(account3DataHash, account3CreationTime)

        val peer1KeyPair = Sig.generateKeyPair()
        val peer2KeyPair = Sig.generateKeyPair()
        val peer3KeyPair = Sig.generateKeyPair()


        val account1DataHashAsHexString = Utilities.encodeToHex(account1DataHash)
        val account2DataHashAsHexString = Utilities.encodeToHex(account2DataHash)
        val account3DataHashAsHexString = Utilities.encodeToHex(account3DataHash)

        val signature1 = Sig.sign(peer3KeyPair.private, account1DataHashAsHexString.toByteArray(Charsets.UTF_8))
        val signature2 = Sig.sign(peer1KeyPair.private, account2DataHashAsHexString.toByteArray(Charsets.UTF_8))
        val signature3 = Sig.sign(peer2KeyPair.private, account3DataHashAsHexString.toByteArray(Charsets.UTF_8))

        val signer1PubKey = Sig.getPublicKeyBytes(peer3KeyPair.public)
        val signer2PubKey = Sig.getPublicKeyBytes(peer1KeyPair.public)
        val signer3PubKey = Sig.getPublicKeyBytes(peer2KeyPair.public)
        val witnessOwner1PubKey = Sig.getPublicKeyBytes(peer1KeyPair.public)
        val witnessOwner2PubKey = Sig.getPublicKeyBytes(peer2KeyPair.public)
        val witnessOwner3PubKey = Sig.getPublicKeyBytes(peer3KeyPair.public)
        val date1 = getTodayMinusNDays(95)
        val date2 = getTodayMinusNDays(64)
        val date3 = getTodayMinusNDays(33)

        val tradeAmount1: Long = 1000
        val tradeAmount2: Long = 1001
        val tradeAmount3: Long = 1001

        val sw1 = SignedWitness(false, account1DataHash, signature1, signer1PubKey, witnessOwner1PubKey, date1, tradeAmount1)
        val sw2 = SignedWitness(false, account2DataHash, signature2, signer2PubKey, witnessOwner2PubKey, date2, tradeAmount2)
        val sw3 = SignedWitness(false, account3DataHash, signature3, signer3PubKey, witnessOwner3PubKey, date3, tradeAmount3)

        signedWitnessService.addToMap(sw1)
        signedWitnessService.addToMap(sw2)
        signedWitnessService.addToMap(sw3)

        assertFalse(signedWitnessService.isValidAccountAgeWitness(aew3))
    }


    @Test
    @Throws(Exception::class)
    fun testIsValidAccountAgeWitnessLongLoop() {
        var aew: AccountAgeWitness? = null
        var signerKeyPair: KeyPair
        var signedKeyPair = Sig.generateKeyPair()
        val iterations = 1002
        for (i in 0 until iterations) {
            val accountDataHash = org.bitcoinj.core.Utils.sha256hash160(i.toString().toByteArray(Charsets.UTF_8))
            val accountCreationTime = getTodayMinusNDays((iterations - i) * (SignedWitnessService.CHARGEBACK_SAFETY_DAYS + 1))
            aew = AccountAgeWitness(accountDataHash, accountCreationTime)
            val accountDataHashAsHexString = Utilities.encodeToHex(accountDataHash)
            val signature: ByteArray
            val signerPubKey: ByteArray
            if (i == 0) {
                // use arbitrator key
                val arbitratorKey = ECKey()
                signedKeyPair = Sig.generateKeyPair()
                val signature1String = arbitratorKey.signMessage(accountDataHashAsHexString)
                signature = signature1String.toByteArray(Charsets.UTF_8)
                signerPubKey = arbitratorKey.pubKey
            } else {
                signerKeyPair = signedKeyPair
                signedKeyPair = Sig.generateKeyPair()
                signature = Sig.sign(signedKeyPair.private, accountDataHashAsHexString.toByteArray(Charsets.UTF_8))
                signerPubKey = Sig.getPublicKeyBytes(signerKeyPair.public)
            }
            val witnessOwnerPubKey = Sig.getPublicKeyBytes(signedKeyPair.public)
            val date = getTodayMinusNDays((iterations - i) * (SignedWitnessService.CHARGEBACK_SAFETY_DAYS + 1))
            val sw = SignedWitness(i == 0, accountDataHash, signature, signerPubKey, witnessOwnerPubKey, date, tradeAmount1)
            signedWitnessService.addToMap(sw)
        }
        assertFalse(signedWitnessService.isValidAccountAgeWitness(aew))
    }


    private fun getTodayMinusNDays(days: Long): Long {
        return Instant.ofEpochMilli(Date().time).minus(days, ChronoUnit.DAYS).toEpochMilli()
    }

}

