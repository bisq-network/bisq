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

package bisq.desktop.main.dao.monitor.daostate;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.main.dao.monitor.StateMonitorView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.FormBuilder;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.monitoring.DaoStateMonitoringService;
import bisq.core.dao.monitoring.model.DaoStateBlock;
import bisq.core.dao.monitoring.model.DaoStateHash;
import bisq.core.dao.state.DaoStateService;
import bisq.core.locale.Res;

import javax.inject.Inject;

import java.util.Map;
import java.util.stream.Collectors;

@FxmlView
public class DaoStateMonitorView extends StateMonitorView<DaoStateHash, DaoStateBlock, DaoStateBlockListItem, DaoStateInConflictListItem> {
    private final DaoStateMonitoringService daoStateMonitoringService;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Inject
    private DaoStateMonitorView(DaoStateService daoStateService,
                                DaoFacade daoFacade,
                                DaoStateMonitoringService daoStateMonitoringService) {
        super(daoStateService, daoFacade);

        this.daoStateMonitoringService = daoStateMonitoringService;
    }

    @Override
    public void initialize() {
        FormBuilder.addTitledGroupBg(root, gridRow, 3, Res.get("dao.monitor.daoState.headline"));

        statusTextField = FormBuilder.addTopLabelTextField(root, ++gridRow,
                Res.get("dao.monitor.daoState.state")).second;
        resyncButton = FormBuilder.addButton(root, ++gridRow, Res.get("dao.monitor.daoState.resync"), 10);

        super.initialize();
    }

    @Override
    protected void activate() {
        super.activate();
        daoStateMonitoringService.addListener(this);

        resyncButton.setOnAction(e -> daoFacade.resyncDao(() ->
                new Popup<>().attention(Res.get("setting.preferences.dao.resync.popup"))
                        .useShutDownButton()
                        .hideCloseButton()
                        .show())
        );
    }

    @Override
    protected void deactivate() {
        super.deactivate();
        daoStateMonitoringService.removeListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Implementation abstract methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected DaoStateBlockListItem getStateBlockListItem(DaoStateBlock daoStateBlock) {
        return new DaoStateBlockListItem(daoStateBlock);
    }

    @Override
    protected DaoStateInConflictListItem getStateInConflictListItem(Map.Entry<String, DaoStateHash> mapEntry) {
        return new DaoStateInConflictListItem(mapEntry.getKey(), mapEntry.getValue());
    }

    @Override
    protected String getTableHeadLine() {
        return Res.get("dao.monitor.daoState.table.headline");
    }

    @Override
    protected String getConflictTableHeadLine() {
        return Res.get("dao.monitor.daoState.conflictTable.headline");
    }

    @Override
    protected String getConflictsTableHeader() {
        return Res.get("dao.monitor.daoState.table.numMisMatches");
    }

    @Override
    protected String getPeersTableHeader() {
        return Res.get("dao.monitor.daoState.table.numPeer");
    }

    @Override
    protected String getPrevHashTableHeader() {
        return Res.get("dao.monitor.daoState.table.prev");
    }

    @Override
    protected String getHashTableHeader() {
        return Res.get("dao.monitor.daoState.table.hash");
    }

    @Override
    protected String getBlockHeightTableHeader() {
        return Res.get("dao.monitor.daoState.table.blockHeight");
    }

    @Override
    protected String getRequestHashes() {
        return Res.get("dao.monitor.daoState.conflictTable.request");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Override
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onDataUpdate() {
        isInConflict.set(daoStateMonitoringService.isInConflict());

        if (isInConflict.get()) {
            statusTextField.setText(Res.get("dao.monitor.daoState.daoStateNotInSync"));
            statusTextField.getStyleClass().add("dao-inConflict");
        } else {
            statusTextField.setText(Res.get("dao.monitor.daoState.daoStateInSync"));
            statusTextField.getStyleClass().remove("dao-inConflict");
        }

        listItems.setAll(daoStateMonitoringService.getDaoStateBlockChain().stream()
                .map(this::getStateBlockListItem)
                .collect(Collectors.toList()));

        super.onDataUpdate();
    }

    @Override
    protected void requestHashesFromGenesisBlockHeight(String peerAddress) {
        daoStateMonitoringService.requestHashesFromGenesisBlockHeight(peerAddress);
    }
}
