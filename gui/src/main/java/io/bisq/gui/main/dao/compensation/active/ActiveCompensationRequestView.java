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

package io.bisq.gui.main.dao.compensation.active;

import io.bisq.common.locale.Res;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.dao.DaoPeriodService;
import io.bisq.core.dao.blockchain.BsqBlockChainChangeDispatcher;
import io.bisq.core.dao.blockchain.BsqBlockChainListener;
import io.bisq.core.dao.blockchain.parse.BsqBlockChain;
import io.bisq.core.dao.compensation.CompensationRequest;
import io.bisq.core.dao.compensation.CompensationRequestManager;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.gui.Navigation;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.components.SeparatedPhaseBars;
import io.bisq.gui.main.MainView;
import io.bisq.gui.main.dao.DaoView;
import io.bisq.gui.main.dao.compensation.CompensationRequestDisplay;
import io.bisq.gui.main.dao.compensation.CompensationRequestListItem;
import io.bisq.gui.main.dao.compensation.CompensationRequestView;
import io.bisq.gui.main.dao.voting.VotingView;
import io.bisq.gui.main.dao.voting.vote.VoteView;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.util.BsqFormatter;
import io.bisq.gui.util.Layout;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

import static io.bisq.gui.util.FormBuilder.addButtonAfterGroup;
import static io.bisq.gui.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class ActiveCompensationRequestView extends CompensationRequestView implements BsqBlockChainListener {

    private List<SeparatedPhaseBars.SeparatedPhaseBarsItem> phaseBarsItems;
    private Button removeButton, voteButton;
    private final Navigation navigation;
    private final DaoPeriodService daoPeriodService;
    private DaoPeriodService.Phase currentPhase;
    private ChangeListener<DaoPeriodService.Phase> phaseChangeListener;
    private Subscription phaseSubscription;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ActiveCompensationRequestView(CompensationRequestManager compensationRequestManger,
                                          DaoPeriodService daoPeriodService,
                                          BsqWalletService bsqWalletService,
                                          BsqBlockChain bsqBlockChain,
                                          FeeService feeService,
                                          BsqBlockChainChangeDispatcher bsqBlockChainChangeDispatcher,
                                          Navigation navigation,
                                          BsqFormatter bsqFormatter) {
        super(compensationRequestManger, bsqWalletService, bsqBlockChain, bsqBlockChainChangeDispatcher, bsqFormatter);
        this.daoPeriodService = daoPeriodService;
        this.navigation = navigation;
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
        addTitledGroupBg(gridPane, gridRow, 1, Res.get("dao.compensation.active.phase.header"));
        SeparatedPhaseBars separatedPhaseBars = createSeparatedPhaseBars();
        GridPane.setColumnSpan(separatedPhaseBars, 2);
        GridPane.setColumnIndex(separatedPhaseBars, 0);
        GridPane.setMargin(separatedPhaseBars, new Insets(Layout.FIRST_ROW_DISTANCE - 6, 0, 0, 0));
        GridPane.setRowIndex(separatedPhaseBars, gridRow);
        gridPane.getChildren().add(separatedPhaseBars);

     /*   final Tuple2<Label, TextField> tuple2 = addLabelTextField(gridPane, ++gridRow, Res.get("dao.compensation.active.cycle"));
        final Label label = tuple2.first;
        GridPane.setHalignment(label, HPos.RIGHT);
        cycleTextField = tuple2.second;*/

        // Add compensationrequest pane
        tableView = new TableView<>();
        detailsGridPane = new GridPane();
        compensationRequestDisplay = new CompensationRequestDisplay(detailsGridPane, bsqFormatter, bsqWalletService, null);
        compensationRequestPane = compensationRequestDisplay.createCompensationRequestPane(tableView, Res.get("dao.compensation.active.header"));
        GridPane.setColumnSpan(compensationRequestPane, 2);
        GridPane.setMargin(compensationRequestPane, new Insets(Layout.FIRST_ROW_DISTANCE - 6, -10, 0, -10));
        GridPane.setRowIndex(compensationRequestPane, ++gridRow);
        gridPane.getChildren().add(compensationRequestPane);

        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);

        chainHeightChangeListener = (observable, oldValue, newValue) -> {
            onChainHeightChanged((int) newValue);
        };

        compensationRequestListChangeListener = c -> updateList();
        phaseChangeListener = (observable, oldValue, newValue) -> onPhaseChanged(newValue);
    }


    private SeparatedPhaseBars createSeparatedPhaseBars() {
        phaseBarsItems = Arrays.asList(
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPeriodService.Phase.COMPENSATION_REQUESTS, true),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPeriodService.Phase.BREAK1, false),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPeriodService.Phase.OPEN_FOR_VOTING, true),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPeriodService.Phase.BREAK2, false),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPeriodService.Phase.VOTE_CONFIRMATION, true),
                new SeparatedPhaseBars.SeparatedPhaseBarsItem(DaoPeriodService.Phase.BREAK3, false));
        SeparatedPhaseBars separatedPhaseBars = new SeparatedPhaseBars(phaseBarsItems);
        return separatedPhaseBars;
    }

    @Override
    protected void activate() {
        super.activate();
        phaseSubscription = EasyBind.subscribe(daoPeriodService.getPhaseProperty(), phase -> {
            if (!phase.equals(this.currentPhase)) {
                this.currentPhase = phase;
                onSelectCompensationRequest(selectedCompensationRequest);
            }
            phaseBarsItems.stream().forEach(item -> {
                if (item.getPhase() == phase) {
                    item.setActive();
                } else {
                    item.setInActive();
                }
            });
        });

        daoPeriodService.getPhaseProperty().addListener(phaseChangeListener);
        onChainHeightChanged(bsqWalletService.getChainHeightProperty().get());
    }

    @Override
    protected void deactivate() {
        super.deactivate();
        phaseSubscription.unsubscribe();
        daoPeriodService.getPhaseProperty().removeListener(phaseChangeListener);
    }

    @Override
    protected void updateList() {
        doUpdateList(compensationRequestManger.getActiveRequests());
    }

    private void onChainHeightChanged(int height) {
        phaseBarsItems.stream().forEach(item -> {
            int startBlock = daoPeriodService.getAbsoluteStartBlockOfPhase(height, item.getPhase());
            int endBlock = daoPeriodService.getAbsoluteEndBlockOfPhase(height, item.getPhase());
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

    protected void onSelectCompensationRequest(CompensationRequestListItem item) {
        super.onSelectCompensationRequest(item);
        if (item != null) {
            if (removeButton != null) {
                removeButton.setManaged(false);
                removeButton.setVisible(false);
                removeButton = null;
            }
            if (voteButton != null) {
                voteButton.setManaged(false);
                voteButton.setVisible(false);
                voteButton = null;
            }
            onPhaseChanged(daoPeriodService.getPhaseProperty().get());
        }
    }

    protected void onPhaseChanged(DaoPeriodService.Phase phase) {
        if (removeButton != null) {
            removeButton.setManaged(false);
            removeButton.setVisible(false);
            removeButton = null;
        }
        if (selectedCompensationRequest != null && compensationRequestDisplay != null) {
            final CompensationRequest compensationRequest = selectedCompensationRequest.getCompensationRequest();
            switch (phase) {
                case COMPENSATION_REQUESTS:
                    if (compensationRequestManger.isMine(compensationRequest)) {
                        if (removeButton == null) {
                            removeButton = addButtonAfterGroup(detailsGridPane, compensationRequestDisplay.incrementAndGetGridRow(), Res.get("dao.compensation.active.remove"));
                            removeButton.setOnAction(event -> {
                                if (compensationRequestManger.removeCompensationRequest(compensationRequest))
                                    compensationRequestDisplay.removeAllFields();
                                else
                                    new Popup<>().warning(Res.get("dao.compensation.active.remove.failed")).show();
                            });
                        } else {
                            removeButton.setManaged(true);
                            removeButton.setVisible(true);
                        }
                    }
                    break;
                case BREAK1:
                    break;
                case OPEN_FOR_VOTING:
                    if (voteButton == null) {
                        voteButton = addButtonAfterGroup(detailsGridPane, compensationRequestDisplay.incrementAndGetGridRow(), Res.get("dao.compensation.active.vote"));
                        voteButton.setOnAction(event -> {
                            //noinspection unchecked
                            navigation.navigateTo(MainView.class, DaoView.class, VotingView.class, VoteView.class);
                        });
                    } else {
                        voteButton.setManaged(true);
                        voteButton.setVisible(true);
                    }
                    break;
                case BREAK2:
                    break;
                case VOTE_CONFIRMATION:
                    //TODO
                    log.warn("VOTE_CONFIRMATION");
                    break;
                case BREAK3:
                    break;
                case UNDEFINED:
                default:
                    log.warn("Undefined phase: " + daoPeriodService.getPhaseProperty());
                    break;
            }
        }
    }
}

