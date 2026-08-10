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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BondedRoleRegistrationSignatureTest {
    // The preimage is the concatenation of the domain tag, proposalTxId, lockupTxId and profileId, each UTF-8 encoded
    // and prefixed by its four-byte big-endian length. Bisq 2 has to reproduce it byte for byte, so the digest is
    // pinned here rather than recomputed from the implementation.
    @Test
    public void canonicalMessageMatchesProtocolTestVector() {
        assertEquals("7f41ce23e74f13be752d8b8806cd4297fc71c7159576327004c166b0ceb19459",
                BondedRoleRegistrationSignature.getMessage(
                        "proposalTx",
                        "1111111111111111111111111111111111111111111111111111111111111111",
                        "profileId"));
    }

    @Test
    public void canonicalMessageRejectsEmptyFields() {
        assertThrows(IllegalArgumentException.class,
                () -> BondedRoleRegistrationSignature.getMessage("", "lockupTx", "profileId"));
    }
}
