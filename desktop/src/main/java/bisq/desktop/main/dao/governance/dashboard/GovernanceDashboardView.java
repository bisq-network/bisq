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
import bisq.desktop.main.dao.governance.PhasesView;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.Layout;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.locale.Res;
import bisq.core.util.BSFormatter;

import javax.inject.Inject;

import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.Locale;

import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static bisq.desktop.util.FormBuilder.addTopLabelReadOnlyTextField;

// We use here ChainHeightListener because we are interested in period changes not in the result of a completed
// block. The event from the ChainHeightListener is sent before parsing starts.
// The event from the ChainHeightListener would notify after parsing a new block.
@FxmlView
public class GovernanceDashboardView extends ActivatableView<GridPane, Void> implements DaoStateListener {
    private final DaoFacade daoFacade;
    private final PhasesView phasesView;
    private final BSFormatter formatter;

    private int gridRow = 0;
    private TextField currentPhaseTextField, currentBlockHeightTextField, proposalTextField, blindVoteTextField, voteRevealTextField, voteResultTextField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public GovernanceDashboardView(DaoFacade daoFacade, PhasesView phasesView, BSFormatter formatter) {
        this.daoFacade = daoFacade;
        this.phasesView = phasesView;
        this.formatter = formatter;
    }

    @Override
    public void initialize() {
        gridRow = phasesView.addGroup(root, gridRow);

        addTitledGroupBg(root, ++gridRow, 6, Res.get("dao.cycle.overview.headline"), Layout.GROUP_DISTANCE);
        currentBlockHeightTextField = addTopLabelReadOnlyTextField(root, gridRow, Res.get("dao.cycle.currentBlockHeight"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        currentPhaseTextField = FormBuilder.addTopLabelReadOnlyTextField(root, ++gridRow, Res.get("dao.cycle.currentPhase")).second;
        proposalTextField = FormBuilder.addTopLabelReadOnlyTextField(root, ++gridRow, Res.get("dao.cycle.proposal")).second;
        blindVoteTextField = FormBuilder.addTopLabelReadOnlyTextField(root, ++gridRow, Res.get("dao.cycle.blindVote")).second;
        voteRevealTextField = FormBuilder.addTopLabelReadOnlyTextField(root, ++gridRow, Res.get("dao.cycle.voteReveal")).second;
        voteResultTextField = FormBuilder.addTopLabelReadOnlyTextField(root, ++gridRow, Res.get("dao.cycle.voteResult")).second;
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
    public void onNewBlockHeight(int height) {
        applyData(height);
    }

    @Override
    public void onParseBlockChainComplete() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyData(int height) {
        currentBlockHeightTextField.setText(String.valueOf(daoFacade.getChainHeight()));
        currentPhaseTextField.setText(Res.get("dao.phase." + daoFacade.phaseProperty().get().name()));
        proposalTextField.setText(getPhaseDuration(height, DaoPhase.Phase.PROPOSAL));
        blindVoteTextField.setText(getPhaseDuration(height, DaoPhase.Phase.BLIND_VOTE));
        voteRevealTextField.setText(getPhaseDuration(height, DaoPhase.Phase.VOTE_REVEAL));
        voteResultTextField.setText(getPhaseDuration(height, DaoPhase.Phase.RESULT));
    }

    private String getPhaseDuration(int height, DaoPhase.Phase phase) {
        long start = daoFacade.getFirstBlockOfPhaseForDisplay(height, phase);
        long end = daoFacade.getLastBlockOfPhaseForDisplay(height, phase);
        long duration = daoFacade.getDurationForPhaseForDisplay(phase);
        long now = new Date().getTime();
        SimpleDateFormat dateFormatter = new SimpleDateFormat("dd MMM", Locale.getDefault());
        SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String startDateTime = formatter.formatDateTime(new Date(now + (start - height) * 10 * 60 * 1000L), dateFormatter, timeFormatter);
        String endDateTime = formatter.formatDateTime(new Date(now + (end - height) * 10 * 60 * 1000L), dateFormatter, timeFormatter);
        String durationTime = formatter.formatDurationAsWords(duration * 10 * 60 * 1000, false, false);
        return Res.get("dao.cycle.phaseDuration", duration, durationTime, start, end, startDateTime, endDateTime);
    }
}
