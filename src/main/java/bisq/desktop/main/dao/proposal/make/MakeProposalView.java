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
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.BsqFormatter;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.InsufficientBsqException;
import bisq.core.btc.wallet.WalletsSetup;
import bisq.core.dao.proposal.ProposalCollectionsManager;
import bisq.core.dao.proposal.ProposalType;
import bisq.core.dao.proposal.compensation.CompensationAmountException;
import bisq.core.dao.proposal.compensation.CompensationRequest;
import bisq.core.dao.proposal.compensation.CompensationRequestManager;
import bisq.core.dao.proposal.compensation.CompensationRequestPayload;
import bisq.core.dao.proposal.generic.GenericProposal;
import bisq.core.dao.proposal.generic.GenericProposalManager;
import bisq.core.dao.proposal.generic.GenericProposalPayload;
import bisq.core.provider.fee.FeeService;
import bisq.core.util.CoinUtil;

import bisq.network.p2p.P2PService;

import bisq.common.app.DevEnv;
import bisq.common.locale.Res;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import com.google.common.util.concurrent.FutureCallback;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.GridPane;

import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;

import javafx.util.StringConverter;

import java.util.Arrays;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;
import static bisq.desktop.util.FormBuilder.addLabelComboBox;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class MakeProposalView extends ActivatableView<GridPane, Void> {

    private ProposalDisplay proposalDisplay;
    private Button createButton;

    private final BsqWalletService bsqWalletService;
    private final WalletsSetup walletsSetup;
    private final P2PService p2PService;
    private final FeeService feeService;
    private final ProposalCollectionsManager proposalCollectionsManager;
    private final CompensationRequestManager compensationRequestManager;
    private final GenericProposalManager genericProposalManager;
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
                             ProposalCollectionsManager proposalCollectionsManager,
                             CompensationRequestManager compensationRequestManager,
                             GenericProposalManager genericProposalManager,
                             BSFormatter btcFormatter,
                             BsqFormatter bsqFormatter) {
        this.bsqWalletService = bsqWalletService;
        this.walletsSetup = walletsSetup;
        this.p2PService = p2PService;
        this.feeService = feeService;
        this.proposalCollectionsManager = proposalCollectionsManager;
        this.compensationRequestManager = compensationRequestManager;
        this.genericProposalManager = genericProposalManager;
        this.btcFormatter = btcFormatter;
        this.bsqFormatter = bsqFormatter;
    }


    @Override
    public void initialize() {
        addTitledGroupBg(root, 0, 1, Res.get("dao.proposal.create.selectProposalType"));
        proposalTypeComboBox = addLabelComboBox(root, 0, Res.getWithCol("dao.proposal.create.proposalType"), Layout.FIRST_ROW_DISTANCE).second;
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
    }

    @Override
    protected void deactivate() {
        proposalTypeComboBox.getSelectionModel().selectedItemProperty().removeListener(proposalTypeChangeListener);
        if (createButton != null)
            createButton.setOnAction(null);
    }

    private void createCompensationRequest() {
        CompensationRequestPayload compensationRequestPayload = compensationRequestManager.getNewCompensationRequestPayload(
                proposalDisplay.nameTextField.getText(),
                proposalDisplay.titleTextField.getText(),
                proposalDisplay.descriptionTextArea.getText(),
                proposalDisplay.linkInputTextField.getText(),
                bsqFormatter.parseToCoin(Objects.requireNonNull(proposalDisplay.requestedBsqTextField).getText()),
                Objects.requireNonNull(proposalDisplay.bsqAddressTextField).getText());
        try {
            CompensationRequest compensationRequest = compensationRequestManager.prepareCompensationRequest(compensationRequestPayload);
            Coin miningFee = compensationRequest.getTx().getFee();
            int txSize = compensationRequest.getTx().bitcoinSerialize().length;
            new Popup<>().headLine(Res.get("dao.proposal.create.confirm"))
                    .confirmation(Res.get("dao.proposal.create.confirm.info",
                            bsqFormatter.formatCoinWithCode(compensationRequest.getRequestedBsq()),
                            bsqFormatter.formatCoinWithCode(compensationRequest.getFeeAsCoin()),
                            btcFormatter.formatCoinWithCode(miningFee),
                            CoinUtil.getFeePerByte(miningFee, txSize),
                            (txSize / 1000d)))
                    .actionButtonText(Res.get("shared.yes"))
                    .onAction(() -> {
                        proposalCollectionsManager.publishProposal(compensationRequest, new FutureCallback<Transaction>() {
                            @Override
                            public void onSuccess(@Nullable Transaction transaction) {
                                proposalDisplay.clearForm();
                                new Popup<>().confirmation(Res.get("dao.tx.published.success")).show();
                            }

                            @Override
                            public void onFailure(@NotNull Throwable t) {
                                log.error(t.toString());
                                new Popup<>().warning(t.toString()).show();
                            }
                        });
                    })
                    .closeButtonText(Res.get("shared.cancel"))
                    .show();
        } catch (InsufficientMoneyException e) {
            BSFormatter formatter = e instanceof InsufficientBsqException ? bsqFormatter : btcFormatter;
            new Popup<>().warning(Res.get("dao.proposal.create.missingFunds", formatter.formatCoinWithCode(e.missing))).show();
        } catch (CompensationAmountException e) {
            new Popup<>().warning(Res.get("validation.bsq.amountBelowMinAmount", bsqFormatter.formatCoinWithCode(e.required))).show();
        } catch (TransactionVerificationException | WalletException e) {
            log.error(e.toString());
            e.printStackTrace();
            new Popup<>().warning(e.toString()).show();
        }
    }

    private void createGenericProposal() {
        GenericProposalPayload genericProposalPayload = genericProposalManager.getNewGenericProposalPayload(
                proposalDisplay.nameTextField.getText(),
                proposalDisplay.titleTextField.getText(),
                proposalDisplay.descriptionTextArea.getText(),
                proposalDisplay.linkInputTextField.getText());
        try {
            GenericProposal genericProposal = genericProposalManager.prepareGenericProposal(genericProposalPayload);
            Coin miningFee = genericProposal.getTx().getFee();
            int txSize = genericProposal.getTx().bitcoinSerialize().length;
            new Popup<>().headLine(Res.get("dao.proposal.create.confirm"))
                    .confirmation(Res.get("dao.proposal.create.confirm.info",
                            bsqFormatter.formatCoinWithCode(Coin.valueOf(10000)), // TODO dummy
                            bsqFormatter.formatCoinWithCode(genericProposal.getFeeAsCoin()),
                            btcFormatter.formatCoinWithCode(miningFee),
                            CoinUtil.getFeePerByte(miningFee, txSize),
                            (txSize / 1000d)))
                    .actionButtonText(Res.get("shared.yes"))
                    .onAction(() -> {
                        proposalCollectionsManager.publishProposal(genericProposal, new FutureCallback<Transaction>() {
                            @Override
                            public void onSuccess(@Nullable Transaction transaction) {
                                proposalDisplay.clearForm();
                                new Popup<>().confirmation(Res.get("dao.tx.published.success")).show();
                            }

                            @Override
                            public void onFailure(@NotNull Throwable t) {
                                log.error(t.toString());
                                new Popup<>().warning(t.toString()).show();
                            }
                        });
                    })
                    .closeButtonText(Res.get("shared.cancel"))
                    .show();
        } catch (InsufficientMoneyException e) {
            BSFormatter formatter = e instanceof InsufficientBsqException ? bsqFormatter : btcFormatter;
            new Popup<>().warning(Res.get("dao.proposal.create.missingFunds", formatter.formatCoinWithCode(e.missing))).show();
        } catch (CompensationAmountException e) {
            new Popup<>().warning(Res.get("validation.bsq.amountBelowMinAmount", bsqFormatter.formatCoinWithCode(e.required))).show();
        } catch (TransactionVerificationException | WalletException e) {
            log.error(e.toString());
            e.printStackTrace();
            new Popup<>().warning(e.toString()).show();
        }
    }

    private void addProposalDisplay() {
        // TODO need to update removed fields when switching.
        if (proposalDisplay != null) {
            root.getChildren().remove(3, root.getChildren().size());
        }
        proposalDisplay = new ProposalDisplay(root, bsqFormatter, bsqWalletService, feeService);
        proposalDisplay.createAllFields(Res.get("dao.proposal.create.createNew"), 1, Layout.GROUP_DISTANCE, selectedProposalType, true);
        proposalDisplay.fillWithMock();

        createButton = addButtonAfterGroup(root, proposalDisplay.incrementAndGetGridRow(), Res.get("dao.proposal.create.create.button"));
        createButton.setOnAction(event -> {
            // TODO break up in methods
            if (GUIUtil.isReadyForTxBroadcast(p2PService, walletsSetup)) {
                switch (selectedProposalType) {
                    case COMPENSATION_REQUEST:
                        createCompensationRequest();
                        break;
                    case GENERIC:
                        createGenericProposal();
                        break;
                    case CHANGE_PARAM:
                        //TODO
                        break;
                    case REMOVE_ALTCOIN:
                        //TODO
                        break;
                    default:
                        final String msg = "Undefined ProposalType " + selectedProposalType;
                        log.error(msg);
                        if (DevEnv.isDevMode())
                            throw new RuntimeException(msg);
                        break;

                }
            } else {
                GUIUtil.showNotReadyForTxBroadcastPopups(p2PService, walletsSetup);
            }
        });
    }
}

