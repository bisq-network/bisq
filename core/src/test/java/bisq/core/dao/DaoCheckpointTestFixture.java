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

package bisq.core.dao;

import bisq.core.dao.monitoring.DaoStateMonitoringService;
import bisq.core.dao.monitoring.model.DaoStateHash;
import bisq.core.dao.monitoring.network.DaoStateNetworkService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.GenesisTxInfo;
import bisq.core.dao.state.model.DaoState;
import bisq.core.dao.state.storage.DaoStateStorageService;
import bisq.core.user.Preferences;

import bisq.network.p2p.seed.SeedNodeRepository;

import java.util.LinkedList;
import java.util.List;

import static org.mockito.Mockito.mock;

/** Real checkpoint detection and readiness, with all external effects mocked. */
public final class DaoCheckpointTestFixture {
    public static final int CHECKPOINT_HEIGHT = 572000;

    public final DaoStateService daoStateService =
            new DaoStateService(new DaoState(), mock(GenesisTxInfo.class), null);
    public final DaoStateStorageService storage = mock(DaoStateStorageService.class);
    public final DaoStateMonitoringService monitor = new DaoStateMonitoringService(
            daoStateService, storage, mock(DaoStateNetworkService.class), mock(GenesisTxInfo.class),
            mock(SeedNodeRepository.class), mock(Preferences.class), null, false, false);
    public final DaoFacade facade = new DaoFacade(
            null, null, null, null, null, daoStateService, monitor, null,
            null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, storage);

    public DaoCheckpointTestFixture() {
        monitor.addListeners();
        daoStateService.onParseBlockChainComplete();
    }

    public void failCheckpoint() {
        monitor.applySnapshot(new LinkedList<>(List.of(
                new DaoStateHash(CHECKPOINT_HEIGHT, new byte[20], true))));
        daoStateService.onParseBlockChainComplete();
    }
}
