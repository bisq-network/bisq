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

import bisq.common.crypto.Sig;

import org.bitcoinj.core.ECKey;

import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static bisq.core.account.sign.SignedWitness.VerificationMethod.ARBITRATOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The witnesses which the application bundles pass the rules for a stored witness and for its signature, so that a
 * rule or a refreshed resource cannot silently make accounts unsigned.
 */
class BundledSignedWitnessStoreTest {
    @Test
    void everyBundledWitnessPassesTheRules() throws IOException {
        List<SignedWitness> witnesses = readBundledWitnesses();
        List<String> violations = new ArrayList<>();
        int arbitratorWitnesses = 0;
        for (SignedWitness witness : witnesses) {
            if (!witness.verifyHashSize()) {
                violations.add("malformed: " + witness);
            } else if (witness.getVerificationMethod() == ARBITRATOR) {
                arbitratorWitnesses++;
                ECKey key = ECKey.fromPublicOnly(witness.getSignerPubKey());
                if (!SignedWitnessService.isCanonicalMessageSignature(witness.getSignature(), key)) {
                    violations.add("arbitrator signature not canonical: " + witness);
                }
                if (witness.getDate() >= SignedWitnessService.MAINNET_ARBITRATOR_WITNESS_DATE_LIMIT) {
                    violations.add("arbitrator witness after the date limit: " + witness);
                }
            } else {
                if (!SignedWitnessService.isCanonicalDerSignature(witness.getSignature())) {
                    violations.add("trade signature not canonical: " + witness);
                }
                byte[] signerKey = witness.getSignerPubKey();
                if (!Arrays.equals(Sig.getPublicKeyFromBytes(signerKey).getEncoded(), signerKey)) {
                    violations.add("trade signer key not canonical: " + witness);
                }
            }
        }

        assertTrue(witnesses.size() > 27_000, "bundled witnesses: " + witnesses.size());
        assertTrue(arbitratorWitnesses > 1_000, "bundled arbitrator witnesses: " + arbitratorWitnesses);
        assertEquals(List.of(), violations);
    }

    private static List<SignedWitness> readBundledWitnesses() throws IOException {
        try (InputStream inputStream = BundledSignedWitnessStoreTest.class
                .getResourceAsStream("/SignedWitnessStore_BTC_MAINNET")) {
            assertNotNull(inputStream);
            // As PersistenceManager reads a store
            protobuf.PersistableEnvelope envelope = protobuf.PersistableEnvelope.parseDelimitedFrom(inputStream);
            return new ArrayList<>(SignedWitnessStore.fromProto(envelope.getSignedWitnessStore()).getMap().values()
                    .stream()
                    .map(SignedWitness.class::cast)
                    .toList());
        }
    }
}
