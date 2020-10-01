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

package bisq.core.support.dispute.agent;

import bisq.core.filter.FilterManager;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.HashMapChangedListener;

import bisq.common.app.DevEnv;
import bisq.common.config.Config;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.util.Utilities;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

/**
 * Used to store disputeAgents profile and load map of disputeAgents
 */
@Slf4j
public abstract class DisputeAgentService<T extends DisputeAgent> {
    protected final P2PService p2PService;
    protected final FilterManager filterManager;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public DisputeAgentService(P2PService p2PService, FilterManager filterManager) {
        this.p2PService = p2PService;
        this.filterManager = filterManager;
    }

    public void addHashSetChangedListener(HashMapChangedListener hashMapChangedListener) {
        p2PService.addHashSetChangedListener(hashMapChangedListener);
    }

    public void addDisputeAgent(T disputeAgent,
                                ResultHandler resultHandler,
                                ErrorMessageHandler errorMessageHandler) {
        log.debug("addDisputeAgent disputeAgent.hashCode() " + disputeAgent.hashCode());
        if (!Config.baseCurrencyNetwork().isMainnet() ||
                !Utilities.encodeToHex(disputeAgent.getRegistrationPubKey()).equals(DevEnv.DEV_PRIVILEGE_PUB_KEY)) {
            boolean result = p2PService.addProtectedStorageEntry(disputeAgent);
            if (result) {
                log.trace("Add disputeAgent to network was successful. DisputeAgent.hashCode() = {}", disputeAgent.hashCode());
                resultHandler.handleResult();
            } else {
                errorMessageHandler.handleErrorMessage("Add disputeAgent failed");
            }
        } else {
            log.error("Attempt to publish dev disputeAgent on mainnet.");
            errorMessageHandler.handleErrorMessage("Add disputeAgent failed. Attempt to publish dev disputeAgent on mainnet.");
        }
    }

    public void removeDisputeAgent(T disputeAgent,
                                   ResultHandler resultHandler,
                                   ErrorMessageHandler errorMessageHandler) {
        log.debug("removeDisputeAgent disputeAgent.hashCode() " + disputeAgent.hashCode());
        if (p2PService.removeData(disputeAgent)) {
            log.trace("Remove disputeAgent from network was successful. DisputeAgent.hashCode() = {}", disputeAgent.hashCode());
            resultHandler.handleResult();
        } else {
            errorMessageHandler.handleErrorMessage("Remove disputeAgent failed");
        }
    }

    public P2PService getP2PService() {
        return p2PService;
    }

    public Map<NodeAddress, T> getDisputeAgents() {
        final List<String> bannedDisputeAgents;
        if (filterManager.getFilter() != null) {
            bannedDisputeAgents = getDisputeAgentsFromFilter();
        } else {
            bannedDisputeAgents = null;
        }

        if (bannedDisputeAgents != null && !bannedDisputeAgents.isEmpty()) {
            log.warn("bannedDisputeAgents=" + bannedDisputeAgents);
        }

        Set<T> disputeAgentSet = getDisputeAgentSet(bannedDisputeAgents);

        Map<NodeAddress, T> map = new HashMap<>();
        for (T disputeAgent : disputeAgentSet) {
            NodeAddress disputeAgentNodeAddress = disputeAgent.getNodeAddress();
            if (!map.containsKey(disputeAgentNodeAddress))
                map.put(disputeAgentNodeAddress, disputeAgent);
            else
                log.warn("disputeAgentAddress already exists in disputeAgent map. Seems a disputeAgent object is already registered with the same address.");
        }
        return map;
    }

    protected abstract Set<T> getDisputeAgentSet(List<String> bannedDisputeAgents);

    protected abstract List<String> getDisputeAgentsFromFilter();
}
