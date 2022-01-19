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

import bisq.desktop.Navigation;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.components.TxIdTextField;
import bisq.desktop.main.MainView;
import bisq.desktop.main.dao.DaoView;
import bisq.desktop.main.dao.bonding.BondingView;
import bisq.desktop.main.dao.bonding.bonds.BondsView;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.BsqValidator;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.bond.Bond;
import bisq.core.dao.governance.bond.role.BondedRole;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.proposal.ProposalType;
import bisq.core.dao.governance.proposal.param.ChangeParamInputValidator;
import bisq.core.dao.governance.proposal.param.ChangeParamValidator;
import bisq.core.dao.state.model.blockchain.BaseTx;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.BondedRoleType;
import bisq.core.dao.state.model.governance.ChangeParamProposal;
import bisq.core.dao.state.model.governance.CompensationProposal;
import bisq.core.dao.state.model.governance.ConfiscateBondProposal;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.GenericProposal;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.ProposalVoteResult;
import bisq.core.dao.state.model.governance.ReimbursementProposal;
import bisq.core.dao.state.model.governance.RemoveAssetProposal;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.dao.state.model.governance.RoleProposal;
import bisq.core.dao.state.model.governance.Vote;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.validation.InputValidator;
import bisq.core.util.validation.RegexValidator;

import bisq.asset.Asset;

import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;

import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.*;
import static com.google.common.base.Preconditions.checkNotNull;

@SuppressWarnings({"ConstantConditions", "StatementWithEmptyBody"})
@Slf4j
public class ProposalDisplay {
    private final GridPane gridPane;
    private final BsqFormatter bsqFormatter;
    private final DaoFacade daoFacade;

    // Nullable because if we are in result view mode (readonly) we don't need to set the input validator)
    @Nullable
    private final ChangeParamValidator changeParamValidator;
    private final Navigation navigation;
    private final Preferences preferences;

    @Nullable
    private TextField proposalFeeTextField, comboBoxValueTextField, requiredBondForRoleTextField;
    private TextField proposalTypeTextField, myVoteTextField, voteResultTextField;
    public InputTextField nameTextField;
    public InputTextField linkInputTextField;
    @Nullable
    public InputTextField requestedBsqTextField, paramValueTextField;
    @Nullable
    public ComboBox<Param> paramComboBox;
    @Nullable
    public ComboBox<Bond> confiscateBondComboBox;
    @Nullable
    public ComboBox<BondedRoleType> bondedRoleTypeComboBox;
    @Nullable
    public ComboBox<Asset> assetComboBox;

    @Getter
    private int gridRow;
    private HyperlinkWithIcon linkHyperlinkWithIcon;
    private TxIdTextField txIdTextField;
    private int gridRowStartIndex;
    private final List<Runnable> inputChangedListeners = new ArrayList<>();
    @Getter
    private List<TextInputControl> inputControls = new ArrayList<>();
    @Getter
    private List<ComboBox<?>> comboBoxes = new ArrayList<>();
    private final ChangeListener<Boolean> focusOutListener;
    private final ChangeListener<Object> inputListener;
    private ChangeListener<Param> paramChangeListener;
    private ChangeListener<BondedRoleType> requiredBondForRoleListener;
    private TitledGroupBg myVoteTitledGroup;
    private VBox linkWithIconContainer, comboBoxValueContainer, myVoteBox, voteResultBox;
    private int votingBoxRowSpan;

    private Optional<Runnable> navigateHandlerOptional = Optional.empty();

    public ProposalDisplay(GridPane gridPane,
                           BsqFormatter bsqFormatter,
                           DaoFacade daoFacade,
                           @Nullable ChangeParamValidator changeParamValidator,
                           Navigation navigation,
                           @Nullable Preferences preferences) {
        this.gridPane = gridPane;
        this.bsqFormatter = bsqFormatter;
        this.daoFacade = daoFacade;
        this.changeParamValidator = changeParamValidator;
        this.navigation = navigation;
        this.preferences = preferences;

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
        createAllFields(title, gridRowStartIndex, top, proposalType, isMakeProposalScreen, null);
    }

