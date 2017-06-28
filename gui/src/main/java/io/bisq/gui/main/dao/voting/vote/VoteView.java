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

package io.bisq.gui.main.dao.voting.vote;

import com.google.common.util.concurrent.FutureCallback;
import io.bisq.common.UserThread;
import io.bisq.common.locale.Res;
import io.bisq.core.btc.exceptions.TransactionVerificationException;
import io.bisq.core.btc.exceptions.WalletException;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.ChangeBelowDustException;
import io.bisq.core.dao.compensation.CompensationRequest;
import io.bisq.core.dao.compensation.CompensationRequestManager;
import io.bisq.core.dao.vote.*;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.core.util.CoinUtil;
import io.bisq.gui.common.view.ActivatableView;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.components.TitledGroupBg;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.BsqFormatter;
import io.bisq.gui.util.Layout;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bisq.gui.util.FormBuilder.*;
import static javafx.beans.binding.Bindings.createBooleanBinding;

@FxmlView
public class VoteView extends ActivatableView<GridPane, Void> {

    private ComboBox<VoteItem> parametersComboBox;
    private ComboBox<CompensationRequestVoteItem> compensationRequestsComboBox;

    private int gridRow = 0;
    private final CompensationRequestManager compensationRequestManager;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final FeeService feeService;
    private final BSFormatter btcFormatter;
    private final VotingManager voteManager;
    private Button voteButton;
    private List<CompensationRequest> compensationRequests;
    private TitledGroupBg compensationRequestsTitledGroupBg, parametersTitledGroupBg;
    private VoteItemsList voteItemsList;
    private VBox parametersVBox, compensationRequestsVBox;
    private final DoubleProperty parametersLabelWidth = new SimpleDoubleProperty();
    private final DoubleProperty compensationRequestsLabelWidth = new SimpleDoubleProperty();
    private ChangeListener<Number> numberChangeListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private VoteView(CompensationRequestManager compensationRequestManager, BsqWalletService bsqWalletService,
                     BtcWalletService btcWalletService, FeeService feeService, BsqFormatter bsqFormatter,
                     BSFormatter btcFormatter, VotingManager voteManager) {
        this.compensationRequestManager = compensationRequestManager;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.feeService = feeService;
        this.btcFormatter = btcFormatter;
        this.voteManager = voteManager;
    }

