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
import io.bitsquare.btc.provider.squ.SquUtxoFeedService;
import io.bitsquare.btc.wallet.SquWalletService;
import io.bitsquare.dao.blockchain.SquBlockchainException;
import io.bitsquare.dao.blockchain.SquBlockchainManager;
import io.bitsquare.dao.compensation.CompensationRequestManager;
import io.bitsquare.dao.vote.VotingManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DaoManager {
    private static final Logger log = LoggerFactory.getLogger(DaoManager.class);

    private final SquBlockchainManager squBlockchainManager;
    private final SquWalletService squWalletService;
    private final DaoPeriodService daoPeriodService;
    private final SquUtxoFeedService squUtxoFeedService;
    private final VotingManager voteManager;
    private final CompensationRequestManager compensationRequestManager;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoManager(SquBlockchainManager squBlockchainManager,
                      SquWalletService squWalletService,
                      DaoPeriodService daoPeriodService,
                      SquUtxoFeedService squUtxoFeedService,
                      VotingManager voteManager,
                      CompensationRequestManager compensationRequestManager) {
        this.squBlockchainManager = squBlockchainManager;
        this.squWalletService = squWalletService;
        this.daoPeriodService = daoPeriodService;
        this.squUtxoFeedService = squUtxoFeedService;
        this.voteManager = voteManager;
        this.compensationRequestManager = compensationRequestManager;
    }

    public void onAllServicesInitialized() throws SquBlockchainException {
        daoPeriodService.onAllServicesInitialized();
        squUtxoFeedService.onAllServicesInitialized();
        voteManager.onAllServicesInitialized();
        compensationRequestManager.onAllServicesInitialized();
        squBlockchainManager.onAllServicesInitialized();
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