    public void createAllFields(String title, int gridRowStartIndex, double top, ProposalType proposalType,
                                boolean isMakeProposalScreen, String titledGroupStyle) {
        removeAllFields();
        this.gridRowStartIndex = gridRowStartIndex;
        this.gridRow = gridRowStartIndex;
        int titledGroupBgRowSpan = 5;

        switch (proposalType) {
            case COMPENSATION_REQUEST:
            case REIMBURSEMENT_REQUEST:
            case CONFISCATE_BOND:
            case REMOVE_ASSET:
                break;
            case CHANGE_PARAM:
            case BONDED_ROLE:
                titledGroupBgRowSpan = 6;
                break;
            case GENERIC:
                titledGroupBgRowSpan = 4;
                break;
        }

        TitledGroupBg titledGroupBg = addTitledGroupBg(gridPane, gridRow, titledGroupBgRowSpan, title, top);

        if (titledGroupStyle != null) titledGroupBg.getStyleClass().add(titledGroupStyle);

        double proposalTypeTop;

        if (top == Layout.GROUP_DISTANCE_WITHOUT_SEPARATOR) {
            proposalTypeTop = Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE_WITHOUT_SEPARATOR;
        } else if (top == Layout.GROUP_DISTANCE) {
            proposalTypeTop = Layout.FIRST_ROW_AND_GROUP_DISTANCE;
        } else if (top == 0) {
            proposalTypeTop = Layout.FIRST_ROW_DISTANCE;
        } else {
            proposalTypeTop = Layout.FIRST_ROW_DISTANCE + top;
        }

        proposalTypeTextField = addTopLabelTextField(gridPane, gridRow,
                Res.get("dao.proposal.display.type"), proposalType.getDisplayName(), proposalTypeTop).second;

        nameTextField = addInputTextField(gridPane, ++gridRow, Res.get("dao.proposal.display.name"));
        if (isMakeProposalScreen)
            nameTextField.setValidator(new InputValidator());
        inputControls.add(nameTextField);

        linkInputTextField = addInputTextField(gridPane, ++gridRow,
                Res.get("dao.proposal.display.link"));
        linkInputTextField.setPromptText(Res.get("dao.proposal.display.link.prompt"));
        if (isMakeProposalScreen) {
            RegexValidator validator = new RegexValidator();
            if (proposalType == ProposalType.COMPENSATION_REQUEST) {
                validator.setPattern("https://bisq.network/dao-compensation/\\d+");
                linkInputTextField.setText("https://bisq.network/dao-compensation/#");
            } else if (proposalType == ProposalType.REIMBURSEMENT_REQUEST) {
                validator.setPattern("https://bisq.network/dao-reimbursement/\\d+");
                linkInputTextField.setText("https://bisq.network/dao-reimbursement/#");
            } else {
                validator.setPattern("https://bisq.network/dao-proposals/\\d+");
                linkInputTextField.setText("https://bisq.network/dao-proposals/#");
            }
            linkInputTextField.setValidator(validator);
        }
        inputControls.add(linkInputTextField);

        Tuple3<Label, HyperlinkWithIcon, VBox> tuple = addTopLabelHyperlinkWithIcon(gridPane, gridRow,
                Res.get("dao.proposal.display.link"), "", "", 0);
        linkHyperlinkWithIcon = tuple.second;
        linkWithIconContainer = tuple.third;
        // TODO HyperlinkWithIcon does not scale automatically (button base, -> make anchorpane as base)
        linkHyperlinkWithIcon.prefWidthProperty().bind(nameTextField.widthProperty());

        linkWithIconContainer.setVisible(false);
        linkWithIconContainer.setManaged(false);

        if (!isMakeProposalScreen) {
            final Tuple3<Label, TxIdTextField, VBox> labelTxIdTextFieldVBoxTuple3 =
                    addTopLabelTxIdTextField(gridPane, ++gridRow, Res.get("dao.proposal.display.txId"), -10d);
            txIdTextField = labelTxIdTextFieldVBoxTuple3.second;
        }

        int comboBoxValueTextFieldIndex = -1;
        switch (proposalType) {
            case COMPENSATION_REQUEST:
            case REIMBURSEMENT_REQUEST:
                requestedBsqTextField = addInputTextField(gridPane, ++gridRow,
                        Res.get("dao.proposal.display.requestedBsq"));
                checkNotNull(requestedBsqTextField, "requestedBsqTextField must not be null");
                inputControls.add(requestedBsqTextField);

                if (isMakeProposalScreen) {
                    BsqValidator bsqValidator = new BsqValidator(bsqFormatter);
                    if (proposalType == ProposalType.COMPENSATION_REQUEST) {
                        bsqValidator.setMinValue(daoFacade.getMinCompensationRequestAmount());
                        bsqValidator.setMaxValue(daoFacade.getMaxCompensationRequestAmount());
                    } else if (proposalType == ProposalType.REIMBURSEMENT_REQUEST) {
                        bsqValidator.setMinValue(daoFacade.getMinReimbursementRequestAmount());
                        bsqValidator.setMaxValue(daoFacade.getMaxReimbursementRequestAmount());
                    }
                    requestedBsqTextField.setValidator(bsqValidator);
                }
                break;
            case CHANGE_PARAM:
                checkNotNull(gridPane, "gridPane must not be null");
                paramComboBox = FormBuilder.addComboBox(gridPane, ++gridRow,
                        Res.get("dao.proposal.display.paramComboBox.label"));
                comboBoxValueTextFieldIndex = gridRow;
                checkNotNull(paramComboBox, "paramComboBox must not be null");
                List<Param> list = Arrays.stream(Param.values())
                        .filter(e -> e != Param.UNDEFINED && e != Param.PHASE_UNDEFINED)
                        .collect(Collectors.toList());
                paramComboBox.setItems(FXCollections.observableArrayList(list));
                paramComboBox.setConverter(new StringConverter<>() {
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
                paramValueTextField = addInputTextField(gridPane, ++gridRow,
                        Res.get("dao.proposal.display.paramValue"));

                inputControls.add(paramValueTextField);

                paramChangeListener = (observable, oldValue, newValue) -> {
                    if (newValue != null) {
                        paramValueTextField.clear();
                        String currentValue = bsqFormatter.formatParamValue(newValue, daoFacade.getParamValue(newValue));
                        paramValueTextField.setPromptText(Res.get("dao.param.currentValue", currentValue));
                        if (changeParamValidator != null && isMakeProposalScreen) {
                            ChangeParamInputValidator validator = new ChangeParamInputValidator(newValue, changeParamValidator);
                            paramValueTextField.setValidator(validator);
                        }
                    }
                };
                paramComboBox.getSelectionModel().selectedItemProperty().addListener(paramChangeListener);
                break;
            case BONDED_ROLE:
                bondedRoleTypeComboBox = FormBuilder.addComboBox(gridPane, ++gridRow,
                        Res.get("dao.proposal.display.bondedRoleComboBox.label"));
                comboBoxValueTextFieldIndex = gridRow;
                checkNotNull(bondedRoleTypeComboBox, "bondedRoleTypeComboBox must not be null");
                List<BondedRoleType> bondedRoleTypes = Arrays.stream(BondedRoleType.values())
                        .filter(e -> e != BondedRoleType.UNDEFINED)
                        .collect(Collectors.toList());
                bondedRoleTypeComboBox.setItems(FXCollections.observableArrayList(bondedRoleTypes));
                bondedRoleTypeComboBox.setConverter(new StringConverter<>() {
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
                requiredBondForRoleTextField = addTopLabelReadOnlyTextField(gridPane, ++gridRow,
                        Res.get("dao.proposal.display.requiredBondForRole.label")).second;

                requiredBondForRoleListener = (observable, oldValue, newValue) -> {
                    if (newValue != null) {
                        requiredBondForRoleTextField.setText(bsqFormatter.formatCoinWithCode(Coin.valueOf(daoFacade.getRequiredBond(newValue))));
                    }
                };
                bondedRoleTypeComboBox.getSelectionModel().selectedItemProperty().addListener(requiredBondForRoleListener);

                break;
            case CONFISCATE_BOND:
                confiscateBondComboBox = FormBuilder.addComboBox(gridPane, ++gridRow,
                        Res.get("dao.proposal.display.confiscateBondComboBox.label"));
                comboBoxValueTextFieldIndex = gridRow;
                checkNotNull(confiscateBondComboBox, "confiscateBondComboBox must not be null");

                confiscateBondComboBox.setItems(FXCollections.observableArrayList(daoFacade.getAllActiveBonds()));
                confiscateBondComboBox.setConverter(new StringConverter<>() {
                    @Override
                    public String toString(Bond bond) {
                        String details = " (" + Res.get("dao.bond.table.column.lockupTxId") + ": " + bond.getLockupTxId() + ")";
                        if (bond instanceof BondedRole) {
                            return bond.getBondedAsset().getDisplayString() + details;
                        } else {
                            return Res.get("dao.bond.bondedReputation") + details;
                        }
                    }

                    @Override
                    public Bond fromString(String string) {
                        return null;
                    }
                });
                comboBoxes.add(confiscateBondComboBox);
                break;
            case GENERIC:
                break;
            case REMOVE_ASSET:
                assetComboBox = FormBuilder.addComboBox(gridPane, ++gridRow,
                        Res.get("dao.proposal.display.assetComboBox.label"));
                comboBoxValueTextFieldIndex = gridRow;
                checkNotNull(assetComboBox, "assetComboBox must not be null");
                List<Asset> assetList = CurrencyUtil.getSortedAssetStream()
                        .filter(e -> !e.getTickerSymbol().equals("BSQ"))
                        .collect(Collectors.toList());
                assetComboBox.setItems(FXCollections.observableArrayList(assetList));
                assetComboBox.setConverter(new StringConverter<>() {
                    @Override
                    public String toString(Asset asset) {
                        return asset != null ? CurrencyUtil.getNameAndCode(asset.getTickerSymbol()) : "";
                    }

                    @Override
                    public Asset fromString(String string) {
                        return null;
                    }
                });
                comboBoxes.add(assetComboBox);
                break;
        }

        if (comboBoxValueTextFieldIndex > -1) {
            Tuple3<Label, TextField, VBox> tuple3 = addTopLabelReadOnlyTextField(gridPane, comboBoxValueTextFieldIndex,
                    Res.get("dao.proposal.display.option"));
            comboBoxValueTextField = tuple3.second;
            comboBoxValueContainer = tuple3.third;
            comboBoxValueContainer.setVisible(false);
            comboBoxValueContainer.setManaged(false);
        }

        if (isMakeProposalScreen) {
            proposalFeeTextField = addTopLabelTextField(gridPane, ++gridRow, Res.get("dao.proposal.display.proposalFee")).second;
            proposalFeeTextField.setText(bsqFormatter.formatCoinWithCode(daoFacade.getProposalFee(daoFacade.getChainHeight())));
        }

        votingBoxRowSpan = 4;

        myVoteTitledGroup = addTitledGroupBg(gridPane, ++gridRow, 4, Res.get("dao.proposal.myVote.title"), Layout.COMPACT_FIRST_ROW_DISTANCE);

        Tuple3<Label, TextField, VBox> tuple3 = addTopLabelTextField(gridPane, ++gridRow, Res.get("dao.proposal.display.myVote"), Layout.COMPACT_FIRST_ROW_DISTANCE);

        myVoteBox = tuple3.third;
        setMyVoteBoxVisibility(false);

        myVoteTextField = tuple3.second;

        tuple3 = addTopLabelReadOnlyTextField(gridPane, ++gridRow, Res.get("dao.proposal.display.voteResult"));

        voteResultBox = tuple3.third;
        voteResultBox.setVisible(false);
        voteResultBox.setManaged(false);

        voteResultTextField = tuple3.second;

        addListeners();
    }

    public void applyBallot(@Nullable Ballot ballot) {
        String myVote = Res.get("dao.proposal.display.myVote.ignored");
        boolean isNotNull = ballot != null;
        Vote vote = isNotNull ? ballot.getVote() : null;
        if (vote != null) {
            myVote = vote.isAccepted() ? Res.get("dao.proposal.display.myVote.accepted") :
                    Res.get("dao.proposal.display.myVote.rejected");
        }
        myVoteTextField.setText(myVote);

        setMyVoteBoxVisibility(isNotNull);
    }

    public void applyEvaluatedProposal(@Nullable EvaluatedProposal evaluatedProposal) {

        boolean isEvaluatedProposalNotNull = evaluatedProposal != null;
        if (isEvaluatedProposalNotNull) {
            String result = evaluatedProposal.isAccepted() ? Res.get("dao.proposal.voteResult.success") :
                    Res.get("dao.proposal.voteResult.failed");
            ProposalVoteResult proposalVoteResult = evaluatedProposal.getProposalVoteResult();
            String threshold = (proposalVoteResult.getThreshold() / 100D) + "%";
            String requiredThreshold = (daoFacade.getRequiredThreshold(evaluatedProposal.getProposal()) * 100D) + "%";
            String quorum = bsqFormatter.formatCoinWithCode(Coin.valueOf(proposalVoteResult.getQuorum()));
            String requiredQuorum = bsqFormatter.formatCoinWithCode(daoFacade.getRequiredQuorum(evaluatedProposal.getProposal()));
            String summary = Res.get("dao.proposal.voteResult.summary", result,
                    threshold, requiredThreshold, quorum, requiredQuorum);
            voteResultTextField.setText(summary);
        }
        voteResultBox.setVisible(isEvaluatedProposalNotNull);
        voteResultBox.setManaged(isEvaluatedProposalNotNull);
    }

    public void applyBallotAndVoteWeight(@Nullable Ballot ballot, long merit, long stake) {
        applyBallotAndVoteWeight(ballot, merit, stake, true);
    }

    public void applyBallotAndVoteWeight(@Nullable Ballot ballot, long merit, long stake, boolean ballotIncluded) {
        boolean ballotIsNotNull = ballot != null;
        boolean hasVoted = stake > 0;
        if (hasVoted) {
            String myVote = Res.get("dao.proposal.display.myVote.ignored");
            Vote vote = ballotIsNotNull ? ballot.getVote() : null;
            if (vote != null) {
                myVote = vote.isAccepted() ? Res.get("dao.proposal.display.myVote.accepted") :
                        Res.get("dao.proposal.display.myVote.rejected");
            }

            String voteIncluded = ballotIncluded ? "" : " - " + Res.get("dao.proposal.display.myVote.unCounted");
            String meritString = bsqFormatter.formatCoinWithCode(Coin.valueOf(merit));
            String stakeString = bsqFormatter.formatCoinWithCode(Coin.valueOf(stake));
            String weight = bsqFormatter.formatCoinWithCode(Coin.valueOf(merit + stake));
            String myVoteSummary = Res.get("dao.proposal.myVote.summary", myVote,
                    weight, meritString, stakeString, voteIncluded);
            myVoteTextField.setText(myVoteSummary);

            GridPane.setRowSpan(myVoteTitledGroup, votingBoxRowSpan - 1);
        }

        boolean show = ballotIsNotNull && hasVoted;
        setMyVoteBoxVisibility(show);
    }

    public void setIsVoteIncludedInResult(boolean isVoteIncludedInResult) {
        if (!isVoteIncludedInResult && myVoteTextField != null && !myVoteTextField.getText().isEmpty()) {
            String text = myVoteTextField.getText();
            myVoteTextField.setText(Res.get("dao.proposal.myVote.invalid") + " - " + text);
            myVoteTextField.getStyleClass().add("error-text");
        }
    }

    public void applyProposalPayload(Proposal proposal) {
        proposalTypeTextField.setText(proposal.getType().getDisplayName());
        nameTextField.setText(proposal.getName());
        linkInputTextField.setVisible(false);
        linkInputTextField.setManaged(false);
        if (linkWithIconContainer != null) {
            linkWithIconContainer.setVisible(true);
            linkWithIconContainer.setManaged(true);
            linkHyperlinkWithIcon.setText(proposal.getLink());
            linkHyperlinkWithIcon.setOnAction(e -> GUIUtil.openWebPage(proposal.getLink()));
        }

        if (txIdTextField != null) {
            txIdTextField.setup(proposal.getTxId());
        }

        if (proposal instanceof CompensationProposal) {
            CompensationProposal compensationProposal = (CompensationProposal) proposal;
            checkNotNull(requestedBsqTextField, "requestedBsqTextField must not be null");
            requestedBsqTextField.setText(bsqFormatter.formatCoinWithCode(compensationProposal.getRequestedBsq()));
        } else if (proposal instanceof ReimbursementProposal) {
            ReimbursementProposal reimbursementProposal = (ReimbursementProposal) proposal;
            checkNotNull(requestedBsqTextField, "requestedBsqTextField must not be null");
            requestedBsqTextField.setText(bsqFormatter.formatCoinWithCode(reimbursementProposal.getRequestedBsq()));
        } else if (proposal instanceof ChangeParamProposal) {
            ChangeParamProposal changeParamProposal = (ChangeParamProposal) proposal;
            checkNotNull(paramComboBox, "paramComboBox must not be null");
            paramComboBox.getSelectionModel().select(changeParamProposal.getParam());
            comboBoxValueTextField.setText(paramComboBox.getConverter().toString(changeParamProposal.getParam()));
            checkNotNull(paramValueTextField, "paramValueTextField must not be null");
            paramValueTextField.setText(bsqFormatter.formatParamValue(changeParamProposal.getParam(), changeParamProposal.getParamValue()));
            String currentValue = bsqFormatter.formatParamValue(changeParamProposal.getParam(),
                    daoFacade.getParamValue(changeParamProposal.getParam()));
            int height = daoFacade.getTx(changeParamProposal.getTxId())
                    .map(BaseTx::getBlockHeight)
                    .orElse(daoFacade.getGenesisBlockHeight());
            String valueAtProposal = bsqFormatter.formatParamValue(changeParamProposal.getParam(),
                    daoFacade.getParamValue(changeParamProposal.getParam(), height));
            paramValueTextField.setPromptText(Res.get("dao.param.currentAndPastValue", currentValue, valueAtProposal));
        } else if (proposal instanceof RoleProposal) {
            RoleProposal roleProposal = (RoleProposal) proposal;
            checkNotNull(bondedRoleTypeComboBox, "bondedRoleComboBox must not be null");
            Role role = roleProposal.getRole();
            bondedRoleTypeComboBox.getSelectionModel().select(role.getBondedRoleType());
            comboBoxValueTextField.setText(bondedRoleTypeComboBox.getConverter().toString(role.getBondedRoleType()));
            requiredBondForRoleTextField.setText(bsqFormatter.formatCoin(Coin.valueOf(daoFacade.getRequiredBond(roleProposal))));
            // TODO maybe show also unlock time?
        } else if (proposal instanceof ConfiscateBondProposal) {
            ConfiscateBondProposal confiscateBondProposal = (ConfiscateBondProposal) proposal;
            checkNotNull(confiscateBondComboBox, "confiscateBondComboBox must not be null");
            daoFacade.getBondByLockupTxId(confiscateBondProposal.getLockupTxId())
                    .ifPresent(bond -> {
                        confiscateBondComboBox.getSelectionModel().select(bond);
                        comboBoxValueTextField.setText(confiscateBondComboBox.getConverter().toString(bond));
                        comboBoxValueTextField.setOnMouseClicked(e -> {
                            navigateHandlerOptional.ifPresent(Runnable::run);
                            navigation.navigateToWithData(bond, MainView.class, DaoView.class, BondingView.class,
                                    BondsView.class);
                        });

                        comboBoxValueTextField.getStyleClass().addAll("hyperlink", "force-underline", "show-hand");
                    });
        } else if (proposal instanceof GenericProposal) {
            // do nothing
        } else if (proposal instanceof RemoveAssetProposal) {
            RemoveAssetProposal removeAssetProposal = (RemoveAssetProposal) proposal;
            checkNotNull(assetComboBox, "assetComboBox must not be null");
            CurrencyUtil.findAsset(removeAssetProposal.getTickerSymbol(), BaseCurrencyNetwork.BTC_MAINNET)
                    .ifPresent(asset -> {
                        assetComboBox.getSelectionModel().select(asset);
                        comboBoxValueTextField.setText(assetComboBox.getConverter().toString(asset));
                    });
        }
        int chainHeight = daoFacade.getTx(proposal.getTxId()).map(Tx::getBlockHeight).orElse(daoFacade.getChainHeight());
        if (proposalFeeTextField != null)
            proposalFeeTextField.setText(bsqFormatter.formatCoinWithCode(daoFacade.getProposalFee(chainHeight)));
    }

    private void addListeners() {
        inputControls.stream()
                .filter(Objects::nonNull).forEach(inputControl -> {
            inputControl.textProperty().addListener(inputListener);
            inputControl.focusedProperty().addListener(focusOutListener);
        });
        comboBoxes.stream()
                .filter(Objects::nonNull)
                .forEach(comboBox -> comboBox.getSelectionModel().selectedItemProperty().addListener(inputListener));
    }

    public void removeListeners() {
        inputControls.stream()
                .filter(Objects::nonNull).forEach(inputControl -> {
            inputControl.textProperty().removeListener(inputListener);
            inputControl.focusedProperty().removeListener(focusOutListener);
        });
        comboBoxes.stream()
                .filter(Objects::nonNull)
                .forEach(comboBox -> comboBox.getSelectionModel().selectedItemProperty().removeListener(inputListener));

        if (paramComboBox != null && paramChangeListener != null)
            paramComboBox.getSelectionModel().selectedItemProperty().removeListener(paramChangeListener);

        if (bondedRoleTypeComboBox != null && requiredBondForRoleListener != null)
            bondedRoleTypeComboBox.getSelectionModel().selectedItemProperty().removeListener(requiredBondForRoleListener);
    }

    public void clearForm() {
        inputControls.stream().filter(Objects::nonNull).forEach(TextInputControl::clear);

        if (linkHyperlinkWithIcon != null)
            linkHyperlinkWithIcon.clear();

        comboBoxes.stream().filter(Objects::nonNull).forEach(comboBox -> comboBox.getSelectionModel().clearSelection());
    }

    public void setEditable(boolean isEditable) {
        inputControls.stream().filter(Objects::nonNull).forEach(e -> e.setEditable(isEditable));
        comboBoxes.stream().filter(Objects::nonNull).forEach(comboBox -> {
            comboBox.setVisible(isEditable);
            comboBox.setManaged(isEditable);

            if (comboBoxValueContainer != null) {
                comboBoxValueContainer.setVisible(!isEditable);
                comboBoxValueContainer.setManaged(!isEditable);
            }
        });

        linkInputTextField.setVisible(true);
        linkInputTextField.setManaged(true);

        if (linkWithIconContainer != null) {
            linkWithIconContainer.setVisible(false);
            linkWithIconContainer.setManaged(false);
            linkHyperlinkWithIcon.setOnAction(null);
        }
    }

    public void removeAllFields() {
        if (gridRow > 0) {
            clearForm();
            GUIUtil.removeChildrenFromGridPaneRows(gridPane, gridRowStartIndex, gridRow);
            gridRow = gridRowStartIndex;
        }

        if (linkHyperlinkWithIcon != null)
            linkHyperlinkWithIcon.prefWidthProperty().unbind();

        inputControls.clear();
        comboBoxes.clear();
    }

    public void onNavigate(Runnable navigateHandler) {
        navigateHandlerOptional = Optional.of(navigateHandler);
    }

    public int incrementAndGetGridRow() {
        return ++gridRow;
    }

    @SuppressWarnings("Duplicates")
    public ScrollPane getView() {
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        AnchorPane anchorPane = new AnchorPane();
        scrollPane.setContent(anchorPane);

        gridPane.setHgap(5);
        gridPane.setVgap(5);

        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setPercentWidth(100);

        gridPane.getColumnConstraints().addAll(columnConstraints1);

        AnchorPane.setBottomAnchor(gridPane, 10d);
        AnchorPane.setRightAnchor(gridPane, 10d);
        AnchorPane.setLeftAnchor(gridPane, 10d);
        AnchorPane.setTopAnchor(gridPane, 10d);
        anchorPane.getChildren().add(gridPane);

        return scrollPane;
    }

    private void setMyVoteBoxVisibility(boolean visibility) {
        myVoteTitledGroup.setVisible(visibility);
        myVoteTitledGroup.setManaged(visibility);
        myVoteBox.setVisible(visibility);
        myVoteBox.setManaged(visibility);
    }
}
