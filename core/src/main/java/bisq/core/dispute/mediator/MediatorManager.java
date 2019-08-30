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

package bisq.core.dispute.mediator;

import bisq.core.app.AppOptionKeys;
import bisq.core.dispute.DisputeResolverManager;
import bisq.core.filter.FilterManager;
import bisq.core.user.User;

import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.crypto.KeyRing;

import com.google.inject.Singleton;
import com.google.inject.name.Named;

import javax.inject.Inject;

import java.util.List;

@Singleton
public class MediatorManager extends DisputeResolverManager<Mediator> {

    @Inject
    public MediatorManager(KeyRing keyRing,
                           MediatorService disputeResolverService,
                           User user,
                           FilterManager filterManager,
                           @Named(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        super(keyRing, disputeResolverService, user, filterManager, useDevPrivilegeKeys);
    }

    @Override
    protected boolean isExpectedInstance(ProtectedStorageEntry data) {
        return data.getProtectedStoragePayload() instanceof Mediator;
    }

    @Override
    protected void addAcceptedDisputeResolverToUser(Mediator disputeResolver) {
        user.addAcceptedMediator(disputeResolver);
    }

    @Override
    protected void removeAcceptedDisputeResolverFromUser(ProtectedStorageEntry data) {
        user.removeAcceptedMediator((Mediator) data.getProtectedStoragePayload());
    }

    @Override
    protected List<Mediator> getAcceptedDisputeResolversFromUser() {
        return user.getAcceptedMediators();
    }

    @Override
    protected void clearAcceptedDisputeResolversAtUser() {
        user.clearAcceptedMediators();
    }

    @Override
    protected Mediator getRegisteredDisputeResolverFromUser() {
        return user.getRegisteredMediator();
    }

    @Override
    protected void setRegisteredDisputeResolverAtUser(Mediator disputeResolver) {
        user.setRegisteredMediator(disputeResolver);
    }
}