    @Override
    public void initialize() {
        addTitledGroupBg(root, gridRow, 2, Res.get("dao.voting.addItems"));

        //noinspection unchecked
        compensationRequestsComboBox = addLabelComboBox(root, gridRow, "", Layout.FIRST_ROW_DISTANCE).second;
        compensationRequestsComboBox.setPromptText(Res.get("dao.voting.addRequest"));
        compensationRequestsComboBox.setConverter(new StringConverter<CompensationRequestVoteItem>() {
            @Override
            public String toString(CompensationRequestVoteItem item) {
                return item.compensationRequest.getCompensationRequestPayload().getUid();
            }

            @Override
            public CompensationRequestVoteItem fromString(String s) {
                return null;
            }
        });
        compensationRequestsComboBox.setOnAction(event -> {
            SingleSelectionModel<CompensationRequestVoteItem> selectionModel = compensationRequestsComboBox.getSelectionModel();
            CompensationRequestVoteItem selectedItem = selectionModel.getSelectedItem();
            if (selectedItem != null) {
                if (!CompensationViewItem.contains(selectedItem)) {
                    CompensationViewItem.attach(selectedItem, compensationRequestsVBox, compensationRequestsLabelWidth,
                            () -> compensationRequestsTitledGroupBg.setManaged(!CompensationViewItem.isEmpty()));
                    UserThread.execute(selectionModel::clearSelection);
                } else {
                    new Popup<>().warning(Res.get("dao.voting.requestAlreadyAdded")).show();
                }
            }

            compensationRequestsTitledGroupBg.setManaged(!CompensationViewItem.isEmpty());
        });

        //noinspection unchecked
        parametersComboBox = addLabelComboBox(root, ++gridRow, "").second;
        parametersComboBox.setPromptText(Res.get("dao.voting.addParameter"));
        parametersComboBox.setConverter(new StringConverter<VoteItem>() {
            @Override
            public String toString(VoteItem item) {
                return item.getName();
            }

            @Override
            public VoteItem fromString(String s) {
                return null;
            }
        });
        parametersComboBox.setOnAction(event -> {
            SingleSelectionModel<VoteItem> selectionModel = parametersComboBox.getSelectionModel();
            VoteItem selectedItem = selectionModel.getSelectedItem();
            if (selectedItem != null) {
                if (!ParameterViewItem.contains(selectedItem)) {
                    ParameterViewItem.attach(selectedItem, parametersVBox, parametersLabelWidth, voteManager.getVotingDefaultValues(),
                            () -> parametersTitledGroupBg.setManaged(!ParameterViewItem.isEmpty()));
                    UserThread.execute(selectionModel::clearSelection);
                } else {
                    new Popup<>().warning(Res.get("dao.voting.parameterAlreadyAdded")).show();
                }
            }
            parametersTitledGroupBg.setManaged(!ParameterViewItem.isEmpty());

        });

        compensationRequestsTitledGroupBg = addTitledGroupBg(root, ++gridRow, 1, Res.get("dao.voting.compensationRequests"), Layout.GROUP_DISTANCE);
        compensationRequestsTitledGroupBg.setManaged(false);
        compensationRequestsTitledGroupBg.visibleProperty().bind(compensationRequestsTitledGroupBg.managedProperty());

        compensationRequestsVBox = new VBox();
        compensationRequestsVBox.setSpacing(5);
        GridPane.setRowIndex(compensationRequestsVBox, gridRow);
        GridPane.setColumnSpan(compensationRequestsVBox, 2);
        GridPane.setMargin(compensationRequestsVBox, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, 0, 0, 0));
        root.getChildren().add(compensationRequestsVBox);
        compensationRequestsVBox.managedProperty().bind(compensationRequestsTitledGroupBg.managedProperty());
        compensationRequestsVBox.visibleProperty().bind(compensationRequestsVBox.managedProperty());


        parametersTitledGroupBg = addTitledGroupBg(root, ++gridRow, 1, Res.get("shared.parameters"), Layout.GROUP_DISTANCE);
        parametersTitledGroupBg.setManaged(false);
        parametersTitledGroupBg.visibleProperty().bind(parametersTitledGroupBg.managedProperty());

