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

package bisq.desktop.main.dao.proposal;

import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.util.Layout;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import javafx.geometry.Insets;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;

public class ProposalDetailsWindow extends Overlay<ProposalDetailsWindow> {
    private final BsqFormatter bsqFormatter;
    private final BsqWalletService bsqWalletService;
    private final Proposal proposal;
    private final DaoFacade daoFacade;

    private ProposalDisplay proposalDisplay;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ProposalDetailsWindow(BsqFormatter bsqFormatter, BsqWalletService bsqWalletService, Proposal proposal,
                                 DaoFacade daoFacade) {
        this.bsqFormatter = bsqFormatter;
        this.bsqWalletService = bsqWalletService;
        this.proposal = proposal;
        this.daoFacade = daoFacade;

        type = Type.Confirmation;
        width = 950;
    }

    public void show() {
        createGridPane();

        proposalDisplay = new ProposalDisplay(gridPane, bsqFormatter, bsqWalletService, daoFacade);
        proposalDisplay.createAllFields(Res.get("dao.proposal.details"), 1, Layout.GROUP_DISTANCE,
                proposal.getType(), false, true);

        proposalDisplay.setEditable(false);
        proposalDisplay.applyProposalPayload(proposal);

        closeButton = addButtonAfterGroup(gridPane, proposalDisplay.incrementAndGetGridRow(), Res.get("shared.close"));
        closeButton.setOnAction(e -> doClose());

        display();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void cleanup() {
        proposalDisplay.clearForm();
    }

    @Override
    protected void createGridPane() {
        super.createGridPane();
        gridPane.setPadding(new Insets(-10, 40, 30, 40));
        gridPane.getStyleClass().add("grid-pane");
    }
}
