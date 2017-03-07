/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.dao;

import com.google.inject.Inject;
import io.bitsquare.btc.provider.squ.BsqUtxoFeedService;
import io.bitsquare.btc.wallet.BsqWalletService;
import io.bitsquare.dao.blockchain.BsqBlockchainException;
import io.bitsquare.dao.blockchain.BsqBlockchainManager;
import io.bitsquare.dao.compensation.CompensationRequestManager;
import io.bitsquare.dao.vote.VotingManager;
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

    public void onAllServicesInitialized() throws BsqBlockchainException {
        daoPeriodService.onAllServicesInitialized();
        bsqUtxoFeedService.onAllServicesInitialized();
        voteManager.onAllServicesInitialized();
        compensationRequestManager.onAllServicesInitialized();
        bsqBlockchainManager.onAllServicesInitialized();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


}
