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

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@ToString
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class ActiveProposalListItem extends ProposalListItem {
    @Getter
    private AutoTooltipButton button;


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

        button = new AutoTooltipButton();
        button.setMinWidth(70);
        onPhaseChanged(daoFacade.phaseProperty().get());
    }

    @Override
    public void onPhaseChanged(DaoPhase.Phase phase) {
        super.onPhaseChanged(phase);

        if (phase == DaoPhase.Phase.PROPOSAL) {
            imageView.setId("image-remove");
            button.setGraphic(imageView);
            button.setText(Res.get("dao.proposal.active.remove"));
            final boolean isMyProposal = daoFacade.isMyProposal(proposal);
            button.setVisible(isMyProposal);
            button.setManaged(isMyProposal);
        } else if (phase == DaoPhase.Phase.BLIND_VOTE) {
            button.setGraphic(null);
            button.setText(Res.get("dao.proposal.active.vote"));
            button.setVisible(true);
            button.setManaged(true);
        } else {
            button.setVisible(false);
            button.setManaged(false);
        }
    }
}
