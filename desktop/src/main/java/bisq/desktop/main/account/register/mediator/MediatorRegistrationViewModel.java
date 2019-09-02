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

package bisq.desktop.main.account.register.mediator;

import bisq.desktop.main.account.register.DisputeResolverRegistrationViewModel;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.support.dispute.mediation.Mediator;
import bisq.core.support.dispute.mediation.MediatorManager;
import bisq.core.user.User;

import bisq.network.p2p.P2PService;

import bisq.common.crypto.KeyRing;

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Date;

class MediatorRegistrationViewModel extends DisputeResolverRegistrationViewModel<Mediator, MediatorManager> {

    @Inject
    public MediatorRegistrationViewModel(MediatorManager disputeResolverManager,
                                         User user,
                                         P2PService p2PService,
                                         BtcWalletService walletService,
                                         KeyRing keyRing) {
        super(disputeResolverManager, user, p2PService, walletService, keyRing);
    }

    @Override
    protected Mediator getDisputeResolver(String registrationSignature,
                                          String emailAddress) {
        return new Mediator(
                p2PService.getAddress(),
                keyRing.getPubKeyRing(),
                new ArrayList<>(languageCodes),
                new Date().getTime(),
                registrationKey.getPubKey(),
                registrationSignature,
                emailAddress,
                null,
                null
        );
    }

    @Override
    protected Mediator getRegisteredDisputeResolverFromUser() {
        return user.getRegisteredMediator();
    }
}
