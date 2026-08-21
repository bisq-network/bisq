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

public final class AccountAgeWitnessOwnershipProof extends WitnessOwnershipProof {
    public static final int VERSION = 2;
    public static final int MAX_ACCOUNT_INPUT_LENGTH = WitnessOwnershipProof.MAX_ACCOUNT_INPUT_LENGTH;
    private static final String DOMAIN = "BISQ2_ACCOUNT_AGE_REPUTATION_V2";

    public AccountAgeWitnessOwnershipProof(int protocolVersion,
                                           String profileId,
                                           byte[] witnessHash,
                                           byte[] accountInputDataWithSalt,
                                           byte[] ownerPublicKey,
                                           byte[] signature) {
        super(DOMAIN,
                VERSION,
                protocolVersion,
                profileId,
                witnessHash,
                accountInputDataWithSalt,
                ownerPublicKey,
                signature);
    }

    public static byte[] getSignatureMessage(int protocolVersion,
                                             String profileId,
                                             byte[] witnessHash,
                                             byte[] accountInputDataWithSalt,
                                             byte[] ownerPublicKey) {
        return getSignatureMessage(DOMAIN,
                protocolVersion,
                profileId,
                witnessHash,
                accountInputDataWithSalt,
                ownerPublicKey);
    }
}
