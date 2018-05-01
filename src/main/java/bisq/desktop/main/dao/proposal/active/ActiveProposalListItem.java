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

package bisq.desktop.main.dao.proposal.active;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.main.dao.proposal.ProposalListItem;
import bisq.desktop.util.BsqFormatter;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.locale.Res;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@ToString
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class ActiveProposalListItem extends ProposalListItem {
    @Getter
    private AutoTooltipButton removeButton;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    ActiveProposalListItem(Proposal proposal,
                           DaoFacade daoFacade,
                           BsqWalletService bsqWalletService,
                           BsqFormatter bsqFormatter) {
        super(proposal,
                daoFacade,
                bsqWalletService,
                bsqFormatter);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void init() {
        super.init();

        imageView.setId("image-remove");

        removeButton = new AutoTooltipButton();
        removeButton.setMinWidth(70);
        removeButton.setText(Res.get("shared.remove"));
        removeButton.setGraphic(imageView);
    }

    @Override
    public void onPhaseChanged(DaoPhase.Phase phase) {
        super.onPhaseChanged(phase);

        removeButton.setDisable(phase != DaoPhase.Phase.PROPOSAL);

        final boolean myProposal = daoFacade.isMyProposal(proposal);
        removeButton.setVisible(myProposal);
        removeButton.setManaged(myProposal);
    }
}
