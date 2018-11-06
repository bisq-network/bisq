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

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.dao.governance.PhasesView;
import bisq.desktop.main.dao.governance.ProposalDisplay;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.btc.exceptions.InsufficientBsqException;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.exceptions.ValidationException;
import bisq.core.dao.governance.bond.Bond;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.proposal.ProposalType;
import bisq.core.dao.governance.proposal.ProposalWithTransaction;
import bisq.core.dao.governance.proposal.TxException;
import bisq.core.dao.governance.proposal.param.ChangeParamValidator;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.locale.Res;
import bisq.core.util.BSFormatter;
import bisq.core.util.BsqFormatter;

import bisq.asset.Asset;

import bisq.network.p2p.P2PService;

import bisq.common.app.DevEnv;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.GridPane;

import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;

import javafx.util.StringConverter;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static com.google.common.base.Preconditions.checkNotNull;

@FxmlView
public class MakeProposalView extends ActivatableView<GridPane, Void> implements DaoStateListener {
    private final DaoFacade daoFacade;
    private final BsqWalletService bsqWalletService;
    private final WalletsSetup walletsSetup;
    private final P2PService p2PService;
    private final PhasesView phasesView;
    private final ChangeParamValidator changeParamValidator;
    private final BSFormatter btcFormatter;
    private final BsqFormatter bsqFormatter;

    private ProposalDisplay proposalDisplay;
    private Button makeProposalButton;
    private ComboBox<ProposalType> proposalTypeComboBox;
    private ChangeListener<ProposalType> proposalTypeChangeListener;
    @Nullable
    private ProposalType selectedProposalType;
    private int gridRow;
    private int alwaysVisibleGridRowIndex;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private MakeProposalView(DaoFacade daoFacade,
                             BsqWalletService bsqWalletService,
                             WalletsSetup walletsSetup,
                             P2PService p2PService,
                             PhasesView phasesView,
                             ChangeParamValidator changeParamValidator,
                             BSFormatter btcFormatter,
                             BsqFormatter bsqFormatter) {
        this.daoFacade = daoFacade;
        this.bsqWalletService = bsqWalletService;
        this.walletsSetup = walletsSetup;
        this.p2PService = p2PService;
        this.phasesView = phasesView;
        this.changeParamValidator = changeParamValidator;
        this.btcFormatter = btcFormatter;
        this.bsqFormatter = bsqFormatter;
    }


