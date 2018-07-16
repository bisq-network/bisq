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

package bisq.desktop.main.dao.proposal.dashboard;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.SeparatedPhaseBars;
import bisq.desktop.util.Layout;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.ChainHeightListener;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.locale.Res;
import bisq.core.util.BSFormatter;

import bisq.common.UserThread;

import javax.inject.Inject;

import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import javafx.geometry.Insets;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static bisq.desktop.util.FormBuilder.addLabelTextField;
import static bisq.desktop.util.FormBuilder.addMultilineLabel;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

// We use here ChainHeightListener because we are interested in period changes not in the result of a completed
// block. The event from the ChainHeightListener is sent before parsing starts.
// The event from the ChainHeightListener would notify after parsing a new block.
@FxmlView
public class ProposalDashboardView extends ActivatableView<GridPane, Void> implements ChainHeightListener {
    private final DaoFacade daoFacade;
    private final BSFormatter formatter;

    private List<SeparatedPhaseBars.SeparatedPhaseBarsItem> phaseBarsItems;
    private DaoPhase.Phase currentPhase;
    private Subscription phaseSubscription;
    private int gridRow = 0;
    private SeparatedPhaseBars separatedPhaseBars;
    private TextField currentPhaseTextField, currentBlockHeightTextField, proposalTextField, blindVoteTextField, voteRevealTextField, voteResultTextField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProposalDashboardView(DaoFacade daoFacade, BSFormatter formatter) {
        this.daoFacade = daoFacade;
        this.formatter = formatter;
    }

    @Override
    public void initialize() {
        // cycle bar
        addTitledGroupBg(root, gridRow, 1, Res.get("dao.cycle.headline"));
        separatedPhaseBars = createSeparatedPhaseBars();
        GridPane.setColumnSpan(separatedPhaseBars, 2);
        GridPane.setColumnIndex(separatedPhaseBars, 0);
        GridPane.setMargin(separatedPhaseBars, new Insets(Layout.FIRST_ROW_DISTANCE - 6, 0, 0, 0));
        GridPane.setRowIndex(separatedPhaseBars, gridRow);
        root.getChildren().add(separatedPhaseBars);

        addTitledGroupBg(root, ++gridRow, 6, Res.get("dao.cycle.overview.headline"), Layout.GROUP_DISTANCE);
        currentBlockHeightTextField = addLabelTextField(root, gridRow, Res.get("dao.cycle.currentBlockHeight"),
                "", Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        currentPhaseTextField = addLabelTextField(root, ++gridRow, Res.get("dao.cycle.currentPhase"), "").second;
        proposalTextField = addLabelTextField(root, ++gridRow, Res.get("dao.cycle.proposal"), "").second;
        blindVoteTextField = addLabelTextField(root, ++gridRow, Res.get("dao.cycle.blindVote"), "").second;
        voteRevealTextField = addLabelTextField(root, ++gridRow, Res.get("dao.cycle.voteReveal"), "").second;
        voteResultTextField = addLabelTextField(root, ++gridRow, Res.get("dao.cycle.voteResult"), "").second;

        addTitledGroupBg(root, ++gridRow, 1, Res.get("dao.cycle.info.headline"), Layout.GROUP_DISTANCE);
        addMultilineLabel(root, gridRow, Res.get("dao.cycle.info.details"), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
    }

    @Override
    protected void activate() {
        super.activate();

        phaseSubscription = EasyBind.subscribe(daoFacade.phaseProperty(), phase -> {
            if (!phase.equals(this.currentPhase)) {
                this.currentPhase = phase;
            }
            phaseBarsItems.forEach(item -> {
                if (item.getPhase() == phase) {
                    item.setActive();
                } else {
                    item.setInActive();
                }
            });

        });
        daoFacade.addChainHeightListener(this);

        // We need to delay as otherwise the periodService has not been updated yet.
        UserThread.execute(() -> onChainHeightChanged(daoFacade.getChainHeight()));
    }

    @Override
    protected void deactivate() {
        super.deactivate();
        daoFacade.removeChainHeightListener(this);
        phaseSubscription.unsubscribe();
    }

    // ChainHeightListener
    @Override
    public void onChainHeightChanged(int height) {
        if (height > 0) {
            separatedPhaseBars.updateWidth();
            phaseBarsItems.forEach(item -> {
                int firstBlock = daoFacade.getFirstBlockOfPhase(height, item.getPhase());
                int lastBlock = daoFacade.getLastBlockOfPhase(height, item.getPhase());
                final int duration = daoFacade.getDurationForPhase(item.getPhase());
                item.setPeriodRange(firstBlock, lastBlock, duration);
                double progress = 0;
                if (height >= firstBlock && height <= lastBlock) {
                    progress = (double) (height - firstBlock + 1) / (double) duration;
                } else if (height < firstBlock) {
                    progress = 0;
                } else if (height > lastBlock) {
                    progress = 1;
                }
                item.getProgressProperty().set(progress);
            });
        }

        currentBlockHeightTextField.setText(String.valueOf(daoFacade.getChainHeight()));
        currentPhaseTextField.setText(Res.get("dao.phase." + daoFacade.phaseProperty().get().name()));
        proposalTextField.setText(getPhaseDuration(height, DaoPhase.Phase.PROPOSAL));
        blindVoteTextField.setText(getPhaseDuration(height, DaoPhase.Phase.BLIND_VOTE));
        voteRevealTextField.setText(getPhaseDuration(height, DaoPhase.Phase.VOTE_REVEAL));
        voteResultTextField.setText(getPhaseDuration(height, DaoPhase.Phase.RESULT));
    }

    private SeparatedPhaseBars createSeparatedPhaseBars() {
        phaseBarsItems = Arrays.asList(
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPhase.Phase.PROPOSAL, true),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPhase.Phase.BREAK1, false),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPhase.Phase.BLIND_VOTE, true),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPhase.Phase.BREAK2, false),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPhase.Phase.VOTE_REVEAL, true),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPhase.Phase.BREAK3, false),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPhase.Phase.RESULT, false),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPhase.Phase.BREAK4, false));
        return new SeparatedPhaseBars(phaseBarsItems);
    }

    private String getPhaseDuration(int height, DaoPhase.Phase phase) {
        final long start = daoFacade.getFirstBlockOfPhase(height, phase);
        final long end = daoFacade.getLastBlockOfPhase(height, phase);
        long now = new Date().getTime();
        String startDateTime = formatter.formatDateTime(new Date(now + (start - height) * 10 * 60 * 1000L));
        String endDateTime = formatter.formatDateTime(new Date(now + (end - height) * 10 * 60 * 1000L));
        return Res.get("dao.cycle.phaseDuration", start, end, startDateTime, endDateTime);
    }
}
