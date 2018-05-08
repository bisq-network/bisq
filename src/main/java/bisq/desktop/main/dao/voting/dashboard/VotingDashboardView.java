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

package bisq.desktop.main.dao.voting.dashboard;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.main.dao.proposal.dashboard.ProposalDashboardView;
import bisq.desktop.util.BSFormatter;

import bisq.core.dao.DaoFacade;

import javax.inject.Inject;

@FxmlView
public class VotingDashboardView extends ProposalDashboardView {


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private VotingDashboardView(DaoFacade daoFacade, BSFormatter formatter) {
        super(daoFacade, formatter);
    }

    @Override
    public void initialize() {
        super.initialize();
    }

    @Override
    protected void activate() {
        super.activate();
    }

    @Override
    protected void deactivate() {
        super.deactivate();
    }
}

