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

package bisq.desktop.main.dao.governance.make;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.dao.governance.PhasesView;
import bisq.desktop.main.dao.governance.ProposalDisplay;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.btc.exceptions.InsufficientBsqException;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.bond.Bond;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.proposal.IssuanceProposal;
import bisq.core.dao.governance.proposal.ProposalType;
import bisq.core.dao.governance.proposal.ProposalValidationException;
import bisq.core.dao.governance.proposal.ProposalWithTransaction;
import bisq.core.dao.governance.proposal.TxException;
import bisq.core.dao.governance.proposal.param.ChangeParamValidator;
import bisq.core.dao.governance.voteresult.VoteResultException;
import bisq.core.dao.presentation.DaoUtil;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.governance.BondedRoleType;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.locale.Res;
import bisq.core.util.FormattingUtils;
import bisq.core.util.ParsingUtils;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import bisq.asset.Asset;

import bisq.network.p2p.P2PService;

import bisq.common.app.DevEnv;
import bisq.common.util.Tuple3;
import bisq.common.util.Tuple4;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;

import javafx.util.StringConverter;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.addButtonBusyAnimationLabelAfterGroup;
import static bisq.desktop.util.FormBuilder.addComboBox;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static bisq.desktop.util.FormBuilder.addTopLabelReadOnlyTextField;
import static com.google.common.base.Preconditions.checkNotNull;

@FxmlView
public class MakeProposalView extends ActivatableView<GridPane, Void> implements DaoStateListener {
    private final DaoFacade daoFacade;
    private final WalletsSetup walletsSetup;
    private final P2PService p2PService;
    private final PhasesView phasesView;
    private final ChangeParamValidator changeParamValidator;
    private final CoinFormatter btcFormatter;
    private final BsqFormatter bsqFormatter;
    private final Navigation navigation;
    private final BsqWalletService bsqWalletService;

    @Nullable
    private ProposalDisplay proposalDisplay;
    private Button makeProposalButton;
    private ComboBox<ProposalType> proposalTypeComboBox;
    private ChangeListener<ProposalType> proposalTypeChangeListener;
    private TextField nextProposalTextField;
    private TitledGroupBg proposalTitledGroup;
    private VBox nextProposalBox;
    private BusyAnimation busyAnimation;
    private Label busyLabel;

    private final BooleanProperty isProposalPhase = new SimpleBooleanProperty(false);
    private final StringProperty proposalGroupTitle = new SimpleStringProperty(Res.get("dao.proposal.create.phase.inactive"));

