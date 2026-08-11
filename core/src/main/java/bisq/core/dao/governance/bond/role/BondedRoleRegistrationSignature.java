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

package bisq.core.dao.governance.bond.role;

import bisq.common.crypto.Hash;
import bisq.common.util.Utilities;

import java.nio.charset.StandardCharsets;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Canonical message signed by a bonded-role proposal key when registering that role in Bisq 2.
 */
public final class BondedRoleRegistrationSignature {
    private static final String DOMAIN = "BISQ_BONDED_ROLE_REGISTRATION_V2";

    private BondedRoleRegistrationSignature() {
    }

    public static String getMessage(String proposalTxId,
                                    String lockupTxId,
                                    String profileId) {
        checkNotNull(proposalTxId, "proposalTxId must not be null");
        checkNotNull(lockupTxId, "lockupTxId must not be null");
        checkNotNull(profileId, "profileId must not be null");
        checkArgument(!proposalTxId.isEmpty(), "proposalTxId must not be empty");
        checkArgument(!lockupTxId.isEmpty(), "lockupTxId must not be empty");
        checkArgument(!profileId.isEmpty(), "profileId must not be empty");

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
            writeString(dataOutputStream, DOMAIN);
            writeString(dataOutputStream, proposalTxId);
            writeString(dataOutputStream, lockupTxId);
            writeString(dataOutputStream, profileId);
            dataOutputStream.flush();
            return Utilities.bytesAsHexString(Hash.getSha256Hash(outputStream.toByteArray()));
        } catch (IOException e) {
            throw new IllegalStateException("Could not create bonded-role registration message", e);
        }
    }

    private static void writeString(DataOutputStream outputStream, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        outputStream.writeInt(bytes.length);
        outputStream.write(bytes);
    }
}
