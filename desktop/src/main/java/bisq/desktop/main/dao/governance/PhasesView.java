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

package bisq.desktop.main.dao.governance;

import bisq.desktop.components.SeparatedPhaseBars;
import bisq.desktop.util.Layout;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.locale.Res;

import javax.inject.Inject;

import javafx.scene.layout.GridPane;

import javafx.geometry.Insets;

import java.util.Arrays;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@Slf4j
public class PhasesView implements DaoStateListener {
    private final DaoFacade daoFacade;
    private final PeriodService periodService;
    private SeparatedPhaseBars separatedPhaseBars;
    private List<SeparatedPhaseBars.SeparatedPhaseBarsItem> phaseBarsItems;

    @Inject
    private PhasesView(DaoFacade daoFacade, PeriodService periodService) {
        this.daoFacade = daoFacade;
        this.periodService = periodService;
    }

    public int addGroup(GridPane gridPane, int gridRow) {
        addTitledGroupBg(gridPane, gridRow, 1, Res.get("dao.cycle.headline"));
        separatedPhaseBars = createSeparatedPhaseBars();
        GridPane.setMargin(separatedPhaseBars, new Insets(Layout.FIRST_ROW_DISTANCE + 5, 0, 0, 0));
        GridPane.setRowIndex(separatedPhaseBars, gridRow);
        gridPane.getChildren().add(separatedPhaseBars);
        return gridRow;
    }

    public void activate() {
        daoFacade.addBsqStateListener(this);

        applyData(daoFacade.getChainHeight());
    }

    public void deactivate() {
        daoFacade.removeBsqStateListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        applyData(block.getHeight());

        phaseBarsItems.forEach(item -> {
            DaoPhase.Phase phase = item.getPhase();
            // Last block is considered for the break as we must not publish a tx there (would get confirmed in next
            // block which would be a break). Only at result phase we don't have that situation ans show the last block
            // as valid block in the phase.
            if (periodService.isInPhaseButNotLastBlock(phase) ||
                    (phase == DaoPhase.Phase.RESULT && periodService.isInPhase(block.getHeight(), phase))) {
                item.setActive();
            } else {
                item.setInActive();
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private SeparatedPhaseBars createSeparatedPhaseBars() {
        phaseBarsItems = Arrays.asList(
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPhase.Phase.PROPOSAL, true),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPhase.Phase.BREAK1, false),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPhase.Phase.BLIND_VOTE, true),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPhase.Phase.BREAK2, false),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPhase.Phase.VOTE_REVEAL, true),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPhase.Phase.BREAK3, false),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPhase.Phase.RESULT, false));
        return new SeparatedPhaseBars(phaseBarsItems);
    }

    private void applyData(int height) {
        if (height > 0) {
            phaseBarsItems.forEach(item -> {
                int firstBlock = daoFacade.getFirstBlockOfPhaseForDisplay(height, item.getPhase());
                int lastBlock = daoFacade.getLastBlockOfPhaseForDisplay(height, item.getPhase());
                int duration = daoFacade.getDurationForPhaseForDisplay(item.getPhase());
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
            separatedPhaseBars.updateWidth();
        }
    }
}