    @Nullable
    private ProposalType selectedProposalType;
    private int gridRow;
    private int alwaysVisibleGridRowIndex;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private MakeProposalView(DaoFacade daoFacade,
                             WalletsSetup walletsSetup,
                             P2PService p2PService,
                             BsqWalletService bsqWalletService,
                             PhasesView phasesView,
                             ChangeParamValidator changeParamValidator,
                             @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                             BsqFormatter bsqFormatter,
                             Navigation navigation) {
        this.daoFacade = daoFacade;
        this.walletsSetup = walletsSetup;
        this.p2PService = p2PService;
        this.bsqWalletService = bsqWalletService;
        this.phasesView = phasesView;
        this.changeParamValidator = changeParamValidator;
        this.btcFormatter = btcFormatter;
        this.bsqFormatter = bsqFormatter;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        gridRow = phasesView.addGroup(root, gridRow);

        proposalTitledGroup = addTitledGroupBg(root, ++gridRow, 2, proposalGroupTitle.get(), Layout.GROUP_DISTANCE);
        proposalTitledGroup.getStyleClass().add("last");
        final Tuple3<Label, TextField, VBox> nextProposalPhaseTuple = addTopLabelReadOnlyTextField(root, gridRow,
                Res.get("dao.cycle.proposal.next"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        nextProposalBox = nextProposalPhaseTuple.third;
        nextProposalTextField = nextProposalPhaseTuple.second;
        proposalTypeComboBox = addComboBox(root, gridRow,
                Res.get("dao.proposal.create.proposalType"), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        proposalTypeComboBox.setMaxWidth(300);
        proposalTypeComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(ProposalType proposalType) {
                return proposalType.getDisplayName();
            }

            @Override
            public ProposalType fromString(String string) {
                return null;
            }
        });
        proposalTypeChangeListener = (observable, oldValue, newValue) -> {
            selectedProposalType = newValue;
            removeProposalDisplay();
            addProposalDisplay();
        };
        alwaysVisibleGridRowIndex = gridRow + 1;

        List<ProposalType> proposalTypes = Arrays.stream(ProposalType.values())
                .filter(e -> e != ProposalType.UNDEFINED)
                .collect(Collectors.toList());
        proposalTypeComboBox.setItems(FXCollections.observableArrayList(proposalTypes));
    }

    @Override
    protected void activate() {
        addBindings();

        phasesView.activate();

        daoFacade.addBsqStateListener(this);

        proposalTypeComboBox.getSelectionModel().selectedItemProperty().addListener(proposalTypeChangeListener);
        if (makeProposalButton != null)
            setMakeProposalButtonHandler();

        Optional<Block> blockAtChainHeight = daoFacade.getBlockAtChainHeight();

        blockAtChainHeight.ifPresent(this::onParseBlockCompleteAfterBatchProcessing);
    }

    @Override
    protected void deactivate() {
        removeBindings();

        phasesView.deactivate();

        daoFacade.removeBsqStateListener(this);

        proposalTypeComboBox.getSelectionModel().selectedItemProperty().removeListener(proposalTypeChangeListener);
        if (makeProposalButton != null)
            makeProposalButton.setOnAction(null);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBindings() {
        proposalTypeComboBox.managedProperty().bind(isProposalPhase);
        proposalTypeComboBox.visibleProperty().bind(isProposalPhase);
        nextProposalBox.managedProperty().bind(isProposalPhase.not());
        nextProposalBox.visibleProperty().bind(isProposalPhase.not());
        proposalTitledGroup.textProperty().bind(proposalGroupTitle);
    }

    private void removeBindings() {
        proposalTypeComboBox.managedProperty().unbind();
        proposalTypeComboBox.visibleProperty().unbind();
        nextProposalBox.managedProperty().unbind();
        nextProposalBox.visibleProperty().unbind();
        proposalTitledGroup.textProperty().unbind();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        isProposalPhase.set(daoFacade.isInPhaseButNotLastBlock(DaoPhase.Phase.PROPOSAL));
        if (isProposalPhase.get()) {
            proposalGroupTitle.set(Res.get("dao.proposal.create.selectProposalType"));
        } else {
            proposalGroupTitle.set(Res.get("dao.proposal.create.phase.inactive"));
            proposalTypeComboBox.getSelectionModel().clearSelection();
            updateTimeUntilNextProposalPhase(block.getHeight());
        }

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateTimeUntilNextProposalPhase(int height) {
        nextProposalTextField.setText(DaoUtil.getNextPhaseDuration(height, DaoPhase.Phase.PROPOSAL, daoFacade));
    }

    private void publishMyProposal(ProposalType type) {
        try {
            ProposalWithTransaction proposalWithTransaction = getProposalWithTransaction(type);
            if (proposalWithTransaction == null)
                return;

            Proposal proposal = proposalWithTransaction.getProposal();
            Transaction transaction = proposalWithTransaction.getTransaction();
            Coin miningFee = transaction.getFee();
            int txVsize = transaction.getVsize();
            Coin fee = daoFacade.getProposalFee(daoFacade.getChainHeight());

            if (type.equals(ProposalType.BONDED_ROLE)) {
                checkNotNull(proposalDisplay, "proposalDisplay must not be null");
                checkNotNull(proposalDisplay.bondedRoleTypeComboBox, "proposalDisplay.bondedRoleTypeComboBox must not be null");
                BondedRoleType bondedRoleType = proposalDisplay.bondedRoleTypeComboBox.getSelectionModel().getSelectedItem();
                long requiredBond = daoFacade.getRequiredBond(bondedRoleType);
                long availableBalance = bsqWalletService.getAvailableBalance().value;

                if (requiredBond > availableBalance) {
                    long missing = requiredBond - availableBalance;
                    new Popup().warning(Res.get("dao.proposal.create.missingBsqFundsForBond",
                            bsqFormatter.formatCoinWithCode(missing)))
                            .actionButtonText(Res.get("dao.proposal.create.publish"))
                            .onAction(() -> showFeeInfoAndPublishMyProposal(proposal, transaction, miningFee, txVsize, fee))
                            .show();
                } else {
                    showFeeInfoAndPublishMyProposal(proposal, transaction, miningFee, txVsize, fee);
                }
            } else {
                showFeeInfoAndPublishMyProposal(proposal, transaction, miningFee, txVsize, fee);
            }
        } catch (InsufficientMoneyException e) {
            if (e instanceof InsufficientBsqException) {
                new Popup().warning(Res.get("dao.proposal.create.missingBsqFunds",
                        bsqFormatter.formatCoinWithCode(e.missing))).show();
            } else {
                if (type.equals(ProposalType.COMPENSATION_REQUEST) || type.equals(ProposalType.REIMBURSEMENT_REQUEST)) {
                    new Popup().warning(Res.get("dao.proposal.create.missingIssuanceFunds",
                            100,
                            btcFormatter.formatCoinWithCode(e.missing))).show();
                } else {
                    new Popup().warning(Res.get("dao.proposal.create.missingMinerFeeFunds",
                            btcFormatter.formatCoinWithCode(e.missing))).show();
                }
            }
        } catch (ProposalValidationException e) {
            String message;
            if (e.getMinRequestAmount() != null) {
                message = Res.get("validation.bsq.amountBelowMinAmount",
                        bsqFormatter.formatCoinWithCode(e.getMinRequestAmount()));
            } else {
                message = e.getMessage();
            }
            new Popup().warning(message).show();
        } catch (IllegalArgumentException e) {
            log.error(e.toString());
            e.printStackTrace();
            new Popup().warning(e.getMessage()).show();
        } catch (Throwable e) {
            log.error(e.toString());
            e.printStackTrace();
            new Popup().warning(e.toString()).show();
        }
    }

    private void showFeeInfoAndPublishMyProposal(Proposal proposal, Transaction transaction, Coin miningFee, int txVsize, Coin fee) {
        if (!DevEnv.isDevMode()) {
            Coin btcForIssuance = null;

            if (proposal instanceof IssuanceProposal) btcForIssuance = ((IssuanceProposal) proposal).getRequestedBsq();

            GUIUtil.showBsqFeeInfoPopup(fee, miningFee, btcForIssuance, txVsize, bsqFormatter, btcFormatter,
                    Res.get("dao.proposal"), () -> doPublishMyProposal(proposal, transaction));
        } else {
            doPublishMyProposal(proposal, transaction);
        }
    }

    private void doPublishMyProposal(Proposal proposal, Transaction transaction) {
        //TODO it still happens that the user can click twice. Not clear why that can happen. Maybe we get updateButtonState
        // called in between which re-enables the button?
        makeProposalButton.setDisable(true);
        busyLabel.setVisible(true);
        busyAnimation.play();

        daoFacade.publishMyProposal(proposal,
                transaction,
                () -> {
                    if (!DevEnv.isDevMode())
                        new Popup().feedback(Res.get("dao.tx.published.success")).show();

                    if (proposalDisplay != null)
                        proposalDisplay.clearForm();
                    proposalTypeComboBox.getSelectionModel().clearSelection();
                    busyAnimation.stop();
                    busyLabel.setVisible(false);
                    makeProposalButton.setDisable(false);
                },
                errorMessage -> {
                    new Popup().warning(errorMessage).show();
                    busyAnimation.stop();
                    busyLabel.setVisible(false);
                    makeProposalButton.setDisable(false);
                });

    }

    @Nullable
    private ProposalWithTransaction getProposalWithTransaction(ProposalType proposalType)
            throws InsufficientMoneyException, ProposalValidationException, TxException, VoteResultException.ValidationException {

        checkNotNull(proposalDisplay, "proposalDisplay must not be null");

        String link = proposalDisplay.linkInputTextField.getText();
        String name = proposalDisplay.nameTextField.getText();
        switch (proposalType) {
            case COMPENSATION_REQUEST:
                checkNotNull(proposalDisplay.requestedBsqTextField,
                        "proposalDisplay.requestedBsqTextField must not be null");
                return daoFacade.getCompensationProposalWithTransaction(name,
                        link,
                        ParsingUtils.parseToCoin(proposalDisplay.requestedBsqTextField.getText(), bsqFormatter));
            case REIMBURSEMENT_REQUEST:
                checkNotNull(proposalDisplay.requestedBsqTextField,
                        "proposalDisplay.requestedBsqTextField must not be null");
                return daoFacade.getReimbursementProposalWithTransaction(name,
                        link,
                        ParsingUtils.parseToCoin(proposalDisplay.requestedBsqTextField.getText(), bsqFormatter));
            case CHANGE_PARAM:
                checkNotNull(proposalDisplay.paramComboBox,
                        "proposalDisplay.paramComboBox must not be null");
                checkNotNull(proposalDisplay.paramValueTextField,
                        "proposalDisplay.paramValueTextField must not be null");
                Param selectedParam = proposalDisplay.paramComboBox.getSelectionModel().getSelectedItem();
                if (selectedParam == null)
                    throw new ProposalValidationException("selectedParam is null");
                String paramValueAsString = proposalDisplay.paramValueTextField.getText();
                if (paramValueAsString == null || paramValueAsString.isEmpty())
                    throw new ProposalValidationException("paramValue is null or empty");

                try {
                    String paramValue = bsqFormatter.parseParamValueToString(selectedParam, paramValueAsString);
                    proposalDisplay.paramValueTextField.setText(paramValue);
                    log.info("Change param: paramValue={}, paramValueAsString={}", paramValue, paramValueAsString);

                    changeParamValidator.validateParamValue(selectedParam, paramValue);
                    return daoFacade.getParamProposalWithTransaction(name,
                            link,
                            selectedParam,
                            paramValue);
                } catch (Throwable e) {
                    new Popup().warning(e.getMessage()).show();
                    return null;
                }
            case BONDED_ROLE:
                checkNotNull(proposalDisplay.bondedRoleTypeComboBox,
                        "proposalDisplay.bondedRoleTypeComboBox must not be null");
                Role role = new Role(name,
                        link,
                        proposalDisplay.bondedRoleTypeComboBox.getSelectionModel().getSelectedItem());
                return daoFacade.getBondedRoleProposalWithTransaction(role);
            case CONFISCATE_BOND:
                checkNotNull(proposalDisplay.confiscateBondComboBox,
                        "proposalDisplay.confiscateBondComboBox must not be null");
                Bond bond = proposalDisplay.confiscateBondComboBox.getSelectionModel().getSelectedItem();

                if (!bond.isActive())
                    throw new VoteResultException.ValidationException("Bond is not locked and can't be confiscated");

                return daoFacade.getConfiscateBondProposalWithTransaction(name, link, bond.getLockupTxId());
            case GENERIC:
                return daoFacade.getGenericProposalWithTransaction(name, link);
            case REMOVE_ASSET:
                checkNotNull(proposalDisplay.assetComboBox,
                        "proposalDisplay.assetComboBox must not be null");
                Asset asset = proposalDisplay.assetComboBox.getSelectionModel().getSelectedItem();
                return daoFacade.getRemoveAssetProposalWithTransaction(name, link, asset);
            default:
                final String msg = "Undefined ProposalType " + selectedProposalType;
                log.error(msg);
                throw new RuntimeException(msg);
        }
    }

    private void addProposalDisplay() {
        if (selectedProposalType != null) {
            proposalDisplay = new ProposalDisplay(root, bsqFormatter, daoFacade, changeParamValidator, navigation, null);

            proposalDisplay.createAllFields(Res.get("dao.proposal.create.new"), alwaysVisibleGridRowIndex, Layout.GROUP_DISTANCE_WITHOUT_SEPARATOR,
                    selectedProposalType, true);

            final Tuple4<Button, BusyAnimation, Label, HBox> makeProposalTuple = addButtonBusyAnimationLabelAfterGroup(root,
                    proposalDisplay.getGridRow(), 0, Res.get("dao.proposal.create.button"));
            makeProposalButton = makeProposalTuple.first;

            busyAnimation = makeProposalTuple.second;
            busyLabel = makeProposalTuple.third;
            busyLabel.setVisible(false);
            busyLabel.setText(Res.get("dao.proposal.create.publishing"));

            setMakeProposalButtonHandler();
            proposalDisplay.addInputChangedListener(this::updateButtonState);
            updateButtonState();
        }
    }

    private void removeProposalDisplay() {
        if (proposalDisplay != null) {
            proposalDisplay.removeAllFields();
            GUIUtil.removeChildrenFromGridPaneRows(root, alwaysVisibleGridRowIndex, proposalDisplay.getGridRow());
            proposalDisplay.removeInputChangedListener(this::updateButtonState);
            proposalDisplay.removeListeners();
            proposalDisplay = null;
        }
    }

    private void setMakeProposalButtonHandler() {
        makeProposalButton.setOnAction(event -> {
            if (GUIUtil.isReadyForTxBroadcastOrShowPopup(p2PService, walletsSetup)) {
                publishMyProposal(selectedProposalType);
            }
        });
    }

    private void updateButtonState() {
        AtomicBoolean inputsValid = new AtomicBoolean(true);
        if (proposalDisplay != null) {
            proposalDisplay.getInputControls().stream()
                    .filter(Objects::nonNull).forEach(e -> {
                if (e instanceof InputTextField) {
                    InputTextField inputTextField = (InputTextField) e;
                    inputsValid.set(inputsValid.get() &&
                            inputTextField.getValidator() != null &&
                            inputTextField.getValidator().validate(e.getText()).isValid);
                }
            });
            proposalDisplay.getComboBoxes().stream()
                    .filter(Objects::nonNull).forEach(comboBox -> inputsValid.set(inputsValid.get() &&
                    comboBox.getSelectionModel().getSelectedItem() != null));

            InputTextField linkInputTextField = proposalDisplay.linkInputTextField;
            inputsValid.set(inputsValid.get() &&
                    linkInputTextField.getValidator().validate(linkInputTextField.getText()).isValid);
        }

        makeProposalButton.setDisable(!inputsValid.get());
    }
}
