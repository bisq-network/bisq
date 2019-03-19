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
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.DaoStateSnapshotService;
import bisq.core.dao.state.model.blockchain.Block;

import javax.inject.Inject;

public class DaoEventCoordinator implements DaoSetupService, DaoStateListener {
    private final DaoStateService daoStateService;
    private final DaoStateSnapshotService daoStateSnapshotService;
    private final DaoStateMonitoringService daoStateMonitoringService;

    @Inject
    public DaoEventCoordinator(DaoStateService daoStateService,
                               DaoStateSnapshotService daoStateSnapshotService,
                               DaoStateMonitoringService daoStateMonitoringService) {
        this.daoStateService = daoStateService;
        this.daoStateSnapshotService = daoStateSnapshotService;
        this.daoStateMonitoringService = daoStateMonitoringService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        this.daoStateService.addDaoStateListener(this);
    }

    @Override
    public void start() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We listen onDaoStateChanged to ensure the dao state has been processed from listener clients after parsing.
    // We need to listen during batch processing as well to write snapshots during that process.
    @Override
    public void onDaoStateChanged(Block block) {
        // We need to execute first the daoStateMonitoringService
        daoStateMonitoringService.createHashFromBlock(block);
        daoStateSnapshotService.maybeCreateSnapshot(block);
    }
}
