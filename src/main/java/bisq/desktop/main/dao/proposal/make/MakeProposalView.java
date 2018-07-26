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

package bisq.desktop.main.dao.proposal.make;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.main.dao.proposal.ProposalDisplay;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.InsufficientBsqException;
import bisq.core.btc.wallet.WalletsSetup;
import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.param.DaoParamService;
import bisq.core.dao.vote.proposal.Proposal;
import bisq.core.dao.vote.proposal.ProposalConsensus;
import bisq.core.dao.vote.proposal.ProposalService;
import bisq.core.dao.vote.proposal.ProposalType;
import bisq.core.dao.vote.proposal.ValidationException;
import bisq.core.dao.vote.proposal.compensation.CompensationRequestPayload;
import bisq.core.dao.vote.proposal.compensation.CompensationRequestService;
import bisq.core.dao.vote.proposal.generic.GenericProposalPayload;
import bisq.core.dao.vote.proposal.generic.GenericProposalService;
import bisq.core.locale.Res;
import bisq.core.provider.fee.FeeService;
import bisq.core.util.BSFormatter;
import bisq.core.util.BsqFormatter;
import bisq.core.util.CoinUtil;

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

import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static com.google.common.base.Preconditions.checkArgument;

@FxmlView
public class MakeProposalView extends ActivatableView<GridPane, Void> {

    private ProposalDisplay proposalDisplay;
    private Button createButton;

    private final BsqWalletService bsqWalletService;
    private final WalletsSetup walletsSetup;
    private final P2PService p2PService;
    private final FeeService feeService;
    private final ProposalService proposalService;
    private final CompensationRequestService compensationRequestService;
    private final GenericProposalService genericProposalService;
    private final ReadableBsqBlockChain readableBsqBlockChain;
    private final DaoParamService daoParamService;
    private final BSFormatter btcFormatter;
    private final BsqFormatter bsqFormatter;
    private ComboBox<ProposalType> proposalTypeComboBox;
    private ChangeListener<ProposalType> proposalTypeChangeListener;
    private ProposalType selectedProposalType;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private MakeProposalView(BsqWalletService bsqWalletService,
                             WalletsSetup walletsSetup,
                             P2PService p2PService,
                             FeeService feeService,
                             ProposalService proposalService,
                             CompensationRequestService compensationRequestService,
                             GenericProposalService genericProposalService,
                             ReadableBsqBlockChain readableBsqBlockChain,
                             DaoParamService daoParamService,
                             BSFormatter btcFormatter,
                             BsqFormatter bsqFormatter) {
        this.bsqWalletService = bsqWalletService;
        this.walletsSetup = walletsSetup;
        this.p2PService = p2PService;
        this.feeService = feeService;
        this.proposalService = proposalService;
        this.compensationRequestService = compensationRequestService;
        this.genericProposalService = genericProposalService;
        this.readableBsqBlockChain = readableBsqBlockChain;
        this.daoParamService = daoParamService;
        this.btcFormatter = btcFormatter;
        this.bsqFormatter = bsqFormatter;
    }


    @Override
    public void initialize() {
        addTitledGroupBg(root, 0, 1, Res.get("dao.proposal.create.selectProposalType"));
        proposalTypeComboBox = FormBuilder.<ProposalType>addLabelComboBox(root, 0,
                Res.getWithCol("dao.proposal.create.proposalType"), Layout.FIRST_ROW_DISTANCE).second;
        proposalTypeComboBox.setConverter(new StringConverter<ProposalType>() {
            @Override
            public String toString(ProposalType object) {
                return Res.get(object.name());
            }

            @Override
            public ProposalType fromString(String string) {
                return null;
            }
        });
        proposalTypeComboBox.setPromptText(Res.get("shared.select"));
        proposalTypeChangeListener = (observable, oldValue, newValue) -> {
            selectedProposalType = newValue;
            addProposalDisplay();
        };

        proposalTypeComboBox.setItems(FXCollections.observableArrayList(Arrays.asList(ProposalType.values())));
    }

    @Override
    protected void activate() {
        proposalTypeComboBox.getSelectionModel().selectedItemProperty().addListener(proposalTypeChangeListener);

        if (createButton != null)
            setCreateButtonHandler();
    }

    @Override
    protected void deactivate() {
        proposalTypeComboBox.getSelectionModel().selectedItemProperty().removeListener(proposalTypeChangeListener);
        if (createButton != null)
            createButton.setOnAction(null);
    }

