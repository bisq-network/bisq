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

package bisq.desktop.main.presentation;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.monitoring.DaoStateMonitoringService;
import bisq.core.dao.state.DaoStateService;

import bisq.common.app.DevEnv;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DaoPresentationTest {
    private final DaoStateService daoStateService = mock(DaoStateService.class);
    private final DaoStateMonitoringService daoStateMonitoringService = mock(DaoStateMonitoringService.class);
    private final BsqWalletService bsqWalletService = mock(BsqWalletService.class);
    private DaoPresentationTestUtil.TestDaoPresentation daoPresentation;

    @BeforeEach
    public void setup() {
        when(daoStateService.isParseBlockChainComplete()).thenReturn(true);
        daoPresentation = DaoPresentationTestUtil.daoPresentation(daoStateService,
                daoStateMonitoringService,
                bsqWalletService);
    }

    @AfterEach
    public void tearDown() {
        DevEnv.setDevMode(false);
        DevEnv.setIgnorePopupsInDevMode(true);
    }

    @Test
    public void gateBlocksActionsWhileDaoStateNeedsResync() {
        assertTrue(daoPresentation.isDaoStateInSyncOrShowPopup());

        when(daoStateMonitoringService.isInConflictWithSeedNode()).thenReturn(true);
        assertFalse(daoPresentation.isDaoStateInSyncOrShowPopup());

        when(daoStateMonitoringService.isInConflictWithSeedNode()).thenReturn(false);
        when(daoStateMonitoringService.isDaoStateBlockChainNotConnecting()).thenReturn(true);
        assertFalse(daoPresentation.isDaoStateInSyncOrShowPopup());
    }

    @Test
    public void gateDoesNotBlockBeforeDaoStateIsParsed() {
        when(daoStateService.isParseBlockChainComplete()).thenReturn(false);
        when(daoStateMonitoringService.isInConflictWithSeedNode()).thenReturn(true);

        assertTrue(daoPresentation.isDaoStateInSyncOrShowPopup());
    }

    @Test
    public void reminderIsShownOncePerBlockWhileResyncIsNeeded() {
        when(daoStateMonitoringService.isInConflictWithSeedNode()).thenReturn(true);
        atChainHeight(100);

        daoPresentation.onDaoStateHashesChanged();
        assertFalse(daoPresentation.isResyncReminderDue(100), "no second reminder in the same block");
        assertTrue(daoPresentation.isResyncReminderDue(101), "reminder again at the next block");

        atChainHeight(101);
        daoPresentation.onDaoStateHashesChanged();
        assertFalse(daoPresentation.isResyncReminderDue(101));
    }

    @Test
    public void noReminderWhileDaoStateIsInSync() {
        atChainHeight(100);

        daoPresentation.onDaoStateHashesChanged();

        assertFalse(daoPresentation.isResyncReminderDue(100));
        when(daoStateMonitoringService.isInConflictWithSeedNode()).thenReturn(true);
        assertTrue(daoPresentation.isResyncReminderDue(100), "conflict found later in the block is reminded");
    }

    @Test
    public void noReminderWhileDaoStateIsBehindTheWallet() {
        when(daoStateMonitoringService.isInConflictWithSeedNode()).thenReturn(true);
        when(daoStateService.getChainHeight()).thenReturn(100);
        when(bsqWalletService.getBestChainHeight()).thenReturn(101);

        daoPresentation.onDaoStateHashesChanged();

        assertTrue(daoPresentation.isResyncReminderDue(100), "reminder is still due once the DAO state caught up");
    }

    // Another popup can be displayed, so the resync popup waits in the popup queue. It must not pile up there.
    @Test
    public void reminderAddsNoSecondPopupWhileOneIsDisplayedOrQueued() {
        when(daoStateMonitoringService.isInConflictWithSeedNode()).thenReturn(true);
        atChainHeight(100);
        daoPresentation.onDaoStateHashesChanged();
        assertEquals(1, daoPresentation.popups.size());

        atChainHeight(101);
        daoPresentation.onDaoStateHashesChanged();
        assertEquals(1, daoPresentation.popups.size(), "no second popup while the first is not closed");

        daoPresentation.closePopups();
        atChainHeight(102);
        daoPresentation.onDaoStateHashesChanged();
        assertEquals(2, daoPresentation.popups.size(), "a new popup once the first was closed");
    }

    @Test
    public void gateAddsNoSecondPopupWhileOneIsDisplayedOrQueued() {
        when(daoStateMonitoringService.isInConflictWithSeedNode()).thenReturn(true);
        atChainHeight(100);

        assertFalse(daoPresentation.isDaoStateInSyncOrShowPopup());
        assertFalse(daoPresentation.isDaoStateInSyncOrShowPopup());
        assertEquals(1, daoPresentation.popups.size());

        daoPresentation.closePopups();
        assertFalse(daoPresentation.isDaoStateInSyncOrShowPopup());
        assertEquals(2, daoPresentation.popups.size());
    }

    @Test
    public void gatePopupCountsAsTheReminderOfTheBlock() {
        when(daoStateMonitoringService.isInConflictWithSeedNode()).thenReturn(true);
        atChainHeight(100);
        assertFalse(daoPresentation.isDaoStateInSyncOrShowPopup());
        daoPresentation.closePopups();

        daoPresentation.onDaoStateHashesChanged();

        assertEquals(1, daoPresentation.popups.size(), "no reminder after the gate popup in the same block");
        assertTrue(daoPresentation.isResyncReminderDue(101));
    }

    private void atChainHeight(int chainHeight) {
        when(daoStateService.getChainHeight()).thenReturn(chainHeight);
        when(bsqWalletService.getBestChainHeight()).thenReturn(chainHeight);
    }
}
