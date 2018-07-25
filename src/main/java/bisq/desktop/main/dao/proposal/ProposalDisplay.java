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
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.BsqAddressValidator;
import bisq.desktop.util.validation.BsqValidator;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.role.BondedRole;
import bisq.core.dao.role.BondedRoleType;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.ext.Param;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.dao.voting.proposal.ProposalType;
import bisq.core.dao.voting.proposal.compensation.CompensationConsensus;
import bisq.core.dao.voting.proposal.compensation.CompensationProposal;
import bisq.core.dao.voting.proposal.confiscatebond.ConfiscateBondProposal;
import bisq.core.dao.voting.proposal.param.ChangeParamProposal;
import bisq.core.dao.voting.proposal.role.BondedRoleProposal;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;
import bisq.core.util.validation.InputValidator;
import bisq.core.util.validation.IntegerValidator;

import bisq.common.util.Tuple2;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.geometry.HPos;

import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;

import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.*;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class ProposalDisplay {
    private final GridPane gridPane;
    private final BsqFormatter bsqFormatter;
    private final BsqWalletService bsqWalletService;
    private final DaoFacade daoFacade;

    @Nullable
    private TextField uidTextField;
    private TextField proposalFeeTextField, proposalTypeTextField;
    public InputTextField nameTextField;
    public InputTextField linkInputTextField;
    @Nullable
    public InputTextField requestedBsqTextField, bsqAddressTextField, paramValueTextField;
    @Nullable
    public ComboBox<Param> paramComboBox;
    @Nullable
    public ComboBox<BondedRole> confiscateBondComboBox;
    @Nullable
    public ComboBox<BondedRoleType> bondedRoleTypeComboBox;

    @Getter
    private int gridRow;
    private HyperlinkWithIcon linkHyperlinkWithIcon;
    @Nullable
    private TxIdTextField txIdTextField;
    private int gridRowStartIndex;
    private List<Runnable> inputChangedListeners = new ArrayList<>();
    @Getter
    private List<TextInputControl> inputControls = new ArrayList<>();
    @Getter
    private List<ComboBox> comboBoxes = new ArrayList<>();
    private final ChangeListener<Boolean> focusOutListener;
    private final ChangeListener<Object> inputListener;


    // TODO get that warning at closing the window...
    // javafx.scene.CssStyleHelper calculateValue
    // WARNING: Could not resolve '-fx-accent' while resolving lookups for '-fx-text-fill' from rule '*.hyperlink' in stylesheet file:/Users/dev/idea/bisq/desktop/out/production/resources/bisq/desktop/bisq.css

    public ProposalDisplay(GridPane gridPane, BsqFormatter bsqFormatter, BsqWalletService bsqWalletService,
                           DaoFacade daoFacade) {
        this.gridPane = gridPane;
        this.bsqFormatter = bsqFormatter;
        this.bsqWalletService = bsqWalletService;
        this.daoFacade = daoFacade;

        // focusOutListener = observable -> inputChangedListeners.forEach(Runnable::run);

        focusOutListener = (observable, oldValue, newValue) -> {
            if (oldValue && !newValue)
                inputChangedListeners.forEach(Runnable::run);
        };
        inputListener = (observable, oldValue, newValue) -> inputChangedListeners.forEach(Runnable::run);
    }

    public void addInputChangedListener(Runnable listener) {
        inputChangedListeners.add(listener);
    }

    public void removeInputChangedListener(Runnable listener) {
        inputChangedListeners.remove(listener);
    }

    public void createAllFields(String title, int gridRowStartIndex, double top, ProposalType proposalType,
                                boolean isMakeProposalScreen) {
        removeAllFields();
        this.gridRowStartIndex = gridRowStartIndex;
        this.gridRow = gridRowStartIndex;
        int rowSpan = 5;

        switch (proposalType) {
            case COMPENSATION_REQUEST:
                rowSpan = 6;
                break;
            case BONDED_ROLE:
                rowSpan = 5;
                break;
            case REMOVE_ALTCOIN:
                break;
            case CHANGE_PARAM:
                rowSpan = 6;
                break;
            case GENERIC:
                break;
            case CONFISCATE_BOND:
                rowSpan = 5;
                break;
        }
        // for already created proposals we show the uid and tx id so we add 2 rows
        if (!isMakeProposalScreen)
            rowSpan += 2;

        addTitledGroupBg(gridPane, gridRow, rowSpan, title, top);
        double proposalTypeTop = top == Layout.GROUP_DISTANCE ? Layout.FIRST_ROW_AND_GROUP_DISTANCE : Layout.FIRST_ROW_DISTANCE;
        proposalTypeTextField = addLabelTextField(gridPane, gridRow,
                Res.getWithCol("dao.proposal.display.type"), proposalType.getDisplayName(), proposalTypeTop).second;

        if (!isMakeProposalScreen)
            uidTextField = addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.id")).second;

        nameTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("dao.proposal.display.name")).second;
        nameTextField.setValidator(new InputValidator());
        inputControls.add(nameTextField);

        Tuple2<Label, InputTextField> tuple = addLabelInputTextField(gridPane, ++gridRow,
                Res.get("dao.proposal.display.link"));
        linkInputTextField = tuple.second;
        linkInputTextField.setPromptText(Res.get("dao.proposal.display.link.prompt"));
        linkInputTextField.setValidator(new InputValidator());
        inputControls.add(linkInputTextField);

        linkHyperlinkWithIcon = addLabelHyperlinkWithIcon(gridPane, gridRow,
                "", "", "").second;
        linkHyperlinkWithIcon.setVisible(false);
        linkHyperlinkWithIcon.setManaged(false);

        switch (proposalType) {
            case COMPENSATION_REQUEST:
                requestedBsqTextField = addLabelInputTextField(gridPane, ++gridRow,
                        Res.get("dao.proposal.display.requestedBsq")).second;
                BsqValidator bsqValidator = new BsqValidator(bsqFormatter);
                bsqValidator.setMinValue(CompensationConsensus.getMinCompensationRequestAmount());
                checkNotNull(requestedBsqTextField, "requestedBsqTextField must not be null");
                requestedBsqTextField.setValidator(bsqValidator);
                inputControls.add(requestedBsqTextField);

                // TODO validator, addressTF
                bsqAddressTextField = addLabelInputTextField(gridPane, ++gridRow,
                        Res.get("dao.proposal.display.bsqAddress")).second;
                checkNotNull(bsqAddressTextField, "bsqAddressTextField must not be null");
                bsqAddressTextField.setText("B" + bsqWalletService.getUnusedAddress().toBase58());
                bsqAddressTextField.setValidator(new BsqAddressValidator(bsqFormatter));
                inputControls.add(bsqAddressTextField);
                break;
            case BONDED_ROLE:
                bondedRoleTypeComboBox = FormBuilder.<BondedRoleType>addLabelComboBox(gridPane, ++gridRow,
                        Res.getWithCol("dao.proposal.display.bondedRoleComboBox.label")).second;
                checkNotNull(bondedRoleTypeComboBox, "bondedRoleTypeComboBox must not be null");
                bondedRoleTypeComboBox.setPromptText(Res.get("shared.select"));
                bondedRoleTypeComboBox.setItems(FXCollections.observableArrayList(BondedRoleType.values()));
                bondedRoleTypeComboBox.setConverter(new StringConverter<BondedRoleType>() {
                    @Override
                    public String toString(BondedRoleType bondedRoleType) {
                        return bondedRoleType != null ? bondedRoleType.getDisplayString() : "";
                    }

                    @Override
                    public BondedRoleType fromString(String string) {
                        return null;
                    }
                });
                comboBoxes.add(bondedRoleTypeComboBox);
                break;
            case REMOVE_ALTCOIN:
                break;
            case CHANGE_PARAM:
                checkNotNull(gridPane, "gridPane must not be null");
                paramComboBox = FormBuilder.<Param>addLabelComboBox(gridPane, ++gridRow,
                        Res.getWithCol("dao.proposal.display.paramComboBox.label")).second;
                checkNotNull(paramComboBox, "paramComboBox must not be null");
                paramComboBox.setPromptText(Res.get("shared.select"));
                List<Param> list = Arrays.stream(Param.values())
                        .filter(e -> e != Param.UNDEFINED && e != Param.PHASE_UNDEFINED)
                        .collect(Collectors.toList());
                paramComboBox.setItems(FXCollections.observableArrayList(list));
                paramComboBox.setConverter(new StringConverter<Param>() {
                    @Override
                    public String toString(Param param) {
                        return param != null ? param.getDisplayString() : "";
                    }

                    @Override
                    public Param fromString(String string) {
                        return null;
                    }
                });
                comboBoxes.add(paramComboBox);
                paramValueTextField = addLabelInputTextField(gridPane, ++gridRow,
                        Res.get("dao.proposal.display.paramValue")).second;
                paramValueTextField.setValidator(new IntegerValidator());
                inputControls.add(paramValueTextField);
                break;
            case GENERIC:
                break;
            case CONFISCATE_BOND:
                confiscateBondComboBox = FormBuilder.<BondedRole>addLabelComboBox(gridPane, ++gridRow,
                        Res.getWithCol("dao.proposal.display.confiscateBondComboBox.label")).second;
                checkNotNull(confiscateBondComboBox, "confiscateBondComboBox must not be null");
                confiscateBondComboBox.setPromptText(Res.get("shared.select"));
                confiscateBondComboBox.setItems(FXCollections.observableArrayList(daoFacade.getValidBondedRoleList()));
                confiscateBondComboBox.setConverter(new StringConverter<BondedRole>() {
                    @Override
                    public String toString(BondedRole bondedRole) {
                        return bondedRole != null ? bondedRole.getDisplayString() : "";
                    }

                    @Override
                    public BondedRole fromString(String string) {
                        return null;
                    }
                });
                comboBoxes.add(confiscateBondComboBox);
                break;
        }

        if (!isMakeProposalScreen)
            txIdTextField = addLabelTxIdTextField(gridPane, ++gridRow,
                    Res.get("dao.proposal.display.txId"), "").second;

        proposalFeeTextField = addLabelTextField(gridPane, ++gridRow, Res.get("dao.proposal.display.proposalFee")).second;
        if (isMakeProposalScreen)
            proposalFeeTextField.setText(bsqFormatter.formatCoinWithCode(daoFacade.getProposalFee(daoFacade.getChainHeight())));

        addListeners();
    }

    public void applyProposalPayload(Proposal proposal) {
        proposalTypeTextField.setText(proposal.getType().getDisplayName());
        if (uidTextField != null)
            uidTextField.setText(proposal.getUid());

        nameTextField.setText(proposal.getName());
        linkInputTextField.setVisible(false);
        linkInputTextField.setManaged(false);
        linkHyperlinkWithIcon.setVisible(true);
        linkHyperlinkWithIcon.setManaged(true);
        linkHyperlinkWithIcon.setText(proposal.getLink());
        linkHyperlinkWithIcon.setOnAction(e -> GUIUtil.openWebPage(proposal.getLink()));
        if (proposal instanceof CompensationProposal) {
            CompensationProposal compensationProposal = (CompensationProposal) proposal;
            checkNotNull(requestedBsqTextField, "requestedBsqTextField must not be null");
            requestedBsqTextField.setText(bsqFormatter.formatCoinWithCode(compensationProposal.getRequestedBsq()));
            if (bsqAddressTextField != null)
                bsqAddressTextField.setText(compensationProposal.getBsqAddress());
        } else if (proposal instanceof ChangeParamProposal) {
            ChangeParamProposal changeParamProposal = (ChangeParamProposal) proposal;
            checkNotNull(paramComboBox, "paramComboBox must not be null");
            paramComboBox.getSelectionModel().select(changeParamProposal.getParam());
            checkNotNull(paramValueTextField, "paramValueTextField must not be null");
            paramValueTextField.setText(String.valueOf(changeParamProposal.getParamValue()));
        } else if (proposal instanceof BondedRoleProposal) {
            BondedRoleProposal bondedRoleProposal = (BondedRoleProposal) proposal;
            checkNotNull(bondedRoleTypeComboBox, "bondedRoleComboBox must not be null");
            BondedRole bondedRole = bondedRoleProposal.getBondedRole();
            bondedRoleTypeComboBox.getSelectionModel().select(bondedRole.getBondedRoleType());

        } else if (proposal instanceof ConfiscateBondProposal) {
            ConfiscateBondProposal confiscateBondProposal = (ConfiscateBondProposal) proposal;
            checkNotNull(confiscateBondComboBox, "confiscateBondComboBox must not be null");
            daoFacade.getBondedRoleFromHash(confiscateBondProposal.getHash())
                    .ifPresent(bondedRole -> confiscateBondComboBox.getSelectionModel().select(bondedRole));
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

    private void addListeners() {
        inputControls.stream()
                .filter(Objects::nonNull).forEach(inputControl -> {
            inputControl.textProperty().addListener(inputListener);
            inputControl.focusedProperty().addListener(focusOutListener);
        });
        comboBoxes.stream()
                .filter(Objects::nonNull).forEach(comboBox -> {
            comboBox.getSelectionModel().selectedItemProperty().addListener(inputListener);
        });
    }

    public void removeListeners() {
        inputControls.stream()
                .filter(Objects::nonNull).forEach(inputControl -> {
            inputControl.textProperty().removeListener(inputListener);
            inputControl.focusedProperty().removeListener(focusOutListener);
        });
        comboBoxes.stream()
                .filter(Objects::nonNull).forEach(comboBox -> {
            comboBox.getSelectionModel().selectedItemProperty().removeListener(inputListener);
        });
    }

    public void clearForm() {
        inputControls.stream().filter(Objects::nonNull).forEach(TextInputControl::clear);

        if (uidTextField != null) uidTextField.clear();
        if (linkHyperlinkWithIcon != null) linkHyperlinkWithIcon.clear();
        if (txIdTextField != null) txIdTextField.cleanup();

        comboBoxes.stream()
                .filter(Objects::nonNull).forEach(comboBox -> {
            comboBox.getSelectionModel().clearSelection();
        });
    }

    public void setEditable(boolean isEditable) {
        inputControls.stream().filter(Objects::nonNull).forEach(e -> e.setEditable(!isEditable));
        comboBoxes.stream()
                .filter(Objects::nonNull).forEach(comboBox -> comboBox.setEditable(!isEditable));

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

    public void fillWithMock() {
        if (uidTextField != null)
            uidTextField.setText(UUID.randomUUID().toString());
        nameTextField.setText("Manfred Karrer");
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
}
