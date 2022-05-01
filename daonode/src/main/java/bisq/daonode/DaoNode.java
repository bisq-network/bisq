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

package bisq.daonode;

import bisq.core.app.misc.AppSetup;
import bisq.core.app.misc.AppSetupWithP2PAndDAO;
import bisq.core.dao.state.DaoStateService;
import bisq.core.network.p2p.inventory.GetInventoryRequestHandler;
import bisq.core.user.Preferences;

import com.google.inject.Injector;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;



import bisq.daonode.service.DaoNodeService;

@Slf4j
public class DaoNode {
    @Setter
    private Injector injector;
    private AppSetup appSetup;
    private DaoNodeService daoNodeService;
    private GetInventoryRequestHandler getInventoryRequestHandler;

    public DaoNode() {
    }

    public void startApplication(int restServerPort) {
        appSetup = injector.getInstance(AppSetupWithP2PAndDAO.class);

        // todo should run as full dao node when in production
        injector.getInstance(Preferences.class).setUseFullModeDaoMonitor(false);

        appSetup.start();

        getInventoryRequestHandler = injector.getInstance(GetInventoryRequestHandler.class);
        DaoStateService daoStateService = injector.getInstance(DaoStateService.class);

        daoNodeService = new DaoNodeService(daoStateService);
        daoNodeService.start(restServerPort);
    }

    public void shutDown() {
        getInventoryRequestHandler.shutDown();
        daoNodeService.shutDown();
    }
}
