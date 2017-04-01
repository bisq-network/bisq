/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.dao;

import com.google.inject.Inject;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.core.btc.provider.squ.BsqUtxoFeedService;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.dao.blockchain.BsqBlockchainManager;
import io.bisq.core.dao.compensation.CompensationRequestManager;
import io.bisq.core.dao.vote.VotingManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DaoManager {
    private static final Logger log = LoggerFactory.getLogger(DaoManager.class);

    private final BsqBlockchainManager bsqBlockchainManager;
    private final BsqWalletService bsqWalletService;
    private final DaoPeriodService daoPeriodService;
    private final BsqUtxoFeedService bsqUtxoFeedService;
    private final VotingManager voteManager;
    private final CompensationRequestManager compensationRequestManager;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoManager(BsqBlockchainManager bsqBlockchainManager,
                      BsqWalletService bsqWalletService,
                      DaoPeriodService daoPeriodService,
                      BsqUtxoFeedService bsqUtxoFeedService,
                      VotingManager voteManager,
                      CompensationRequestManager compensationRequestManager) {
        this.bsqBlockchainManager = bsqBlockchainManager;
        this.bsqWalletService = bsqWalletService;
        this.daoPeriodService = daoPeriodService;
        this.bsqUtxoFeedService = bsqUtxoFeedService;
        this.voteManager = voteManager;
        this.compensationRequestManager = compensationRequestManager;
    }

    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        daoPeriodService.onAllServicesInitialized();
        bsqUtxoFeedService.onAllServicesInitialized();
        voteManager.onAllServicesInitialized();
        compensationRequestManager.onAllServicesInitialized();
        bsqBlockchainManager.onAllServicesInitialized(errorMessageHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

}
