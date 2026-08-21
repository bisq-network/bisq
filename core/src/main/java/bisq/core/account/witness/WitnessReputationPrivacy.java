/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
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
import bisq.common.util.Utilities;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

public final class WitnessReputationPrivacy {
    public static final int NULLIFIER_LENGTH = 32;
    public static final long DATE_BUCKET_SIZE_MILLIS = TimeUnit.DAYS.toMillis(1);

    private static final byte[] NULLIFIER_DOMAIN =
            "BISQ2_WITNESS_REPUTATION_NULLIFIER_V1".getBytes(StandardCharsets.UTF_8);

    private WitnessReputationPrivacy() {
    }

    public static byte[] deriveNullifier(WitnessOwnershipProof proof) {
        return deriveNullifier(proof.getAccountInputDataWithSalt(), proof.getOwnerPublicKey());
    }

    static byte[] deriveNullifier(byte[] accountInputDataWithSalt, byte[] ownerPublicKey) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
            writeBytes(dataOutputStream, NULLIFIER_DOMAIN);
            writeBytes(dataOutputStream,
                    Utilities.concatenateByteArrays(accountInputDataWithSalt, ownerPublicKey));
            dataOutputStream.flush();
            byte[] nullifier = Hash.getSha256Hash(outputStream.toByteArray());
            checkArgument(nullifier.length == NULLIFIER_LENGTH,
                    "Unexpected witness nullifier length");
            return nullifier;
        } catch (IOException e) {
            throw new IllegalStateException("Could not derive witness reputation nullifier", e);
        }
    }

    public static long toDateBucket(long authoritativeDate) {
        checkArgument(authoritativeDate > 0, "Witness date must be positive");
        return Math.floorDiv(authoritativeDate, DATE_BUCKET_SIZE_MILLIS) * DATE_BUCKET_SIZE_MILLIS;
    }

    private static void writeBytes(DataOutputStream outputStream, byte[] value) throws IOException {
        outputStream.writeInt(value.length);
        outputStream.write(value);
    }
}
