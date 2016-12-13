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

import io.bitsquare.common.util.Tuple2;
import io.bitsquare.dao.proposals.Proposal;
import io.bitsquare.dao.proposals.ProposalManager;
import io.bitsquare.dao.vote.*;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.components.TitledGroupBg;
import io.bitsquare.gui.util.Layout;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

import static io.bitsquare.gui.util.FormBuilder.*;

@FxmlView
public class VoteView extends ActivatableView<GridPane, Void> {


    private int gridRow = 0;
    private ProposalManager proposalManager;
    private VoteManager voteManager;
    private VoteItemCollection voteCollection;
    private Button voteButton;
    private List<Proposal> proposals;
    private TitledGroupBg titledGroupBg;
    private List<VoteItem> voteItems;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private VoteView(ProposalManager proposalManager, VoteManager voteManager, VoteItemCollection voteCollection) {
        this.proposalManager = proposalManager;
        this.voteManager = voteManager;
        this.voteCollection = voteCollection;
    }

    @Override
    public void initialize() {
        voteItems = voteCollection.getVoteItems();
        // proposals = proposalManager.getObservableProposalsList().stream().filter(Proposal::isInVotePeriod).collect(Collectors.toList());


    }

    @Override
    protected void activate() {
        root.getChildren().clear();
        // TODO
        proposals = proposalManager.getObservableProposalsList().stream().filter(Proposal::isInVotePeriod).collect(Collectors.toList());
        titledGroupBg = addTitledGroupBg(root, gridRow, voteItems.size() + proposals.size() - 1, "Voting");
        // GridPane.setRowSpan(titledGroupBg, voteItems.size() + proposals.size());
        voteItems.stream().forEach(this::addVoteItem);

        voteButton = addButtonAfterGroup(root, ++gridRow, "Vote");
        voteButton.setOnAction(event -> {
            voteManager.vote();
        });
    }

    @Override
    protected void deactivate() {
    }

    private void addVoteItem(VoteItem voteItem) {
        if (voteItem instanceof ProposalVoteItemCollection) {
            addProposals((ProposalVoteItemCollection) voteItem);
        } else {
            Tuple2<Label, InputTextField> tuple;
            if (voteItem == voteItems.get(0))
                tuple = addLabelInputTextField(root, gridRow, voteItem.name + " (" + voteItem.code + "):", Layout.FIRST_ROW_DISTANCE);
            else
                tuple = addLabelInputTextField(root, ++gridRow, voteItem.name + " (" + voteItem.code + "):");

            InputTextField inputTextField = tuple.second;
            inputTextField.setText(String.valueOf(voteItem.getValue()));
            inputTextField.textProperty().addListener((observable, oldValue, newValue) -> voteItem.setValue(Integer.valueOf(newValue)));
        }
    }

    private void addProposals(ProposalVoteItemCollection proposalVoteItemCollection) {
        proposals.forEach(proposal -> addProposalItem(proposal, proposalVoteItemCollection));
    }

    private void addProposalItem(Proposal proposal, ProposalVoteItemCollection proposalVoteItemCollection) {
        ProposalVoteItem proposalVoteItem = new ProposalVoteItem(proposal);
        proposalVoteItemCollection.addProposalVoteItem(proposalVoteItem);

        addLabel(root, ++gridRow, "Proposal ID:", 0);

        TextField textField = new TextField("ID: " + proposal.getProposalPayload().getShortId());
        textField.setEditable(false);
        textField.setMouseTransparent(true);
        textField.setFocusTraversable(false);
        textField.setMaxWidth(120);

        Button openButton = new Button("Open Proposal");
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
            proposalVoteItem.setAcceptedVote(acceptCheckBox.isSelected());
            if (declineCheckBox.isSelected())
                declineCheckBox.setSelected(!acceptCheckBox.isSelected());

        });
        acceptCheckBox.setSelected(proposalVoteItem.isAcceptedVote());

        declineCheckBox.setOnAction(event -> {
            proposalVoteItem.setDeclineVote(declineCheckBox.isSelected());
            if (acceptCheckBox.isSelected())
                acceptCheckBox.setSelected(!declineCheckBox.isSelected());

        });
        declineCheckBox.setSelected(proposalVoteItem.isDeclineVote());
    }
}

