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

package bisq.core.dispute;

import bisq.core.app.BisqEnvironment;
import bisq.core.filter.FilterManager;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.HashMapChangedListener;

import bisq.common.app.DevEnv;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.util.Utilities;

import javax.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

/**
 * Used to store disputeResolvers profile and load map of disputeResolvers
 */
@Slf4j
public abstract class DisputeResolverService<T extends DisputeResolver> {
    protected final P2PService p2PService;
    protected final FilterManager filterManager;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DisputeResolverService(P2PService p2PService, FilterManager filterManager) {
        this.p2PService = p2PService;
        this.filterManager = filterManager;
    }

    public void addHashSetChangedListener(HashMapChangedListener hashMapChangedListener) {
        p2PService.addHashSetChangedListener(hashMapChangedListener);
    }

    public void addDisputeResolver(T disputeResolver,
                                   ResultHandler resultHandler,
                                   ErrorMessageHandler errorMessageHandler) {
        log.debug("addDisputeResolver disputeResolver.hashCode() " + disputeResolver.hashCode());
        if (!BisqEnvironment.getBaseCurrencyNetwork().isMainnet() ||
                !Utilities.encodeToHex(disputeResolver.getRegistrationPubKey()).equals(DevEnv.DEV_PRIVILEGE_PUB_KEY)) {
            boolean result = p2PService.addProtectedStorageEntry(disputeResolver, true);
            if (result) {
                log.trace("Add disputeResolver to network was successful. DisputeResolver.hashCode() = " + disputeResolver.hashCode());
                resultHandler.handleResult();
            } else {
                errorMessageHandler.handleErrorMessage("Add disputeResolver failed");
            }
        } else {
            log.error("Attempt to publish dev disputeResolver on mainnet.");
            errorMessageHandler.handleErrorMessage("Add disputeResolver failed. Attempt to publish dev disputeResolver on mainnet.");
        }
    }

    public void removeDisputeResolver(T disputeResolver,
                                      ResultHandler resultHandler,
                                      ErrorMessageHandler errorMessageHandler) {
        log.debug("removeDisputeResolver disputeResolver.hashCode() " + disputeResolver.hashCode());
        if (p2PService.removeData(disputeResolver, true)) {
            log.trace("Remove disputeResolver from network was successful. DisputeResolver.hashCode() = " + disputeResolver.hashCode());
            resultHandler.handleResult();
        } else {
            errorMessageHandler.handleErrorMessage("Remove disputeResolver failed");
        }
    }

    public P2PService getP2PService() {
        return p2PService;
    }

    public Map<NodeAddress, T> getDisputeResolvers() {
        final List<String> bannedDisputeResolvers;
        if (filterManager.getFilter() != null) {
            bannedDisputeResolvers = getDisputeResolversFromFilter();
        } else {
            bannedDisputeResolvers = null;
        }
        if (bannedDisputeResolvers != null)
            log.warn("bannedDisputeResolvers=" + bannedDisputeResolvers);
        Set<T> disputeResolverSet = getCollect(bannedDisputeResolvers);

        Map<NodeAddress, T> map = new HashMap<>();
        for (T disputeResolver : disputeResolverSet) {
            NodeAddress disputeResolverNodeAddress = disputeResolver.getNodeAddress();
            if (!map.containsKey(disputeResolverNodeAddress))
                map.put(disputeResolverNodeAddress, disputeResolver);
            else
                log.warn("disputeResolverAddress already exist in disputeResolver map. Seems an disputeResolver object is already registered with the same address.");
        }
        return map;
    }

    @NotNull
    protected abstract Set<T> getCollect(List<String> bannedDisputeResolvers);

    protected abstract List<String> getDisputeResolversFromFilter();
}
