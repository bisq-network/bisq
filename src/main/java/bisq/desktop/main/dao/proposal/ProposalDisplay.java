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

package bisq.desktop.main.dao.proposal;

import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TxIdTextField;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.BsqAddressValidator;
import bisq.desktop.util.validation.BsqValidator;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.ext.Param;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.dao.voting.proposal.ProposalConsensus;
import bisq.core.dao.voting.proposal.ProposalType;
import bisq.core.dao.voting.proposal.burnbond.BurnBondProposal;
import bisq.core.dao.voting.proposal.compensation.CompensationConsensus;
import bisq.core.dao.voting.proposal.compensation.CompensationProposal;
import bisq.core.dao.voting.proposal.param.ChangeParamProposal;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.geometry.HPos;

import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import javafx.util.StringConverter;

import java.util.UUID;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.*;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class ProposalDisplay {
    private final GridPane gridPane;
    private final int maxLengthDescriptionText;
    private final BsqFormatter bsqFormatter;
    private final BsqWalletService bsqWalletService;
    private DaoFacade daoFacade;
    private InputTextField uidTextField;
    private TextField proposalFeeTextField;
    public InputTextField nameTextField;
    public InputTextField titleTextField;
    public InputTextField linkInputTextField;
    @Nullable
    public InputTextField requestedBsqTextField, bsqAddressTextField, paramValueTextField;
    @Nullable
    public ComboBox<Param> paramComboBox;
    public ComboBox<String> burnBondComboBox;
    @Getter
    private int gridRow;
    public TextArea descriptionTextArea;
    private HyperlinkWithIcon linkHyperlinkWithIcon;
    @Nullable
    private TxIdTextField txIdTextField;
    private final ChangeListener<String> descriptionTextAreaListener;
    private int gridRowStartIndex;


    // TODO get that warning at closing the window...
    // javafx.scene.CssStyleHelper calculateValue
    // WARNING: Could not resolve '-fx-accent' while resolving lookups for '-fx-text-fill' from rule '*.hyperlink' in stylesheet file:/Users/dev/idea/bisq/desktop/out/production/resources/bisq/desktop/bisq.css

    public ProposalDisplay(GridPane gridPane, BsqFormatter bsqFormatter, BsqWalletService bsqWalletService,
                           DaoFacade daoFacade) {
        this.gridPane = gridPane;
        this.bsqFormatter = bsqFormatter;
        this.bsqWalletService = bsqWalletService;
        this.daoFacade = daoFacade;

        maxLengthDescriptionText = ProposalConsensus.getMaxLengthDescriptionText();

        descriptionTextAreaListener = (observable, oldValue, newValue) -> {
            if (!ProposalConsensus.isDescriptionSizeValid(newValue)) {
                new Popup<>().warning(Res.get("dao.proposal.display.description.tooLong", maxLengthDescriptionText)).show();
                descriptionTextArea.setText(newValue.substring(0, maxLengthDescriptionText));
            }
        };
    }

    public void createAllFields(String title, int gridRowStartIndex, double top, ProposalType proposalType,
                                boolean isMakeProposalScreen, boolean showDetails) {
        removeAllFields();
        this.gridRowStartIndex = gridRowStartIndex;
        this.gridRow = gridRowStartIndex;
        int rowSpan;

        boolean hasAddedFields = proposalType == ProposalType.COMPENSATION_REQUEST ||
                proposalType == ProposalType.CHANGE_PARAM || proposalType == ProposalType.BURN_BOND;
        if (isMakeProposalScreen) {
            rowSpan = hasAddedFields ? 8 : 6;
        } else if (showDetails) {
            rowSpan = hasAddedFields ? 9 : 7;
        } else {
            //noinspection IfCanBeSwitch
            if (proposalType == ProposalType.COMPENSATION_REQUEST)
                rowSpan = 6;
            else if (proposalType == ProposalType.CHANGE_PARAM)
                rowSpan = 7;
            else if (proposalType == ProposalType.BURN_BOND)
                rowSpan = 6;
            else
                rowSpan = 5;
        }

        addTitledGroupBg(gridPane, gridRow, rowSpan, title, top);
        if (showDetails) {
            uidTextField = addLabelInputTextField(gridPane, gridRow,
                    Res.getWithCol("shared.id"), top == Layout.GROUP_DISTANCE ? Layout.FIRST_ROW_AND_GROUP_DISTANCE : Layout.FIRST_ROW_DISTANCE).second;
            uidTextField.setEditable(false);
            nameTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("dao.proposal.display.name")).second;
        } else {
            nameTextField = addLabelInputTextField(gridPane, gridRow, Res.get("dao.proposal.display.name"),
                    top == Layout.GROUP_DISTANCE ? Layout.FIRST_ROW_AND_GROUP_DISTANCE : Layout.FIRST_ROW_DISTANCE).second;
        }

        titleTextField = addLabelInputTextField(gridPane, ++gridRow, Res.getWithCol("dao.proposal.title")).second;

        descriptionTextArea = addLabelTextArea(gridPane, ++gridRow, Res.get("dao.proposal.display.description"),
                Res.get("dao.proposal.display.description.prompt", maxLengthDescriptionText)).second;
        descriptionTextArea.setMaxHeight(42); // for 2 lines
        descriptionTextArea.setMinHeight(descriptionTextArea.getMaxHeight());
        if (isMakeProposalScreen)
            descriptionTextArea.textProperty().addListener(descriptionTextAreaListener);

        linkInputTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("dao.proposal.display.link")).second;
        linkHyperlinkWithIcon = addLabelHyperlinkWithIcon(gridPane, gridRow, Res.get("dao.proposal.display.link"), "", "").second;
        linkHyperlinkWithIcon.setVisible(false);
        linkHyperlinkWithIcon.setManaged(false);
        linkInputTextField.setPromptText(Res.get("dao.proposal.display.link.prompt"));

        switch (proposalType) {
            case COMPENSATION_REQUEST:

                requestedBsqTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("dao.proposal.display.requestedBsq")).second;
                BsqValidator bsqValidator = new BsqValidator(bsqFormatter);
                bsqValidator.setMinValue(CompensationConsensus.getMinCompensationRequestAmount());
                checkNotNull(requestedBsqTextField, "requestedBsqTextField must no tbe null");
                requestedBsqTextField.setValidator(bsqValidator);
                // TODO validator, addressTF
                if (showDetails) {
                    bsqAddressTextField = addLabelInputTextField(gridPane, ++gridRow,
                            Res.get("dao.proposal.display.bsqAddress")).second;
                    checkNotNull(bsqAddressTextField, "bsqAddressTextField must no tbe null");
                    bsqAddressTextField.setText("B" + bsqWalletService.getUnusedAddress().toBase58());
                    bsqAddressTextField.setValidator(new BsqAddressValidator(bsqFormatter));
                }
                break;
            case GENERIC:
                break;
            case CHANGE_PARAM:
                checkNotNull(gridPane, "gridPane must no tbe null");
                paramComboBox = addLabelComboBox(gridPane, ++gridRow, Res.get("dao.proposal.display.paramComboBox.label")).second;
                checkNotNull(paramComboBox, "paramComboBox must no tbe null");
                paramComboBox.setItems(FXCollections.observableArrayList(Param.values()));
                paramComboBox.setConverter(new StringConverter<Param>() {
                    @Override
                    public String toString(Param param) {
                        return param.name();
                    }

                    @Override
                    public Param fromString(String string) {
                        return null;
                    }
                });
                paramValueTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("dao.proposal.display.paramValue")).second;
                break;
            case REMOVE_ALTCOIN:
                break;
            case BURN_BOND:
                burnBondComboBox = addLabelComboBox(gridPane, ++gridRow,
                        Res.get("dao.proposal.display.burnBondComboBox.label")).second;
                ObservableList<String> burnableBonds = FXCollections.observableArrayList();
                burnableBonds.addAll("bond1", "bond2", "bond3");
                burnBondComboBox.setItems(burnableBonds);
                break;
        }

        if (!isMakeProposalScreen && showDetails)
            txIdTextField = addLabelTxIdTextField(gridPane, ++gridRow,
                    Res.get("dao.proposal.display.txId"), "").second;

        proposalFeeTextField = addLabelTextField(gridPane, ++gridRow, Res.get("dao.proposal.display.proposalFee")).second;
        if (isMakeProposalScreen)
            proposalFeeTextField.setText(bsqFormatter.formatCoinWithCode(daoFacade.getProposalFee(daoFacade.getChainHeight())));
    }

    public void applyProposalPayload(Proposal proposal) {
        if (uidTextField != null)
            uidTextField.setText(proposal.getUid());
        nameTextField.setText(proposal.getName());
        titleTextField.setText(proposal.getTitle());
        descriptionTextArea.setText(proposal.getDescription());
        linkInputTextField.setVisible(false);
        linkInputTextField.setManaged(false);
        linkHyperlinkWithIcon.setVisible(true);
        linkHyperlinkWithIcon.setManaged(true);
        linkHyperlinkWithIcon.setText(proposal.getLink());
        linkHyperlinkWithIcon.setOnAction(e -> GUIUtil.openWebPage(proposal.getLink()));
        if (proposal instanceof CompensationProposal) {
            CompensationProposal compensationProposal = (CompensationProposal) proposal;
            checkNotNull(requestedBsqTextField, "requestedBsqTextField must no tbe null");
            requestedBsqTextField.setText(bsqFormatter.formatCoinWithCode(compensationProposal.getRequestedBsq()));
            if (bsqAddressTextField != null)
                bsqAddressTextField.setText(compensationProposal.getBsqAddress());
        } else if (proposal instanceof ChangeParamProposal) {
            ChangeParamProposal changeParamProposal = (ChangeParamProposal) proposal;
            checkNotNull(paramComboBox, "paramComboBox must not be null");
            paramComboBox.getSelectionModel().select(changeParamProposal.getParam());
            checkNotNull(paramValueTextField, "paramValueTextField must no tbe null");
            paramValueTextField.setText(String.valueOf(changeParamProposal.getParamValue()));
        } else if (proposal instanceof BurnBondProposal) {
            BurnBondProposal burnBondProposal = (BurnBondProposal) proposal;
            checkNotNull(burnBondComboBox, "burnBondComboBox must not be null");
            burnBondComboBox.getSelectionModel().select(burnBondProposal.getBondId());
        }
        int chainHeight;
        if (txIdTextField != null) {
            txIdTextField.setup(proposal.getTxId());
            chainHeight = daoFacade.getChainHeight();
        } else {
            chainHeight = daoFacade.getTx(proposal.getTxId()).map(Tx::getBlockHeight).orElse(0);
        }
        proposalFeeTextField.setText(bsqFormatter.formatCoinWithCode(daoFacade.getProposalFee(chainHeight)));
    }

    public void clearForm() {
        if (uidTextField != null) uidTextField.clear();
        if (nameTextField != null) nameTextField.clear();
        if (titleTextField != null) titleTextField.clear();
        if (descriptionTextArea != null) descriptionTextArea.clear();
        if (linkInputTextField != null) linkInputTextField.clear();
        if (linkHyperlinkWithIcon != null) linkHyperlinkWithIcon.clear();
        if (requestedBsqTextField != null) requestedBsqTextField.clear();
        if (bsqAddressTextField != null) bsqAddressTextField.clear();
        if (paramComboBox != null) paramComboBox.getSelectionModel().clearSelection();
        if (paramValueTextField != null) paramValueTextField.clear();
        if (burnBondComboBox != null) burnBondComboBox.getSelectionModel().clearSelection();
        if (txIdTextField != null) txIdTextField.cleanup();
        if (descriptionTextArea != null) descriptionTextArea.textProperty().removeListener(descriptionTextAreaListener);
    }

    public void fillWithMock() {
        uidTextField.setText(UUID.randomUUID().toString());
        nameTextField.setText("Manfred Karrer");
        titleTextField.setText("Development work November 2017");
        descriptionTextArea.setText("Development work");
        linkInputTextField.setText("https://github.com/bisq-network/compensation/issues/12");
        if (requestedBsqTextField != null)
            requestedBsqTextField.setText("14000");
        if (bsqAddressTextField != null)
            bsqAddressTextField.setText("B" + bsqWalletService.getUnusedAddress().toBase58());

        if (paramComboBox != null)
            paramComboBox.getSelectionModel().select(8); // PROPOSAL_FEE
        if (paramValueTextField != null)
            paramValueTextField.setText("333");
    }

    public void setEditable(boolean isEditable) {
        nameTextField.setEditable(isEditable);
        titleTextField.setEditable(isEditable);
        descriptionTextArea.setEditable(isEditable);
        linkInputTextField.setEditable(isEditable);
        if (requestedBsqTextField != null)
            requestedBsqTextField.setEditable(isEditable);
        if (bsqAddressTextField != null)
            bsqAddressTextField.setEditable(isEditable);

        if (paramComboBox != null)
            paramComboBox.setDisable(!isEditable);
        if (paramValueTextField != null)
            paramValueTextField.setEditable(isEditable);

        if (burnBondComboBox != null)
            burnBondComboBox.setDisable(!isEditable);

        linkInputTextField.setVisible(true);
        linkInputTextField.setManaged(true);
        linkHyperlinkWithIcon.setVisible(false);
        linkHyperlinkWithIcon.setManaged(false);
        linkHyperlinkWithIcon.setOnAction(null);
    }

    public void removeAllFields() {
        if (gridRow > 0) {
            clearForm();
            GUIUtil.removeChildrenFromGridPaneRows(gridPane, gridRowStartIndex, gridRow);
            gridRow = gridRowStartIndex;
        }
    }

    public int incrementAndGetGridRow() {
        return ++gridRow;
    }

    @SuppressWarnings("Duplicates")
    public ScrollPane getView() {
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setMinHeight(280); // just enough to display overview at voting without scroller

        AnchorPane anchorPane = new AnchorPane();
        scrollPane.setContent(anchorPane);

        gridPane.setHgap(5);
        gridPane.setVgap(5);
        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHalignment(HPos.RIGHT);
        columnConstraints1.setHgrow(Priority.SOMETIMES);
        columnConstraints1.setMinWidth(140);
        ColumnConstraints columnConstraints2 = new ColumnConstraints();
        columnConstraints2.setHgrow(Priority.ALWAYS);
        columnConstraints2.setMinWidth(300);

        gridPane.getColumnConstraints().addAll(columnConstraints1, columnConstraints2);
        AnchorPane.setBottomAnchor(gridPane, 20d);
        AnchorPane.setRightAnchor(gridPane, 10d);
        AnchorPane.setLeftAnchor(gridPane, 10d);
        AnchorPane.setTopAnchor(gridPane, 20d);
        anchorPane.getChildren().add(gridPane);

        return scrollPane;
    }
}
