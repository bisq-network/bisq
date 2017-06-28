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

package io.bisq.core.dao;

import com.google.inject.Inject;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.dao.blockchain.BsqBlockchainManager;
import io.bisq.core.dao.compensation.CompensationRequestManager;
import io.bisq.core.dao.vote.VotingManager;

public class DaoManager {
    private final BsqBlockchainManager bsqBlockchainManager;
    private final DaoPeriodService daoPeriodService;
    private final VotingManager voteManager;
    private final CompensationRequestManager compensationRequestManager;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoManager(BsqBlockchainManager bsqBlockchainManager,
                      DaoPeriodService daoPeriodService,
                      VotingManager voteManager,
                      CompensationRequestManager compensationRequestManager) {
        this.bsqBlockchainManager = bsqBlockchainManager;
        this.daoPeriodService = daoPeriodService;
        this.voteManager = voteManager;
        this.compensationRequestManager = compensationRequestManager;

    }

    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            daoPeriodService.onAllServicesInitialized();
            voteManager.onAllServicesInitialized();
            compensationRequestManager.onAllServicesInitialized();
            bsqBlockchainManager.onAllServicesInitialized(errorMessageHandler);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

}
