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

import bisq.core.dao.consensus.period.PeriodStateChangeListener;
import bisq.core.dao.consensus.period.Phase;
import bisq.core.dao.presentation.period.PeriodServiceFacade;
import bisq.core.dao.presentation.state.StateServiceFacade;
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

@FxmlView
public class ProposalDashboardView extends ActivatableView<GridPane, Void> implements PeriodStateChangeListener {

    private List<SeparatedPhaseBars.SeparatedPhaseBarsItem> phaseBarsItems;
    private final PeriodServiceFacade periodServiceFacade;
    private Phase currentPhase;
    private Subscription phaseSubscription;
    private GridPane gridPane;
    private int gridRow = 0;
    private SeparatedPhaseBars separatedPhaseBars;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ProposalDashboardView(PeriodServiceFacade periodServiceFacade, StateServiceFacade stateService) {
        this.periodServiceFacade = periodServiceFacade;
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
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(Phase.ISSUANCE, false),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(Phase.BREAK4, false));
        return new SeparatedPhaseBars(phaseBarsItems);
    }

    @Override
    protected void activate() {
        super.activate();

        phaseSubscription = EasyBind.subscribe(periodServiceFacade.phaseProperty(), phase -> {
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
        periodServiceFacade.addPeriodStateChangeListener(this);

        // We need to delay as otherwise the periodService has not been updated yet.
        UserThread.execute(() -> onChainHeightChanged(periodServiceFacade.getChainHeight()));
    }

    @Override
    protected void deactivate() {
        super.deactivate();
        periodServiceFacade.removePeriodStateChangeListener(this);
        phaseSubscription.unsubscribe();
    }

    @Override
    public void onChainHeightChanged(int height) {
        if (height > 0) {
            separatedPhaseBars.updateWidth();
            phaseBarsItems.forEach(item -> {
                int firstBlock = periodServiceFacade.getFirstBlockOfPhase(height, item.getPhase());
                int lastBlock = periodServiceFacade.getLastBlockOfPhase(height, item.getPhase());
                final int duration = periodServiceFacade.getDurationForPhase(item.getPhase(), periodServiceFacade.getChainHeight());
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
