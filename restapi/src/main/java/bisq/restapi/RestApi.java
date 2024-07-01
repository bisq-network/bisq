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

package bisq.restapi;


import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.app.misc.ExecutableForAppWithP2p;
import bisq.core.dao.SignVerifyService;
import bisq.core.dao.governance.bond.reputation.BondedReputationRepository;
import bisq.core.dao.governance.bond.role.BondedRolesRepository;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.DaoStateSnapshotService;
import bisq.core.user.Preferences;

import bisq.common.app.Version;
import bisq.common.config.Config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RestApi extends ExecutableForAppWithP2p {
    @Getter
    private DaoStateService daoStateService;
    @Getter
    private BondedReputationRepository bondedReputationRepository;
    @Getter
    private AccountAgeWitnessService accountAgeWitnessService;
    @Getter
    private BondedRolesRepository bondedRolesRepository;
    @Getter
    private SignVerifyService signVerifyService;
    private DaoStateSnapshotService daoStateSnapshotService;
    private Preferences preferences;

    public RestApi() {
        super("Bisq Rest Api", "bisq_restapi", "bisq_restapi", Version.VERSION);
    }

    public Config getConfig() {
        return config;
    }

    @Override
    protected void doExecute() {
        super.doExecute();

        checkMemory(config, this);
    }

    @Override
    protected void applyInjector() {
        super.applyInjector();

        preferences = injector.getInstance(Preferences.class);
        daoStateService = injector.getInstance(DaoStateService.class);
        accountAgeWitnessService = injector.getInstance(AccountAgeWitnessService.class);
        bondedReputationRepository = injector.getInstance(BondedReputationRepository.class);
        bondedRolesRepository = injector.getInstance(BondedRolesRepository.class);
        signVerifyService = injector.getInstance(SignVerifyService.class);
        daoStateSnapshotService = injector.getInstance(DaoStateSnapshotService.class);
    }

    @Override
    protected void startApplication() {
        super.startApplication();

        daoStateSnapshotService.setResyncDaoStateFromResourcesHandler(this::gracefulShutDown);
    }

    @Override
    protected void onHiddenServicePublished() {
        super.onHiddenServicePublished();

        accountAgeWitnessService.onAllServicesInitialized();
    }
}
