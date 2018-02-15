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

package io.bisq.gui.main.dao.compensation.create;

import com.google.common.util.concurrent.FutureCallback;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.locale.Res;
import io.bisq.core.btc.exceptions.TransactionVerificationException;
import io.bisq.core.btc.exceptions.WalletException;
import io.bisq.core.btc.wallet.*;
import io.bisq.core.dao.compensation.CompensationRequest;
import io.bisq.core.dao.compensation.CompensationRequestManager;
import io.bisq.core.dao.compensation.CompensationRequestPayload;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.core.util.CoinUtil;
import io.bisq.gui.common.view.ActivatableView;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.main.dao.compensation.CompensationRequestDisplay;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.GUIUtil;
import io.bisq.gui.util.BsqFormatter;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.P2PService;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import org.bitcoinj.core.*;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.security.PublicKey;
import java.util.Date;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bisq.gui.util.FormBuilder.addButtonAfterGroup;

@FxmlView
public class CreateCompensationRequestView extends ActivatableView<GridPane, Void> {

    private CompensationRequestDisplay compensationRequestDisplay;
    private Button createButton;

    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final WalletsSetup walletsSetup;
    private final P2PService p2PService;
    private final FeeService feeService;
    private final CompensationRequestManager compensationRequestManager;
    private final BSFormatter btcFormatter;
    private final BsqFormatter bsqFormatter;
    private final PublicKey p2pStorageSignaturePubKey;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private CreateCompensationRequestView(BsqWalletService bsqWalletService,
                                          BtcWalletService btcWalletService,
                                          WalletsSetup walletsSetup,
                                          P2PService p2PService,
                                          FeeService feeService,
                                          CompensationRequestManager compensationRequestManager,
                                          KeyRing keyRing,
                                          BSFormatter btcFormatter,
                                          BsqFormatter bsqFormatter) {
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.walletsSetup = walletsSetup;
        this.p2PService = p2PService;
        this.feeService = feeService;
        this.compensationRequestManager = compensationRequestManager;
        this.btcFormatter = btcFormatter;
        this.bsqFormatter = bsqFormatter;

        p2pStorageSignaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
    }


    @Override
    public void initialize() {
        compensationRequestDisplay = new CompensationRequestDisplay(root, bsqFormatter, bsqWalletService);
        compensationRequestDisplay.createAllFields(Res.get("dao.compensation.create.createNew"), 0);
        createButton = addButtonAfterGroup(root, compensationRequestDisplay.incrementAndGetGridRow(), Res.get("dao.compensation.create.create.button"));
    }

    @Override
    protected void activate() {
        compensationRequestDisplay.fillWithMock();

        createButton.setOnAction(event -> {
            // TODO break up in methods
            if (GUIUtil.isReadyForTxBroadcast(p2PService, walletsSetup)) {
                NodeAddress nodeAddress = p2PService.getAddress();
                checkNotNull(nodeAddress, "nodeAddress must not be null");
                CompensationRequestPayload compensationRequestPayload = new CompensationRequestPayload(
                        UUID.randomUUID().toString(),
                        compensationRequestDisplay.nameTextField.getText(),
                        compensationRequestDisplay.titleTextField.getText(),
                        compensationRequestDisplay.descriptionTextArea.getText(),
                        compensationRequestDisplay.linkInputTextField.getText(),
                        bsqFormatter.parseToCoin(compensationRequestDisplay.requestedBsqTextField.getText()),
                        compensationRequestDisplay.bsqAddressTextField.getText(),
                        nodeAddress,
                        p2pStorageSignaturePubKey,
                        new Date()
                );

                boolean walletExceptionMightBeCausedByBtCWallet = false;
                try {
                    CompensationRequest compensationRequest = compensationRequestManager.prepareCompensationRequest(compensationRequestPayload);
                    Coin miningFee = compensationRequest.getSignedTx().getFee();
                    int txSize = compensationRequest.getSignedTx().bitcoinSerialize().length;
                    new Popup<>().headLine(Res.get("dao.compensation.create.confirm"))
                            .confirmation(Res.get("dao.compensation.create.confirm.info",
                                    bsqFormatter.formatCoinWithCode(compensationRequest.getRequestedBsq()),
                                    bsqFormatter.formatCoinWithCode(compensationRequest.getCompensationRequestFee()),
                                    btcFormatter.formatCoinWithCode(miningFee),
                                    CoinUtil.getFeePerByte(miningFee, txSize),
                                    (txSize / 1000d)))
                            .actionButtonText(Res.get("shared.yes"))
                            .onAction(() -> {
                                compensationRequestManager.commitCompensationRequest(compensationRequest, new FutureCallback<Transaction>() {
                                    @Override
                                    public void onSuccess(@Nullable Transaction transaction) {
                                        compensationRequestDisplay.clearForm();
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
                    new Popup<>().warning(Res.get("dao.compensation.create.missingFunds", formatter.formatCoinWithCode(e.missing))).show();
                } catch (IOException | TransactionVerificationException | WalletException | ChangeBelowDustException e) {
                    log.error(e.toString());
                    e.printStackTrace();
                    new Popup<>().warning(e.toString()).show();
                }
            } else {
                GUIUtil.showNotReadyForTxBroadcastPopups(p2PService, walletsSetup);
            }
        });
    }

    @Override
    protected void deactivate() {
        createButton.setOnAction(null);
    }
}

