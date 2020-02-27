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

package bisq.core.support.dispute.mediation.mediator;

import bisq.core.filter.FilterManager;
import bisq.core.support.dispute.agent.DisputeAgentManager;
import bisq.core.user.User;

import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;

import javax.inject.Singleton;
import javax.inject.Named;

import javax.inject.Inject;

import java.util.List;

@Singleton
public class MediatorManager extends DisputeAgentManager<Mediator> {

    @Inject
    public MediatorManager(KeyRing keyRing,
                           MediatorService mediatorService,
                           User user,
                           FilterManager filterManager,
                           @Named(Config.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        super(keyRing, mediatorService, user, filterManager, useDevPrivilegeKeys);
    }

    @Override
    protected List<String> getPubKeyList() {
        return List.of("03be5471ff9090d322110d87912eefe89871784b1754d0707fdb917be5d88d3809",
                "023736953a5a6638db71d7f78edc38cea0e42143c3b184ee67f331dafdc2c59efa",
                "03d82260038253f7367012a4fc0c52dac74cfc67ac9cfbc3c3ad8fca746d8e5fc6",
                "02dac85f726121ef333d425bc8e13173b5b365a6444176306e6a0a9e76ae1073bd",
                "0342a5b37c8f843c3302e930d0197cdd8948a6f76747c05e138a6671a6a4caf739",
                "027afa67c920867a70dfad77db6c6f74051f5af8bf56a1ad479f0bc4005df92325",
                "03505f44f1893b64a457f8883afdd60774d7f4def6f82bb6f60be83a4b5b85cf82",
                "0277d2d505d28ad67a03b001ef66f0eaaf1184fa87ebeaa937703cec7073cb2e8f",
                "027cb3e9a56a438714e2144e2f75db7293ad967f12d5c29b17623efbd35ddbceb0",
                "03be5471ff9090d322110d87912eefe89871784b1754d0707fdb917be5d88d3809",
                "03756937d33d028eea274a3154775b2bffd076ffcc4a23fe0f9080f8b7fa0dab5b",
                "03d8359823a91736cb7aecfaf756872daf258084133c9dd25b96ab3643707c38ca",
                "03589ed6ded1a1aa92d6ad38bead13e4ad8ba24c60ca6ed8a8efc6e154e3f60add",
                "0356965753f77a9c0e33ca7cc47fd43ce7f99b60334308ad3c11eed3665de79a78",
                "031112eb033ebacb635754a2b7163c68270c9171c40f271e70e37b22a2590d3c18");
    }

    @Override
    protected boolean isExpectedInstance(ProtectedStorageEntry data) {
        return data.getProtectedStoragePayload() instanceof Mediator;
    }

    @Override
    protected void addAcceptedDisputeAgentToUser(Mediator disputeAgent) {
        user.addAcceptedMediator(disputeAgent);
    }

    @Override
    protected void removeAcceptedDisputeAgentFromUser(ProtectedStorageEntry data) {
        user.removeAcceptedMediator((Mediator) data.getProtectedStoragePayload());
    }

    @Override
    protected List<Mediator> getAcceptedDisputeAgentsFromUser() {
        return user.getAcceptedMediators();
    }

    @Override
    protected void clearAcceptedDisputeAgentsAtUser() {
        user.clearAcceptedMediators();
    }

    @Override
    protected Mediator getRegisteredDisputeAgentFromUser() {
        return user.getRegisteredMediator();
    }

    @Override
    protected void setRegisteredDisputeAgentAtUser(Mediator disputeAgent) {
        user.setRegisteredMediator(disputeAgent);
    }
}
