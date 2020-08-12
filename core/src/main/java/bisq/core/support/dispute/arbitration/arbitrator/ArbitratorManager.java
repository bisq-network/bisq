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

package bisq.core.support.dispute.arbitration.arbitrator;

import bisq.core.filter.FilterManager;
import bisq.core.support.dispute.agent.DisputeAgentManager;
import bisq.core.user.User;

import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.inject.Named;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ArbitratorManager extends DisputeAgentManager<Arbitrator> {

    @Inject
    public ArbitratorManager(KeyRing keyRing,
                             ArbitratorService arbitratorService,
                             User user,
                             FilterManager filterManager,
                             @Named(Config.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        super(keyRing, arbitratorService, user, filterManager, useDevPrivilegeKeys);
    }

    @Override
    protected List<String> getPubKeyList() {
        return List.of("0365c6af94681dbee69de1851f98d4684063bf5c2d64b1c73ed5d90434f375a054",
                "031c502a60f9dbdb5ae5e438a79819e4e1f417211dd537ac12c9bc23246534c4bd",
                "02c1e5a242387b6d5319ce27246cea6edaaf51c3550591b528d2578a4753c56c2c",
                "025c319faf7067d9299590dd6c97fe7e56cd4dac61205ccee1cd1fc390142390a2",
                "038f6e24c2bfe5d51d0a290f20a9a657c270b94ef2b9c12cd15ca3725fa798fc55",
                "0255256ff7fb615278c4544a9bbd3f5298b903b8a011cd7889be19b6b1c45cbefe",
                "024a3a37289f08c910fbd925ebc72b946f33feaeff451a4738ee82037b4cda2e95",
                "02a88b75e9f0f8afba1467ab26799dcc38fd7a6468fb2795444b425eb43e2c10bd",
                "02349a51512c1c04c67118386f4d27d768c5195a83247c150a4b722d161722ba81",
                "03f718a2e0dc672c7cdec0113e72c3322efc70412bb95870750d25c32cd98de17d",
                "028ff47ee2c56e66313928975c58fa4f1b19a0f81f3a96c4e9c9c3c6768075509e",
                "02b517c0cbc3a49548f448ddf004ed695c5a1c52ec110be1bfd65fa0ca0761c94b",
                "03df837a3a0f3d858e82f3356b71d1285327f101f7c10b404abed2abc1c94e7169",
                "0203a90fb2ab698e524a5286f317a183a84327b8f8c3f7fa4a98fec9e1cefd6b72",
                "023c99cc073b851c892d8c43329ca3beb5d2213ee87111af49884e3ce66cbd5ba5");
    }

    @Override
    protected boolean isExpectedInstance(ProtectedStorageEntry data) {
        return data.getProtectedStoragePayload() instanceof Arbitrator;
    }

    @Override
    protected void addAcceptedDisputeAgentToUser(Arbitrator disputeAgent) {
        user.addAcceptedArbitrator(disputeAgent);
    }

    @Override
    protected void removeAcceptedDisputeAgentFromUser(ProtectedStorageEntry data) {
        user.removeAcceptedArbitrator((Arbitrator) data.getProtectedStoragePayload());
    }

    @Override
    protected List<Arbitrator> getAcceptedDisputeAgentsFromUser() {
        return user.getAcceptedArbitrators();
    }

    @Override
    protected void clearAcceptedDisputeAgentsAtUser() {
        user.clearAcceptedArbitrators();
    }

    @Override
    protected Arbitrator getRegisteredDisputeAgentFromUser() {
        return user.getRegisteredArbitrator();
    }

    @Override
    protected void setRegisteredDisputeAgentAtUser(Arbitrator disputeAgent) {
        user.setRegisteredArbitrator(disputeAgent);
    }
}
