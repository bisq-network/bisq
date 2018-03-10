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

package io.bisq.core.app;

import io.bisq.common.crypto.KeyRing;
import io.bisq.core.dao.DaoManager;
import io.bisq.core.dao.request.compensation.CompensationRequestManager;
import io.bisq.core.filter.FilterManager;
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.trade.statistics.TradeStatisticsManager;
import io.bisq.network.crypto.EncryptionService;
import io.bisq.network.p2p.P2PService;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

@Slf4j
public class AppSetupWithP2PAndDAO extends AppSetupWithP2P {
    private final DaoManager daoManager;

    @Inject
    public AppSetupWithP2PAndDAO(EncryptionService encryptionService,
                                 KeyRing keyRing,
                                 P2PService p2PService,
                                 TradeStatisticsManager tradeStatisticsManager,
                                 AccountAgeWitnessService accountAgeWitnessService,
                                 FilterManager filterManager,
                                 DaoManager daoManager,
                                 CompensationRequestManager compensationRequestManager) {
        super(encryptionService,
                keyRing,
                p2PService,
                tradeStatisticsManager,
                accountAgeWitnessService,
                filterManager);
        this.daoManager = daoManager;
        this.persistedDataHosts.add(compensationRequestManager);
    }

    @Override
    protected void onBasicServicesInitialized() {
        super.onBasicServicesInitialized();

        daoManager.onAllServicesInitialized(log::error);
    }
}
