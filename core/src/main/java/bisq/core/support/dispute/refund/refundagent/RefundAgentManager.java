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

package bisq.core.support.dispute.refund.refundagent;

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
public class RefundAgentManager extends DisputeAgentManager<RefundAgent> {

    @Inject
    public RefundAgentManager(KeyRing keyRing,
                              RefundAgentService refundAgentService,
                              User user,
                              FilterManager filterManager,
                              @Named(Config.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        super(keyRing, refundAgentService, user, filterManager, useDevPrivilegeKeys);
    }

    @Override
    protected List<String> getPubKeyList() {
        return List.of("02a25798e256b800d7ea71c31098ac9a47cb20892176afdfeb051f5ded382d44af",
                "0360455d3cffe00ef73cc1284c84eedacc8c5c3374c43f4aac8ffb95f5130b9ef5",
                "03b0513afbb531bc4551b379eba027feddd33c92b5990fd477b0fa6eff90a5b7db",
                "03533fd75fda29c351298e50b8ea696656dcb8ce4e263d10618c6901a50450bf0e",
                "028124436482aa4c61a4bc4097d60c80b09f4285413be3b023a37a0164cbd5d818",
                "0384fcf883116d8e9469720ed7808cc4141f6dc6a5ed23d76dd48f2f5f255590d7",
                "029bd318ecee4e212ff06a4396770d600d72e9e0c6532142a428bdb401491e9721",
                "02e375b4b24d0a858953f7f94666667554d41f78000b9c8a301294223688b29011",
                "0232c088ae7c070de89d2b6c8d485b34bf0e3b2a964a2c6622f39ca501260c23f7",
                "033e047f74f2aa1ce41e8c85731f97ab83d448d65dc8518ab3df4474a5d53a3d19",
                "02f52a8cf373c8cbddb318e523b7f111168bf753fdfb6f8aa81f88c950ede3a5ce",
                "039784029922c54bcd0f0e7f14530f586053a5f4e596e86b3474cd7404657088ae",
                "037969f9d5ab2cc609104c6e61323df55428f8f108c11aab7c7b5f953081d39304",
                "031bd37475b8c5615ac46d6816e791c59d806d72a0bc6739ae94e5fe4545c7f8a6",
                "021bb92c636feacf5b082313eb071a63dfcd26501a48b3cd248e35438e5afb7daf");


    }

    @Override
    protected boolean isExpectedInstance(ProtectedStorageEntry data) {
        return data.getProtectedStoragePayload() instanceof RefundAgent;
    }

    @Override
    protected void addAcceptedDisputeAgentToUser(RefundAgent disputeAgent) {
        user.addAcceptedRefundAgent(disputeAgent);
    }

    @Override
    protected void removeAcceptedDisputeAgentFromUser(ProtectedStorageEntry data) {
        user.removeAcceptedRefundAgent((RefundAgent) data.getProtectedStoragePayload());
    }

    @Override
    protected List<RefundAgent> getAcceptedDisputeAgentsFromUser() {
        return user.getAcceptedRefundAgents();
    }

    @Override
    protected void clearAcceptedDisputeAgentsAtUser() {
        user.clearAcceptedRefundAgents();
    }

    @Override
    protected RefundAgent getRegisteredDisputeAgentFromUser() {
        return user.getRegisteredRefundAgent();
    }

    @Override
    protected void setRegisteredDisputeAgentAtUser(RefundAgent disputeAgent) {
        user.setRegisteredRefundAgent(disputeAgent);
    }
}
