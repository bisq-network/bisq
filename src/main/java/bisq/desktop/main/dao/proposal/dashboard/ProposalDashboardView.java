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

import bisq.core.dao.consensus.period.PeriodService;
import bisq.core.dao.consensus.period.PeriodStateChangeListener;
import bisq.core.dao.consensus.period.Phase;
import bisq.core.locale.Res;

import bisq.common.UserThread;

import javax.inject.Inject;

import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;

import javafx.geometry.Insets;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import java.util.Arrays;
import java.util.List;

import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

// We use here PeriodStateChangeListener because we are interested in period changes not in the result of a completed
// block. The event from the PeriodStateChangeListener is sent before parsing starts.
// The event from the StateService.Listener would notify after parsing a new block.
@FxmlView
public class ProposalDashboardView extends ActivatableView<GridPane, Void> implements PeriodStateChangeListener {

    private List<SeparatedPhaseBars.SeparatedPhaseBarsItem> phaseBarsItems;
    private final PeriodService PeriodService;
    private Phase currentPhase;
    private Subscription phaseSubscription;
    private GridPane gridPane;
    private int gridRow = 0;
    private SeparatedPhaseBars separatedPhaseBars;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ProposalDashboardView(PeriodService PeriodService) {
        this.PeriodService = PeriodService;
    }

    @Override
    public void initialize() {
        root.getStyleClass().add("compensation-root");
        AnchorPane topAnchorPane = new AnchorPane();
        root.getChildren().add(topAnchorPane);

        gridPane = new GridPane();
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        AnchorPane.setBottomAnchor(gridPane, 10d);
        AnchorPane.setRightAnchor(gridPane, 10d);
        AnchorPane.setLeftAnchor(gridPane, 10d);
        AnchorPane.setTopAnchor(gridPane, 10d);
        topAnchorPane.getChildren().add(gridPane);

        // Add phase info
        addTitledGroupBg(gridPane, gridRow, 1, Res.get("dao.proposal.active.phase.header"));
        separatedPhaseBars = createSeparatedPhaseBars();
        GridPane.setColumnSpan(separatedPhaseBars, 2);
        GridPane.setColumnIndex(separatedPhaseBars, 0);
        GridPane.setMargin(separatedPhaseBars, new Insets(Layout.FIRST_ROW_DISTANCE - 6, 0, 0, 0));
        GridPane.setRowIndex(separatedPhaseBars, gridRow);
        gridPane.getChildren().add(separatedPhaseBars);
    }


    private SeparatedPhaseBars createSeparatedPhaseBars() {
        phaseBarsItems = Arrays.asList(
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(Phase.PROPOSAL, true),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(Phase.BREAK1, false),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(Phase.BLIND_VOTE, true),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(Phase.BREAK2, false),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(Phase.VOTE_REVEAL, true),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(Phase.BREAK3, false),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(Phase.VOTE_RESULT, false),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(Phase.BREAK4, false));
        return new SeparatedPhaseBars(phaseBarsItems);
    }

    @Override
    protected void activate() {
        super.activate();

        phaseSubscription = EasyBind.subscribe(PeriodService.phaseProperty(), phase -> {
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
        PeriodService.addPeriodStateChangeListener(this);

        // We need to delay as otherwise the periodService has not been updated yet.
        UserThread.execute(() -> onChainHeightChanged(PeriodService.getChainHeight()));
    }

    @Override
    protected void deactivate() {
        super.deactivate();
        PeriodService.removePeriodStateChangeListener(this);
        phaseSubscription.unsubscribe();
    }

    @Override
    public void onChainHeightChanged(int height) {
        if (height > 0) {
            separatedPhaseBars.updateWidth();
            phaseBarsItems.forEach(item -> {
                int firstBlock = PeriodService.getFirstBlockOfPhase(height, item.getPhase());
                int lastBlock = PeriodService.getLastBlockOfPhase(height, item.getPhase());
                final int duration = PeriodService.getDurationForPhase(item.getPhase(), PeriodService.getChainHeight());
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
    }

}