    @Override
    public void initialize() {
        gridRow = phasesView.addGroup(root, gridRow);

        addTitledGroupBg(root, ++gridRow, 1, Res.get("dao.proposal.create.selectProposalType"), Layout.GROUP_DISTANCE);
        proposalTypeComboBox = FormBuilder.<ProposalType>addComboBox(root, gridRow,
                Res.getWithCol("dao.proposal.create.proposalType"), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
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

        List<ProposalType> proposalTypes = Arrays.asList(ProposalType.values());
        proposalTypeComboBox.setItems(FXCollections.observableArrayList(proposalTypes));
    }

    @Override
    protected void activate() {
        phasesView.activate();

        daoFacade.addBsqStateListener(this);

        proposalTypeComboBox.getSelectionModel().selectedItemProperty().addListener(proposalTypeChangeListener);
        if (makeProposalButton != null)
            setMakeProposalButtonHandler();

        onNewBlockHeight(daoFacade.getChainHeight());
    }

    @Override
    protected void deactivate() {
        phasesView.deactivate();

        daoFacade.removeBsqStateListener(this);

        proposalTypeComboBox.getSelectionModel().selectedItemProperty().removeListener(proposalTypeChangeListener);
        if (makeProposalButton != null)
            makeProposalButton.setOnAction(null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int height) {
        boolean isProposalPhase = daoFacade.isInPhaseButNotLastBlock(DaoPhase.Phase.PROPOSAL);
        proposalTypeComboBox.setDisable(!isProposalPhase);
        if (!isProposalPhase)
            proposalTypeComboBox.getSelectionModel().clearSelection();
    }

    @Override
    public void onParseTxsComplete(Block block) {
    }

    @Override
    public void onParseBlockChainComplete() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void publishMyProposal(ProposalType type) {
        try {
            ProposalWithTransaction proposalWithTransaction = getProposalWithTransaction(type);
            if (proposalWithTransaction == null)
                return;

            Proposal proposal = proposalWithTransaction.getProposal();
            Transaction transaction = proposalWithTransaction.getTransaction();
            Coin miningFee = transaction.getFee();
            int txSize = transaction.bitcoinSerialize().length;
            Coin fee = daoFacade.getProposalFee(daoFacade.getChainHeight());
            GUIUtil.showBsqFeeInfoPopup(fee, miningFee, txSize, bsqFormatter, btcFormatter,
                    Res.get("dao.proposal"), () -> doPublishMyProposal(proposal, transaction));

        } catch (InsufficientMoneyException e) {
            BSFormatter formatter = e instanceof InsufficientBsqException ? bsqFormatter : btcFormatter;
            new Popup<>().warning(Res.get("dao.proposal.create.missingFunds",
                    formatter.formatCoinWithCode(e.missing))).show();
        } catch (ValidationException e) {
            String message;
            if (e.getMinRequestAmount() != null) {
                message = Res.get("validation.bsq.amountBelowMinAmount",
                        bsqFormatter.formatCoinWithCode(e.getMinRequestAmount()));
            } else {
                message = e.getMessage();
            }
            new Popup<>().warning(message).show();
        } catch (TxException e) {
            log.error(e.toString());
            e.printStackTrace();
            new Popup<>().warning(e.toString()).show();
        }
    }

    private void doPublishMyProposal(Proposal proposal, Transaction transaction) {
        daoFacade.publishMyProposal(proposal,
                transaction,
                () -> {
                    proposalDisplay.clearForm();
                    proposalTypeComboBox.getSelectionModel().clearSelection();
                    if (!DevEnv.isDevMode())
                        new Popup<>().confirmation(Res.get("dao.tx.published.success")).show();
                },
                errorMessage -> new Popup<>().warning(errorMessage).show());
    }

    @Nullable
    private ProposalWithTransaction getProposalWithTransaction(ProposalType type)
            throws InsufficientMoneyException, ValidationException, TxException {

        switch (type) {
            case COMPENSATION_REQUEST:
                checkNotNull(proposalDisplay.requestedBsqTextField,
                        "proposalDisplay.requestedBsqTextField must not be null");
                return daoFacade.getCompensationProposalWithTransaction(proposalDisplay.nameTextField.getText(),
                        proposalDisplay.linkInputTextField.getText(),
                        bsqFormatter.parseToCoin(proposalDisplay.requestedBsqTextField.getText()));
            case REIMBURSEMENT_REQUEST:
                checkNotNull(proposalDisplay.requestedBsqTextField,
                        "proposalDisplay.requestedBsqTextField must not be null");
                return daoFacade.getReimbursementProposalWithTransaction(proposalDisplay.nameTextField.getText(),
                        proposalDisplay.linkInputTextField.getText(),
                        bsqFormatter.parseToCoin(proposalDisplay.requestedBsqTextField.getText()));
            case CHANGE_PARAM:
                checkNotNull(proposalDisplay.paramComboBox,
                        "proposalDisplay.paramComboBox must no tbe null");
                checkNotNull(proposalDisplay.paramValueTextField,
                        "proposalDisplay.paramValueTextField must no tbe null");
                Param selectedParam = proposalDisplay.paramComboBox.getSelectionModel().getSelectedItem();
                if (selectedParam == null)
                    throw new ValidationException("selectedParam is null");
                String paramValueAsString = proposalDisplay.paramValueTextField.getText();
                if (paramValueAsString == null || paramValueAsString.isEmpty())
                    throw new ValidationException("paramValue is null or empty");

                try {
                    String paramValue = bsqFormatter.parseParamValueToString(selectedParam, paramValueAsString);
                    proposalDisplay.paramValueTextField.setText(paramValue);
                    log.info("Change param: paramValue={}, paramValueAsString={}", paramValue, paramValueAsString);

                    changeParamValidator.validateParamValue(selectedParam, paramValue);
                    return daoFacade.getParamProposalWithTransaction(proposalDisplay.nameTextField.getText(),
                            proposalDisplay.linkInputTextField.getText(),
                            selectedParam,
                            paramValue);
                } catch (Throwable e) {
                    new Popup<>().warning(e.getMessage()).show();
                    return null;
                }
            case BONDED_ROLE:
                checkNotNull(proposalDisplay.bondedRoleTypeComboBox,
                        "proposalDisplay.bondedRoleTypeComboBox must not be null");
                Role role = new Role(proposalDisplay.nameTextField.getText(),
                        proposalDisplay.linkInputTextField.getText(),
                        proposalDisplay.bondedRoleTypeComboBox.getSelectionModel().getSelectedItem());
                return daoFacade.getBondedRoleProposalWithTransaction(role);
            case CONFISCATE_BOND:
                checkNotNull(proposalDisplay.confiscateBondComboBox,
                        "proposalDisplay.confiscateBondComboBox must not be null");
                Bond bond = proposalDisplay.confiscateBondComboBox.getSelectionModel().getSelectedItem();
                return daoFacade.getConfiscateBondProposalWithTransaction(proposalDisplay.nameTextField.getText(),
                        proposalDisplay.linkInputTextField.getText(),
                        bond.getLockupTxId());
            case GENERIC:
                return daoFacade.getGenericProposalWithTransaction(proposalDisplay.nameTextField.getText(),
                        proposalDisplay.linkInputTextField.getText());
            case REMOVE_ASSET:
                checkNotNull(proposalDisplay.assetComboBox,
                        "proposalDisplay.assetComboBox must not be null");
                Asset asset = proposalDisplay.assetComboBox.getSelectionModel().getSelectedItem();
                return daoFacade.getRemoveAssetProposalWithTransaction(proposalDisplay.nameTextField.getText(),
                        proposalDisplay.linkInputTextField.getText(),
                        asset);
            default:
                final String msg = "Undefined ProposalType " + selectedProposalType;
                log.error(msg);
                throw new RuntimeException(msg);
        }
    }

    private void addProposalDisplay() {
        if (selectedProposalType != null) {
            proposalDisplay = new ProposalDisplay(root, bsqFormatter, daoFacade, changeParamValidator);
            proposalDisplay.createAllFields(Res.get("dao.proposal.create.createNew"), alwaysVisibleGridRowIndex, Layout.GROUP_DISTANCE,
                    selectedProposalType, true);

            makeProposalButton = addButtonAfterGroup(root, proposalDisplay.incrementAndGetGridRow(), Res.get("dao.proposal.create.create.button"));
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
            if (GUIUtil.isReadyForTxBroadcast(p2PService, walletsSetup)) {
                publishMyProposal(selectedProposalType);
            } else {
                GUIUtil.showNotReadyForTxBroadcastPopups(p2PService, walletsSetup);
            }
        });
    }

    private void updateButtonState() {
        AtomicBoolean inputsValid = new AtomicBoolean(true);
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
                .filter(Objects::nonNull).forEach(comboBox -> {
            inputsValid.set(inputsValid.get() && comboBox.getSelectionModel().getSelectedItem() != null);
        });

        makeProposalButton.setDisable(!inputsValid.get());
    }
}

