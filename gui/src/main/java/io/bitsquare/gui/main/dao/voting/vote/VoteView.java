/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.dao.voting.vote;

import com.google.common.util.concurrent.FutureCallback;
import io.bitsquare.btc.InsufficientFundsException;
import io.bitsquare.btc.exceptions.TransactionVerificationException;
import io.bitsquare.btc.exceptions.WalletException;
import io.bitsquare.btc.provider.fee.FeeService;
import io.bitsquare.btc.wallet.BsqWalletService;
import io.bitsquare.btc.wallet.BtcWalletService;
import io.bitsquare.btc.wallet.ChangeBelowDustException;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.MathUtils;
import io.bitsquare.dao.compensation.CompensationRequest;
import io.bitsquare.dao.compensation.CompensationRequestManager;
import io.bitsquare.dao.vote.*;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.TitledGroupBg;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.BsqFormatter;
import io.bitsquare.gui.util.Layout;
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
import static io.bitsquare.gui.util.FormBuilder.*;
import static javafx.beans.binding.Bindings.createBooleanBinding;

@FxmlView
public class VoteView extends ActivatableView<GridPane, Void> {

    private ComboBox<VoteItem> parametersComboBox;
    private ComboBox<CompensationRequestVoteItem> compensationRequestsComboBox;

    private int gridRow = 0;
    private CompensationRequestManager compensationRequestManager;
    private BsqWalletService bsqWalletService;
    private BtcWalletService btcWalletService;
    private FeeService feeService;
    private BsqFormatter bsqFormatter;
    private BSFormatter btcFormatter;
    private VotingManager voteManager;
    private Button voteButton;
    private List<CompensationRequest> compensationRequests;
    private TitledGroupBg compensationRequestsTitledGroupBg, parametersTitledGroupBg;
    private VoteItemsList voteItemsList;
    private CompensationRequestVoteItemCollection compensationRequestVoteItemCollection;
    private VBox parametersVBox, compensationRequestsVBox;
    private DoubleProperty parametersLabelWidth = new SimpleDoubleProperty();
    private DoubleProperty compensationRequestsLabelWidth = new SimpleDoubleProperty();
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
        this.bsqFormatter = bsqFormatter;
        this.btcFormatter = btcFormatter;
        this.voteManager = voteManager;
    }

    @Override
    public void initialize() {
        addTitledGroupBg(root, gridRow, 2, "Add items for voting");

        compensationRequestsComboBox = addLabelComboBox(root, gridRow, "", Layout.FIRST_ROW_DISTANCE).second;
        compensationRequestsComboBox.setPromptText("Add compensation request");
        compensationRequestsComboBox.setConverter(new StringConverter<CompensationRequestVoteItem>() {
            @Override
            public String toString(CompensationRequestVoteItem item) {
                return item.compensationRequest.getCompensationRequestPayload().uid;
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
                    new Popup<>().warning("You have already that compensation request added.").show();
                }
            }

            compensationRequestsTitledGroupBg.setManaged(!CompensationViewItem.isEmpty());
        });

        parametersComboBox = addLabelComboBox(root, ++gridRow, "").second;
        parametersComboBox.setPromptText("Add parameter");
        parametersComboBox.setConverter(new StringConverter<VoteItem>() {
            @Override
            public String toString(VoteItem item) {
                return item.name;
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
                    new Popup<>().warning("You have already that parameter added.").show();
                }
            }
            parametersTitledGroupBg.setManaged(!ParameterViewItem.isEmpty());

        });

        compensationRequestsTitledGroupBg = addTitledGroupBg(root, ++gridRow, 1, "Compensation requests", Layout.GROUP_DISTANCE);
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


        parametersTitledGroupBg = addTitledGroupBg(root, ++gridRow, 1, "Parameters", Layout.GROUP_DISTANCE);
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

        voteButton = addButtonAfterGroup(root, ++gridRow, "Vote");
        voteButton.managedProperty().bind(createBooleanBinding(() -> compensationRequestsTitledGroupBg.isManaged() || parametersTitledGroupBg.isManaged(),
                compensationRequestsTitledGroupBg.managedProperty(), parametersTitledGroupBg.managedProperty()));
        voteButton.visibleProperty().bind(voteButton.managedProperty());

        voteButton.setOnAction(event -> {
            log.error(voteItemsList.toString());
            //TODO
            if (voteItemsList.isMyVote()) {
                new Popup<>().warning("You voted already.").show();
            } else if (!voteItemsList.stream().filter(VoteItem::hasVoted).findAny().isPresent() &&
                    !voteItemsList.stream().filter(e -> e instanceof CompensationRequestVoteItemCollection)
                            .filter(e -> ((CompensationRequestVoteItemCollection) e).hasVotedOnAnyItem()).findAny().isPresent()) {
                new Popup<>().warning("You did not vote on any entry.").show();
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
                        new Popup().headLine("Confirm voting fee payment transaction")
                                .confirmation("Voting fee: " + btcFormatter.formatCoinWithCode(votingTxFee) + "\n" +
                                        "Mining fee: " + btcFormatter.formatCoinWithCode(miningFee) + " (" +
                                        MathUtils.roundDouble(((double) miningFee.value / (double) txSize), 2) +
                                        " Satoshis/byte)\n" +
                                        "Transaction size: " + (txSize / 1000d) + " Kb\n\n" +
                                        "Are you sure you want to send the transaction?")
                                .actionButtonText("Yes")
                                .onAction(() -> {
                                    try {
                                        bsqWalletService.commitTx(txWithBtcFee);
                                        // We need to create another instance, otherwise the tx would trigger an invalid state exception 
                                        // if it gets committed 2 times 
                                        btcWalletService.commitTx(btcWalletService.getClonedTransaction(txWithBtcFee));
                                        bsqWalletService.broadcastTx(signedTx, new FutureCallback<Transaction>() {
                                            @Override
                                            public void onSuccess(@Nullable Transaction transaction) {
                                                checkNotNull(transaction, "Transaction must not be null at doSend callback.");
                                                log.error("tx successful published" + transaction.getHashAsString());
                                                new Popup<>().confirmation("Your transaction has been successfully published.").show();
                                                voteItemsList.setIsMyVote(true);

                                                //TODO send to P2P network
                                            }

                                            @Override
                                            public void onFailure(@NotNull Throwable t) {
                                                new Popup<>().warning(t.toString()).show();
                                            }
                                        });
                                    } catch (WalletException | TransactionVerificationException e) {
                                        log.error(e.toString());
                                        e.printStackTrace();
                                        new Popup<>().warning(e.toString());
                                    }
                                })
                                .closeButtonText("Cancel")
                                .show();
                    } catch (InsufficientMoneyException | WalletException | TransactionVerificationException |
                            ChangeBelowDustException | InsufficientFundsException e) {
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
        if (voteItemsList != null) {
            compensationRequestVoteItemCollection = voteItemsList.getCompensationRequestVoteItemCollection();
            ObservableList<CompensationRequestVoteItem> compensationRequestVoteItems = FXCollections.observableArrayList(compensationRequestVoteItemCollection.getCompensationRequestVoteItems());
            compensationRequestsComboBox.setItems(compensationRequestVoteItems);

            //TODO move to voteManager.getCurrentVoteItemsList()?
            compensationRequestManager.getObservableCompensationRequestsList().stream().forEach(e -> compensationRequestVoteItems.add(new CompensationRequestVoteItem(e)));

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

