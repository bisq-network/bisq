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
import io.bitsquare.btc.wallet.BtcWalletService;
import io.bitsquare.btc.wallet.ChangeBelowDustException;
import io.bitsquare.btc.wallet.SquWalletService;
import io.bitsquare.common.util.MathUtils;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.dao.compensation.CompensationRequest;
import io.bitsquare.dao.compensation.CompensationRequestManager;
import io.bitsquare.dao.vote.*;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.components.TitledGroupBg;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.gui.util.SQUFormatter;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.gui.util.FormBuilder.*;

@FxmlView
public class VoteView extends ActivatableView<GridPane, Void> {

    private int gridRow = 0;
    private CompensationRequestManager compensationRequestManager;
    private SquWalletService squWalletService;
    private BtcWalletService btcWalletService;
    private FeeService feeService;
    private SQUFormatter squFormatter;
    private BSFormatter btcFormatter;
    private VoteManager voteManager;
    private Button voteButton;
    private List<CompensationRequest> compensationRequests;
    private TitledGroupBg titledGroupBg;
    private VoteItemCollection voteItemCollection;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private VoteView(CompensationRequestManager compensationRequestManager, SquWalletService squWalletService,
                     BtcWalletService btcWalletService, FeeService feeService, SQUFormatter squFormatter, BSFormatter btcFormatter, VoteManager voteManager) {
        this.compensationRequestManager = compensationRequestManager;
        this.squWalletService = squWalletService;
        this.btcWalletService = btcWalletService;
        this.feeService = feeService;
        this.squFormatter = squFormatter;
        this.btcFormatter = btcFormatter;
        this.voteManager = voteManager;
    }

    @Override
    public void initialize() {
        // TODO crate items here
    }

    @Override
    protected void activate() {
        voteItemCollection = voteManager.getCurrentVoteItemCollection();
        root.getChildren().clear();

        compensationRequests = compensationRequestManager.getObservableCompensationRequestsList().stream().filter(CompensationRequest::isInVotePeriod).collect(Collectors.toList());
        titledGroupBg = addTitledGroupBg(root, gridRow, voteItemCollection.size() + compensationRequests.size() - 1, "Voting");
        // GridPane.setRowSpan(titledGroupBg, voteItems.size() + CompensationRequest.size());
        voteItemCollection.stream().forEach(this::addVoteItem);

        voteButton = addButtonAfterGroup(root, ++gridRow, "Vote");
        voteButton.setOnAction(event -> {
            if (!voteItemCollection.isMyVote()) {
                byte[] hash = voteManager.calculateHash(voteItemCollection);
                if (hash.length > 0) {
                    try {
                        Coin votingTxFee = feeService.getVotingTxFee();
                        Transaction preparedVotingTx = squWalletService.getPreparedBurnFeeTx(votingTxFee);
                        Transaction txWithBtcFee = btcWalletService.completePreparedSquTx(preparedVotingTx, false, hash);
                        Transaction signedTx = squWalletService.signTx(txWithBtcFee);
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
                                        squWalletService.commitTx(txWithBtcFee);
                                        // We need to create another instance, otherwise the tx would trigger an invalid state exception 
                                        // if it gets committed 2 times 
                                        btcWalletService.commitTx(btcWalletService.getClonedTransaction(txWithBtcFee));
                                        squWalletService.broadcastTx(signedTx, new FutureCallback<Transaction>() {
                                            @Override
                                            public void onSuccess(@Nullable Transaction transaction) {
                                                checkNotNull(transaction, "Transaction must not be null at doSend callback.");
                                                log.error("tx successful published" + transaction.getHashAsString());
                                                new Popup<>().confirmation("Your transaction has been successfully published.").show();
                                                voteItemCollection.setIsMyVote(true);
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
                } else {
                    new Popup<>().warning("You did not vote on any entry.").show();
                }
            } else {
                //TODO
                new Popup<>().warning("You voted already.").show();
            }
        });
    }

    @Override
    protected void deactivate() {
    }

    private void addVoteItem(VoteItem voteItem) {
        if (voteItem instanceof CompensationRequestVoteItemCollection) {
            addCompensationRequests((CompensationRequestVoteItemCollection) voteItem);
        } else {
            Tuple2<Label, InputTextField> tuple;
            if (voteItem == voteItemCollection.get(0))
                tuple = addLabelInputTextField(root, gridRow, voteItem.name + " (" + voteItem.code + "):", Layout.FIRST_ROW_DISTANCE);
            else
                tuple = addLabelInputTextField(root, ++gridRow, voteItem.name + " (" + voteItem.code + "):");

            InputTextField inputTextField = tuple.second;
            inputTextField.setText(String.valueOf(voteItem.getValue()));
            inputTextField.textProperty().addListener((observable, oldValue, newValue) -> voteItem.setValue((byte) ((int) Integer.valueOf(newValue))));
        }
    }

    private void addCompensationRequests(CompensationRequestVoteItemCollection collection) {
        compensationRequests.forEach(request -> addCompensationRequestItem(request, collection));
    }

    private void addCompensationRequestItem(CompensationRequest compensationRequest, CompensationRequestVoteItemCollection collection) {
        CompensationRequestVoteItem compensationRequestVoteItem = new CompensationRequestVoteItem(compensationRequest);
        collection.addCompensationRequestVoteItem(compensationRequestVoteItem);

        addLabel(root, ++gridRow, "Compensation request ID:", 0);

        TextField textField = new TextField("ID: " + compensationRequest.getCompensationRequestPayload().getShortId());
        textField.setEditable(false);
        textField.setMouseTransparent(true);
        textField.setFocusTraversable(false);
        textField.setMaxWidth(120);

        Button openButton = new Button("Open compensation request");
        CheckBox acceptCheckBox = new CheckBox("Accept");
        CheckBox declineCheckBox = new CheckBox("Decline");

        HBox hBox = new HBox();
        HBox.setMargin(acceptCheckBox, new Insets(5, 0, 0, 0));
        HBox.setMargin(declineCheckBox, new Insets(5, 0, 0, 0));
        hBox.setSpacing(10);
        hBox.getChildren().addAll(textField, openButton, acceptCheckBox, declineCheckBox);
        HBox.setHgrow(textField, Priority.ALWAYS);
        GridPane.setRowIndex(hBox, gridRow);
        GridPane.setColumnIndex(hBox, 1);
        GridPane.setMargin(hBox, new Insets(0, 0, 0, 0));
        root.getChildren().add(hBox);

        openButton.setOnAction(event -> {
            // todo open popup
        });
        acceptCheckBox.setOnAction(event -> {
            compensationRequestVoteItem.setAcceptedVote(acceptCheckBox.isSelected());
            if (declineCheckBox.isSelected())
                declineCheckBox.setSelected(!acceptCheckBox.isSelected());

        });
        acceptCheckBox.setSelected(compensationRequestVoteItem.isAcceptedVote());

        declineCheckBox.setOnAction(event -> {
            compensationRequestVoteItem.setDeclineVote(declineCheckBox.isSelected());
            if (acceptCheckBox.isSelected())
                acceptCheckBox.setSelected(!declineCheckBox.isSelected());

        });
        declineCheckBox.setSelected(compensationRequestVoteItem.isDeclineVote());
    }
}

