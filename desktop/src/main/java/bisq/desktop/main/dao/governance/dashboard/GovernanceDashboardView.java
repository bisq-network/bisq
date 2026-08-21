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

package bisq.desktop.main.dao.governance.dashboard;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.dao.governance.PhasesView;
import bisq.desktop.util.Layout;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.presentation.DaoUtil;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.locale.Res;

import javax.inject.Inject;

import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static bisq.desktop.util.FormBuilder.addTopLabelReadOnlyTextField;

// We use here ChainHeightListener because we are interested in period changes not in the result of a completed
// block. The event from the ChainHeightListener is sent before parsing starts.
// The event from the ChainHeightListener would notify after parsing a new block.
@FxmlView
public class GovernanceDashboardView extends ActivatableView<GridPane, Void> implements DaoStateListener {
    private final DaoFacade daoFacade;
    private final PeriodService periodService;
    private final PhasesView phasesView;

    private int gridRow = 0;
    private TextField currentPhaseTextField, currentBlockHeightTextField, proposalTextField, blindVoteTextField, voteRevealTextField, voteResultTextField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public GovernanceDashboardView(DaoFacade daoFacade, PeriodService periodService, PhasesView phasesView) {
        this.daoFacade = daoFacade;
        this.periodService = periodService;
        this.phasesView = phasesView;
    }

    @Override
    public void initialize() {
        gridRow = phasesView.addGroup(root, gridRow);

        TitledGroupBg titledGroupBg = addTitledGroupBg(root, ++gridRow, 6, Res.get("dao.cycle.overview.headline"), Layout.GROUP_DISTANCE);
        titledGroupBg.getStyleClass().add("last");
        currentBlockHeightTextField = addTopLabelReadOnlyTextField(root, gridRow, Res.get("dao.cycle.currentBlockHeight"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        currentPhaseTextField = addTopLabelReadOnlyTextField(root, ++gridRow, Res.get("dao.cycle.currentPhase")).second;
        proposalTextField = addTopLabelReadOnlyTextField(root, ++gridRow, Res.get("dao.cycle.proposal")).second;
        blindVoteTextField = addTopLabelReadOnlyTextField(root, ++gridRow, Res.get("dao.cycle.blindVote")).second;
        voteRevealTextField = addTopLabelReadOnlyTextField(root, ++gridRow, Res.get("dao.cycle.voteReveal")).second;
        voteResultTextField = addTopLabelReadOnlyTextField(root, ++gridRow, Res.get("dao.cycle.voteResult")).second;
    }

    @Override
    protected void activate() {
        super.activate();

        phasesView.activate();

        daoFacade.addBsqStateListener(this);

        applyData(daoFacade.getChainHeight());
    }

    @Override
    protected void deactivate() {
        super.deactivate();

        phasesView.deactivate();

        daoFacade.removeBsqStateListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        applyData(block.getHeight());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyData(int height) {
        currentBlockHeightTextField.setText(String.valueOf(daoFacade.getChainHeight()));
        DaoPhase.Phase phase = daoFacade.phaseProperty().get();
        // If we are in last block of proposal, blindVote or voteReveal phase we show following break.
        if (!periodService.isInPhaseButNotLastBlock(phase) &&
                (phase == DaoPhase.Phase.PROPOSAL || phase == DaoPhase.Phase.BLIND_VOTE || phase == DaoPhase.Phase.VOTE_REVEAL)) {
            phase = periodService.getPhaseForHeight(height + 1);
        }
        currentPhaseTextField.setText(Res.get("dao.phase." + phase.name()));
        proposalTextField.setText(DaoUtil.getPhaseDuration(height, DaoPhase.Phase.PROPOSAL, daoFacade));
        blindVoteTextField.setText(DaoUtil.getPhaseDuration(height, DaoPhase.Phase.BLIND_VOTE, daoFacade));
        voteRevealTextField.setText(DaoUtil.getPhaseDuration(height, DaoPhase.Phase.VOTE_REVEAL, daoFacade));
        voteResultTextField.setText(DaoUtil.getPhaseDuration(height, DaoPhase.Phase.RESULT, daoFacade));
    }
}
