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

import bisq.desktop.Navigation;
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.monitoring.DaoStateMonitoringService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;

import javafx.collections.FXCollections;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DaoPresentationTestUtil {
    // A DaoPresentation of a node which has parsed the DAO blockchain and whose DAO state either needs a resync
    // because of a conflict with a seed node or does not.
    public static TestDaoPresentation daoPresentation(boolean inConflictWithSeedNode) {
        DaoStateService daoStateService = mock(DaoStateService.class);
        when(daoStateService.isParseBlockChainComplete()).thenReturn(true);
        DaoStateMonitoringService daoStateMonitoringService = mock(DaoStateMonitoringService.class);
        when(daoStateMonitoringService.isInConflictWithSeedNode()).thenReturn(inConflictWithSeedNode);
        return daoPresentation(daoStateService, daoStateMonitoringService, mock(BsqWalletService.class));
    }

    public static TestDaoPresentation daoPresentation(DaoStateService daoStateService,
                                                      DaoStateMonitoringService daoStateMonitoringService,
                                                      BsqWalletService bsqWalletService) {
        // The resync popup is built from localized texts
        Res.setup();
        Preferences preferences = mock(Preferences.class);
        when(preferences.getDontShowAgainMapAsObservable()).thenReturn(FXCollections.observableHashMap());
        return new TestDaoPresentation(preferences,
                mock(Navigation.class),
                mock(BtcWalletService.class),
                bsqWalletService,
                daoStateService,
                daoStateMonitoringService);
    }

    // Records the resync popups instead of displaying them. A popup counts as displayed or queued until the test
    // closes it.
    public static class TestDaoPresentation extends DaoPresentation {
        public final List<Popup> popups = new ArrayList<>();
        private final Set<Popup> closedPopups = new HashSet<>();

        TestDaoPresentation(Preferences preferences,
                            Navigation navigation,
                            BtcWalletService btcWalletService,
                            BsqWalletService bsqWalletService,
                            DaoStateService daoStateService,
                            DaoStateMonitoringService daoStateMonitoringService) {
            super(preferences, navigation, btcWalletService, bsqWalletService, daoStateService,
                    daoStateMonitoringService);
        }

        public void closePopups() {
            closedPopups.addAll(popups);
        }

        @Override
        void show(Popup popup) {
            popups.add(popup);
        }

        @Override
        boolean isDisplayedOrQueued(Popup popup) {
            return !closedPopups.contains(popup);
        }
    }
}