        parametersVBox = new VBox();
        parametersVBox.setSpacing(5);
        GridPane.setRowIndex(parametersVBox, gridRow);
        GridPane.setColumnSpan(parametersVBox, 2);
        GridPane.setMargin(parametersVBox, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, 0, 0, 0));
        root.getChildren().add(parametersVBox);
        parametersVBox.managedProperty().bind(parametersTitledGroupBg.managedProperty());
        parametersVBox.visibleProperty().bind(parametersVBox.managedProperty());

        voteButton = addButtonAfterGroup(root, ++gridRow, Res.get("shared.vote"));
        voteButton.managedProperty().bind(createBooleanBinding(() -> compensationRequestsTitledGroupBg.isManaged() || parametersTitledGroupBg.isManaged(),
                compensationRequestsTitledGroupBg.managedProperty(), parametersTitledGroupBg.managedProperty()));
        voteButton.visibleProperty().bind(voteButton.managedProperty());

        voteButton.setOnAction(event -> {
            log.error(voteItemsList.toString());
            //TODO
            if (voteItemsList.isMyVote()) {
                new Popup<>().warning(Res.get("dao.voting.votedAlready")).show();
            } else if (!voteItemsList.getAllVoteItemList().stream().filter(VoteItem::isHasVoted).findAny().isPresent() &&
                    !voteItemsList.getAllVoteItemList().stream().filter(e -> e instanceof CompensationRequestVoteItemCollection)
                            .filter(e -> ((CompensationRequestVoteItemCollection) e).hasVotedOnAnyItem()).findAny().isPresent()) {
                new Popup<>().warning(Res.get("dao.voting.notVotedOnAnyEntry")).show();
            } else {
                try {
                    byte[] opReturnData = voteManager.calculateOpReturnData(voteItemsList);
                    try {
                        Coin votingTxFee = feeService.getVotingTxFee();
                        Transaction preparedVotingTx = bsqWalletService.getPreparedBurnFeeTx(votingTxFee);
                        Transaction txWithBtcFee = btcWalletService.completePreparedBsqTx(preparedVotingTx, false, opReturnData);
                        Transaction signedTx = bsqWalletService.signTx(txWithBtcFee);
                        Coin miningFee = signedTx.getFee();
                        int txSize = signedTx.bitcoinSerialize().length;
                        new Popup<>().headLine(Res.get("dao.voting.confirmTx"))
                                .confirmation(Res.get("dao.tx.summary",
                                        btcFormatter.formatCoinWithCode(votingTxFee),
                                        btcFormatter.formatCoinWithCode(miningFee),
                                        CoinUtil.getFeePerByte(miningFee, txSize),
                                        (txSize / 1000d)))
                                .actionButtonText(Res.get("shared.yes"))
                                .onAction(() -> {
                                    bsqWalletService.commitTx(txWithBtcFee);
                                    // We need to create another instance, otherwise the tx would trigger an invalid state exception
                                    // if it gets committed 2 times
                                    btcWalletService.commitTx(btcWalletService.getClonedTransaction(txWithBtcFee));
                                    bsqWalletService.broadcastTx(signedTx, new FutureCallback<Transaction>() {
                                        @Override
                                        public void onSuccess(@Nullable Transaction transaction) {
                                            checkNotNull(transaction, "Transaction must not be null at doSend callback.");
                                            log.error("tx successful published" + transaction.getHashAsString());
                                            new Popup<>().confirmation(Res.get("dao.tx.published.success")).show();
                                            voteItemsList.setIsMyVote(true);

                                            //TODO send to P2P network
                                        }

                                        @Override
                                        public void onFailure(@NotNull Throwable t) {
                                            new Popup<>().warning(t.toString()).show();
                                        }
                                    });
                                })
                                .closeButtonText(Res.get("shared.cancel"))
                                .show();
                    } catch (InsufficientMoneyException | WalletException | TransactionVerificationException |
                            ChangeBelowDustException e) {
                        log.error(e.toString());
                        e.printStackTrace();
                        new Popup<>().warning(e.toString()).show();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    new Popup<>().error(e.toString()).show();
                }
            }
        });
    }

    @Override
    protected void activate() {
        //TODO rename
        voteItemsList = voteManager.getActiveVoteItemsList();
        //noinspection StatementWithEmptyBody
        if (voteItemsList != null) {
            CompensationRequestVoteItemCollection compensationRequestVoteItemCollection = voteItemsList.getCompensationRequestVoteItemCollection();
            ObservableList<CompensationRequestVoteItem> compensationRequestVoteItems = FXCollections.observableArrayList(compensationRequestVoteItemCollection.getCompensationRequestVoteItems());
            compensationRequestsComboBox.setItems(compensationRequestVoteItems);

            //TODO move to voteManager.getCurrentVoteItemsList()?
            compensationRequestManager.getObservableList().stream().forEach(e -> compensationRequestVoteItems.add(new CompensationRequestVoteItem(e)));

            parametersComboBox.setItems(FXCollections.observableArrayList(voteItemsList.getVoteItemList()));
        } else {
            //TODO add listener
        }
    }

    @Override
    protected void deactivate() {
        compensationRequestsComboBox.setOnAction(null);
        parametersComboBox.setOnAction(null);
        voteButton.setOnAction(null);
        ParameterViewItem.cleanupAllInstances();
        CompensationViewItem.cleanupAllInstances();
    }
}

