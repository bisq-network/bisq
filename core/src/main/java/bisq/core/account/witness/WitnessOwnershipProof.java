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

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Hash;
import bisq.common.crypto.KeyConversionException;
import bisq.common.crypto.Sig;
import bisq.common.util.Hex;
import bisq.common.util.Utilities;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Locale;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public abstract class WitnessOwnershipProof {
    public static final int MAX_ACCOUNT_INPUT_LENGTH = 4096;

    private final String domain;
    private final int supportedVersion;
    private final int protocolVersion;
    private final String profileId;
    private final byte[] witnessHash;
    private final byte[] accountInputDataWithSalt;
    private final byte[] ownerPublicKey;
    private final byte[] signature;

    protected WitnessOwnershipProof(String domain,
                                    int supportedVersion,
                                    int protocolVersion,
                                    String profileId,
                                    byte[] witnessHash,
                                    byte[] accountInputDataWithSalt,
                                    byte[] ownerPublicKey,
                                    byte[] signature) {
        this.domain = checkNotNull(domain);
        this.supportedVersion = supportedVersion;
        this.protocolVersion = protocolVersion;
        this.profileId = checkNotNull(profileId).toLowerCase(Locale.ROOT);
        this.witnessHash = checkNotNull(witnessHash).clone();
        this.accountInputDataWithSalt = checkNotNull(accountInputDataWithSalt).clone();
        this.ownerPublicKey = checkNotNull(ownerPublicKey).clone();
        this.signature = checkNotNull(signature).clone();

        verifyStructure();
    }

    public final void verify() {
        byte[] calculatedHash = Hash.getSha256Ripemd160hash(
                Utilities.concatenateByteArrays(accountInputDataWithSalt, ownerPublicKey));
        checkArgument(Arrays.equals(witnessHash, calculatedHash),
                "Account age witness hash does not match the ownership proof preimage");

        try {
            PublicKey publicKey = Sig.getPublicKeyFromBytes(ownerPublicKey);
            checkArgument(Sig.verify(publicKey, getSignatureMessage(), signature),
                    "Account age witness ownership signature is invalid");
        } catch (CryptoException | KeyConversionException e) {
            throw new IllegalArgumentException("Could not verify account age witness ownership signature", e);
        }
    }

    public final byte[] getSignatureMessage() {
        return getSignatureMessage(domain,
                protocolVersion,
                profileId,
                witnessHash,
                accountInputDataWithSalt,
                ownerPublicKey);
    }

    protected static byte[] getSignatureMessage(String domain,
                                                int protocolVersion,
                                                String profileId,
                                                byte[] witnessHash,
                                                byte[] accountInputDataWithSalt,
                                                byte[] ownerPublicKey) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
            writeBytes(dataOutputStream, domain.getBytes(StandardCharsets.UTF_8));
            dataOutputStream.writeInt(protocolVersion);
            writeBytes(dataOutputStream, profileId.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            writeBytes(dataOutputStream, witnessHash);
            writeBytes(dataOutputStream, accountInputDataWithSalt);
            writeBytes(dataOutputStream, ownerPublicKey);
            dataOutputStream.flush();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not create account age witness ownership message", e);
        }
    }

    public final byte[] getWitnessHash() {
        return witnessHash.clone();
    }

    public final byte[] getAccountInputDataWithSalt() {
        return accountInputDataWithSalt.clone();
    }

    public final byte[] getOwnerPublicKey() {
        return ownerPublicKey.clone();
    }

    public final byte[] getSignature() {
        return signature.clone();
    }

    public final int getProtocolVersion() {
        return protocolVersion;
    }

    public final String getProfileId() {
        return profileId;
    }

    private void verifyStructure() {
        checkArgument(protocolVersion == supportedVersion,
                "Unsupported witness ownership protocol version: %s", protocolVersion);
        checkArgument(profileId.length() == 40, "Profile ID must be 40 hexadecimal characters");
        checkArgument(Hex.decode(profileId).length == 20, "Profile ID must decode to 20 bytes");
        checkArgument(witnessHash.length == 20, "Account age witness hash must be 20 bytes");
        checkArgument(accountInputDataWithSalt.length > 0 &&
                        accountInputDataWithSalt.length <= MAX_ACCOUNT_INPUT_LENGTH,
                "Account input data must contain between 1 and %s bytes", MAX_ACCOUNT_INPUT_LENGTH);
        checkArgument(ownerPublicKey.length >= 300 && ownerPublicKey.length <= 600,
                "Account owner public key has an unexpected size");
        checkArgument(signature.length >= 30 && signature.length <= 60,
                "Account ownership signature has an unexpected size");
    }

    private static void writeBytes(DataOutputStream outputStream, byte[] value) throws IOException {
        outputStream.writeInt(value.length);
        outputStream.write(value);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "protocolVersion=" + protocolVersion +
                ", profileId='" + profileId + '\'' +
                ", witnessHash=" + Hex.encode(witnessHash) +
                ", accountInputDataWithSalt=<redacted>" +
                ", ownerPublicKey=<redacted>" +
                ", signature=<redacted>" +
                '}';
    }
}
