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

package bisq.desktop.main.dao.bonding.roles;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.bond.BondState;
import bisq.core.dao.governance.bond.role.BondedRole;
import bisq.core.dao.state.model.governance.Role;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RolesListItemTest {
    private static final String LOCKUP_TX_ID = "lockupTxId";
    private static final String PROFILE_ID = "profileId";

    private DaoFacade daoFacade;
    private BondedRole bondedRole;
    private Role role;

    @BeforeEach
    public void setup() {
        daoFacade = mock(DaoFacade.class);
        bondedRole = mock(BondedRole.class);
        role = mock(Role.class);
        when(bondedRole.getBondedAsset()).thenReturn(role);
        when(bondedRole.getLockupTxId()).thenReturn(LOCKUP_TX_ID);
        when(bondedRole.getBondState()).thenReturn(BondState.LOCKUP_TX_CONFIRMED);
    }

    @Test
    public void lockupActionIsVisibleOnlyBeforeRoleIsLocked() {
        when(daoFacade.isMyRole(role)).thenReturn(true);

        when(bondedRole.getBondState()).thenReturn(BondState.READY_FOR_LOCKUP);
        assertTrue(new RolesListItem(bondedRole, daoFacade, true).isLockupButtonVisible());

        when(bondedRole.getBondState()).thenReturn(BondState.LOCKUP_TX_CONFIRMED);
        assertFalse(new RolesListItem(bondedRole, daoFacade, true).isLockupButtonVisible());
        assertFalse(new RolesListItem(bondedRole, daoFacade, false).isLockupButtonVisible());
    }

    @Test
    public void unavailableVerificationTxIdIsResolvedOnlyOnce() {
        when(daoFacade.findBondedRoleVerificationTxId(role, LOCKUP_TX_ID)).thenReturn(Optional.empty());
        RolesListItem item = new RolesListItem(bondedRole, daoFacade, false);

        assertNull(item.getVerificationTxId());
        assertNull(item.getVerificationTxId());

        verify(daoFacade, times(1)).findBondedRoleVerificationTxId(role, LOCKUP_TX_ID);
    }

    @Test
    public void unavailableRegistrationMessageIsReportedWithoutThrowing() {
        when(daoFacade.getBondedRoleRegistrationSignatureMessage(role, LOCKUP_TX_ID, PROFILE_ID))
                .thenReturn(Optional.empty());
        RolesListItem item = new RolesListItem(bondedRole, daoFacade, false);

        assertEquals(Optional.empty(), item.getRegistrationSignatureMessage(PROFILE_ID));
    }
}
