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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.account.witness;

import bisq.common.crypto.Hash;
import bisq.common.crypto.Sig;
import bisq.common.util.Utilities;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountAgeWitnessOwnershipProofTest {
    private static final String PROFILE_ID = "12".repeat(20);
    private static final byte[] ACCOUNT_INPUT = "account-input-with-salt".getBytes();

    @Test
    void ownerKeyAndPreimageProveWitnessOwnership() throws Exception {
        AccountAgeWitnessOwnershipProof proof = createProof(PROFILE_ID, Sig.generateKeyPair(), ACCOUNT_INPUT);

        assertDoesNotThrow(proof::verify);
    }

    @Test
    void harvestedWitnessHashSignedByAnotherKeyIsRejected() throws Exception {
        KeyPair owner = Sig.generateKeyPair();
        AccountAgeWitnessOwnershipProof valid = createProof(PROFILE_ID, owner, ACCOUNT_INPUT);
        KeyPair attacker = Sig.generateKeyPair();
        byte[] attackerKey = Sig.getPublicKeyBytes(attacker.getPublic());
        byte[] attackerSignature = Sig.sign(attacker.getPrivate(),
                AccountAgeWitnessOwnershipProof.getSignatureMessage(
                        AccountAgeWitnessOwnershipProof.VERSION,
                        PROFILE_ID,
                        valid.getWitnessHash(),
                        ACCOUNT_INPUT,
                        attackerKey));
        AccountAgeWitnessOwnershipProof forged = new AccountAgeWitnessOwnershipProof(
                AccountAgeWitnessOwnershipProof.VERSION,
                PROFILE_ID,
                valid.getWitnessHash(),
                ACCOUNT_INPUT,
                attackerKey,
                attackerSignature);

        assertThrows(IllegalArgumentException.class, forged::verify);
    }

    @Test
    void proofCannotMoveToAnotherProfile() throws Exception {
        AccountAgeWitnessOwnershipProof valid = createProof(PROFILE_ID, Sig.generateKeyPair(), ACCOUNT_INPUT);
        AccountAgeWitnessOwnershipProof moved = new AccountAgeWitnessOwnershipProof(
                AccountAgeWitnessOwnershipProof.VERSION,
                "34".repeat(20),
                valid.getWitnessHash(),
                valid.getAccountInputDataWithSalt(),
                valid.getOwnerPublicKey(),
                valid.getSignature());

        assertThrows(IllegalArgumentException.class, moved::verify);
    }

    private static AccountAgeWitnessOwnershipProof createProof(String profileId,
                                                               KeyPair keyPair,
                                                               byte[] accountInput) throws Exception {
        byte[] publicKey = Sig.getPublicKeyBytes(keyPair.getPublic());
        byte[] witnessHash = Hash.getSha256Ripemd160hash(
                Utilities.concatenateByteArrays(accountInput, publicKey));
        byte[] signature = Sig.sign(keyPair.getPrivate(),
                AccountAgeWitnessOwnershipProof.getSignatureMessage(
                        AccountAgeWitnessOwnershipProof.VERSION,
                        profileId,
                        witnessHash,
                        accountInput,
                        publicKey));
        return new AccountAgeWitnessOwnershipProof(
                AccountAgeWitnessOwnershipProof.VERSION,
                profileId,
                witnessHash,
                accountInput,
                publicKey,
                signature);
    }
}
