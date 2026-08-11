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

/**
 * The fields required to verify one Bisq 2 bonded-role registration. Version 1 is the historical unbound format;
 * version 2 binds the proposal, lockup, and profile to a proposal-key signature.
 */
public record BondedRoleRegistration(int protocolVersion,
                                     String bondUserName,
                                     String roleType,
                                     String proposalTxId,
                                     String lockupTxId,
                                     String profileId,
                                     String signatureBase64) {
    public static final int LEGACY_PROTOCOL_VERSION = 1;
    public static final int CURRENT_PROTOCOL_VERSION = 2;

    public static BondedRoleRegistration legacy(String bondUserName,
                                                String roleType,
                                                String profileId,
                                                String signatureBase64) {
        return new BondedRoleRegistration(
                LEGACY_PROTOCOL_VERSION,
                bondUserName,
                roleType,
                "",
                "",
                profileId,
                signatureBase64);
    }

    public static BondedRoleRegistration current(String bondUserName,
                                                 String roleType,
                                                 String proposalTxId,
                                                 String lockupTxId,
                                                 String profileId,
                                                 String signatureBase64) {
        return new BondedRoleRegistration(
                CURRENT_PROTOCOL_VERSION,
                bondUserName,
                roleType,
                proposalTxId,
                lockupTxId,
                profileId,
                signatureBase64);
    }
}
