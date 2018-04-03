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

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.vote.PeriodService;
import bisq.core.locale.Res;

import javax.inject.Inject;

import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;

import javafx.geometry.Insets;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.value.ChangeListener;

import java.util.Arrays;
import java.util.List;

import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class ProposalDashboardView extends ActivatableView<GridPane, Void> {

    private List<SeparatedPhaseBars.SeparatedPhaseBarsItem> phaseBarsItems;
    private final BsqWalletService bsqWalletService;
    private final PeriodService periodService;
    private PeriodService.Phase currentPhase;
    private Subscription phaseSubscription;
    private GridPane gridPane;
    private int gridRow = 0;
    private ChangeListener<Number> chainHeightChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ProposalDashboardView(PeriodService periodService,
                                  BsqWalletService bsqWalletService) {
        this.periodService = periodService;
        this.bsqWalletService = bsqWalletService;
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
        SeparatedPhaseBars separatedPhaseBars = createSeparatedPhaseBars();
        GridPane.setColumnSpan(separatedPhaseBars, 2);
        GridPane.setColumnIndex(separatedPhaseBars, 0);
        GridPane.setMargin(separatedPhaseBars, new Insets(Layout.FIRST_ROW_DISTANCE - 6, 0, 0, 0));
        GridPane.setRowIndex(separatedPhaseBars, gridRow);
        gridPane.getChildren().add(separatedPhaseBars);

        chainHeightChangeListener = (observable, oldValue, newValue) -> onChainHeightChanged((int) newValue);
    }


    private SeparatedPhaseBars createSeparatedPhaseBars() {
        phaseBarsItems = Arrays.asList(
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(PeriodService.Phase.PROPOSAL, true),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(PeriodService.Phase.BREAK1, false),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(PeriodService.Phase.BLIND_VOTE, true),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(PeriodService.Phase.BREAK2, false),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(PeriodService.Phase.VOTE_REVEAL, true),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(PeriodService.Phase.BREAK3, false),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(PeriodService.Phase.ISSUANCE, false),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(PeriodService.Phase.BREAK4, false));
        return new SeparatedPhaseBars(phaseBarsItems);
    }

    @Override
    protected void activate() {
        super.activate();

        bsqWalletService.getChainHeightProperty().addListener(chainHeightChangeListener);

        phaseSubscription = EasyBind.subscribe(periodService.getPhaseProperty(), phase -> {
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

        onChainHeightChanged(bsqWalletService.getChainHeightProperty().get());
    }

    @Override
    protected void deactivate() {
        super.deactivate();

        bsqWalletService.getChainHeightProperty().removeListener(chainHeightChangeListener);
        phaseSubscription.unsubscribe();
    }


    private void onChainHeightChanged(int height) {
        phaseBarsItems.forEach(item -> {
            int startBlock = periodService.getAbsoluteStartBlockOfPhase(height, item.getPhase());
            int endBlock = periodService.getAbsoluteEndBlockOfPhase(height, item.getPhase());
            item.setStartAndEnd(startBlock, endBlock);
            double progress = 0;
            if (height >= startBlock && height <= endBlock) {
                progress = (double) (height - startBlock + 1) / (double) item.getPhase().getDurationInBlocks();
            } else if (height < startBlock) {
                progress = 0;
            } else if (height > endBlock) {
                progress = 1;
            }
            item.getProgressProperty().set(progress);
        });
    }
}