    private void publishProposal(ProposalType type) {
        try {
            Proposal proposal = createProposal(type);
            Transaction tx = Objects.requireNonNull(proposal).getTx();
            Coin miningFee = Objects.requireNonNull(tx).getFee();
            int txSize = tx.bitcoinSerialize().length;

            final Coin fee = ProposalConsensus.getFee(daoParamService, readableBsqBlockChain);
            new Popup<>().headLine(Res.get("dao.proposal.create.confirm"))
                    .confirmation(Res.get("dao.proposal.create.confirm.info",
                            bsqFormatter.formatCoinWithCode(fee),
                            btcFormatter.formatCoinWithCode(miningFee),
                            CoinUtil.getFeePerByte(miningFee, txSize),
                            txSize / 1000d))
                    .actionButtonText(Res.get("shared.yes"))
                    .onAction(() -> {
                        proposalService.publishProposal(proposal,
                                () -> {
                                    proposalDisplay.clearForm();
                                    proposalTypeComboBox.getSelectionModel().clearSelection();
                                    new Popup<>().confirmation(Res.get("dao.tx.published.success")).show();
                                },
                                errorMessage -> new Popup<>().warning(errorMessage).show());
                    })
                    .closeButtonText(Res.get("shared.cancel"))
                    .show();
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
        } catch (TransactionVerificationException | WalletException | IOException e) {
            log.error(e.toString());
            e.printStackTrace();
            new Popup<>().warning(e.toString()).show();
        }
    }

    private Proposal createProposal(ProposalType type)
            throws InsufficientMoneyException, TransactionVerificationException, ValidationException,
            WalletException, IOException {

        validateInputs();

        switch (type) {
            case COMPENSATION_REQUEST:
                CompensationRequestPayload compensationRequestPayload = compensationRequestService.createCompensationRequestPayload(
                        proposalDisplay.nameTextField.getText(),
                        proposalDisplay.titleTextField.getText(),
                        proposalDisplay.descriptionTextArea.getText(),
                        proposalDisplay.linkInputTextField.getText(),
                        bsqFormatter.parseToCoin(Objects.requireNonNull(proposalDisplay.requestedBsqTextField).getText()),
                        Objects.requireNonNull(proposalDisplay.bsqAddressTextField).getText());
                return compensationRequestService.createCompensationRequest(compensationRequestPayload);
            case GENERIC:
                GenericProposalPayload genericProposalPayload = genericProposalService.createGenericProposalPayload(
                        proposalDisplay.nameTextField.getText(),
                        proposalDisplay.titleTextField.getText(),
                        proposalDisplay.descriptionTextArea.getText(),
                        proposalDisplay.linkInputTextField.getText());
                return genericProposalService.createGenericProposal(genericProposalPayload);
            case CHANGE_PARAM:
                //TODO
                return null;
            case REMOVE_ALTCOIN:
                //TODO
                return null;
            default:
                final String msg = "Undefined ProposalType " + selectedProposalType;
                DevEnv.logErrorAndThrowIfDevMode(msg);
                return null;
        }
    }

    private void addProposalDisplay() {
        // TODO need to update removed fields when switching.
        if (proposalDisplay != null) {
            proposalDisplay.removeAllFields();
            proposalDisplay = null;
        }
        if (selectedProposalType != null) {
            proposalDisplay = new ProposalDisplay(root, bsqFormatter, bsqWalletService, feeService);
            proposalDisplay.createAllFields(Res.get("dao.proposal.create.createNew"), 1, Layout.GROUP_DISTANCE,
                    selectedProposalType, true, true);
            proposalDisplay.fillWithMock();

            createButton = addButtonAfterGroup(root, proposalDisplay.incrementAndGetGridRow(), Res.get("dao.proposal.create.create.button"));
            setCreateButtonHandler();
        }
    }

    private void setCreateButtonHandler() {
        createButton.setOnAction(event -> {
            // TODO break up in methods
            if (GUIUtil.isReadyForTxBroadcast(p2PService, walletsSetup)) {
                publishProposal(selectedProposalType);
            } else {
                GUIUtil.showNotReadyForTxBroadcastPopups(p2PService, walletsSetup);
            }
        });
    }

    private void validateInputs() {
        // We check in proposalDisplay that no invalid input as allowed
        checkArgument(ProposalConsensus.isDescriptionSizeValid(proposalDisplay.descriptionTextArea.getText()), "descriptionText must not be longer than " +
                ProposalConsensus.getMaxLengthDescriptionText() + " chars");

        // TODO add more checks for all input fields
    }

}

