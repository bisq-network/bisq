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

package bisq.core.dao.governance.voteresult;

import bisq.core.dao.DaoSetupService;
import bisq.core.dao.governance.blindvote.network.RepublishGovernanceDataHandler;

import javax.inject.Inject;

public class MissingDataRequestService implements DaoSetupService {
    private final RepublishGovernanceDataHandler republishGovernanceDataHandler;

    @Inject
    public MissingDataRequestService(RepublishGovernanceDataHandler republishGovernanceDataHandler) {
        this.republishGovernanceDataHandler = republishGovernanceDataHandler;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
    }

    @Override
    public void start() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void sendRepublishRequest() {
        republishGovernanceDataHandler.sendRepublishRequest();
    }
}
