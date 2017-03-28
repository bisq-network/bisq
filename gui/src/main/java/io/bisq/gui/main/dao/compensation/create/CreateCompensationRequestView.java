/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.dao.compensation.create;

import com.google.common.util.concurrent.FutureCallback;
import io.bisq.common.app.Version;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Utilities;
import io.bisq.core.btc.InsufficientFundsException;
import io.bisq.core.btc.exceptions.TransactionVerificationException;
import io.bisq.core.btc.exceptions.WalletException;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.ChangeBelowDustException;
import io.bisq.core.dao.compensation.CompensationRequestManager;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.core.util.CoinUtil;
import io.bisq.gui.common.view.ActivatableView;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.main.dao.compensation.CompensationRequestDisplay;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.util.BSFormatter;
import io.bisq.network.p2p.storage.P2PService;
import io.bisq.protobuffer.crypto.KeyRing;
import io.bisq.protobuffer.payload.dao.compensation.CompensationRequestPayload;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.DeterministicKey;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.PublicKey;
import java.util.Date;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.bisq.gui.util.FormBuilder.addButtonAfterGroup;

@FxmlView
public class CreateCompensationRequestView extends ActivatableView<GridPane, Void> {

    private CompensationRequestDisplay compensationRequestDisplay;
    private Button createButton;

    private final PublicKey p2pStorageSignaturePubKey;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final FeeService feeService;
    private final CompensationRequestManager compensationRequestManager;
    private final P2PService p2PService;
    private final BSFormatter btcFormatter;

    @Nullable
    private NodeAddress nodeAddress;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private CreateCompensationRequestView(BsqWalletService bsqWalletService, BtcWalletService btcWalletService, FeeService feeService,
                                          CompensationRequestManager compensationRequestManager, P2PService p2PService, KeyRing keyRing, BSFormatter btcFormatter) {
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.feeService = feeService;
        this.compensationRequestManager = compensationRequestManager;
        this.p2PService = p2PService;
        this.btcFormatter = btcFormatter;

        p2pStorageSignaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
    }


    @Override
    public void initialize() {
        compensationRequestDisplay = new CompensationRequestDisplay(root);
        compensationRequestDisplay.removeAllFields();
        compensationRequestDisplay.createAllFields(Res.get("dao.compensation.create.createNew"), 0);
        createButton = addButtonAfterGroup(root, compensationRequestDisplay.incrementAndGetGridRow(), Res.get("dao.compensation.create.create.button"));
    }

    @Override
    protected void activate() {
        compensationRequestDisplay.fillWithMock();
        createButton.setOnAction(event -> {
            //TODO
            Date startDate = new Date();
            Date endDate = new Date();

            // TODO can be null if we are still not full connected
            nodeAddress = p2PService.getAddress();

            // TODO we neet to wait until all services are initiated before calling the code below
            checkNotNull(nodeAddress, "nodeAddress must not be null");
            CompensationRequestPayload compensationRequestPayload = new CompensationRequestPayload(UUID.randomUUID().toString(),
                    compensationRequestDisplay.nameTextField.getText(),
                    compensationRequestDisplay.titleTextField.getText(),
                    compensationRequestDisplay.categoryTextField.getText(),
                    compensationRequestDisplay.descriptionTextField.getText(),
                    compensationRequestDisplay.linkTextField.getText(),
                    startDate,
                    endDate,
                    btcFormatter.parseToCoin(compensationRequestDisplay.requestedBTCTextField.getText()),
                    compensationRequestDisplay.btcAddressTextField.getText(),
                    nodeAddress,
                    p2pStorageSignaturePubKey
            );

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                Coin createCompensationRequestFee = feeService.getCreateCompensationRequestFee();
                Transaction preparedSendTx = bsqWalletService.getPreparedBurnFeeTx(createCompensationRequestFee);

                checkArgument(!preparedSendTx.getInputs().isEmpty(), "preparedSendTx inputs must not be empty");

                // We use the key of the first BSQ input for signing the data
                TransactionOutput connectedOutput = preparedSendTx.getInputs().get(0).getConnectedOutput();
                checkNotNull(connectedOutput, "connectedOutput must not be null");
                DeterministicKey bsqKeyPair = bsqWalletService.findKeyFromPubKeyHash(connectedOutput.getScriptPubKey().getPubKeyHash());
                checkNotNull(bsqKeyPair, "bsqKeyPair must not be null");

                // We get the JSON of the object excluding signature and feeTxId
                String payloadAsJson = StringUtils.deleteWhitespace(Utilities.objectToJson(compensationRequestPayload));
                log.error(payloadAsJson);
                // Signs a text message using the standard Bitcoin messaging signing format and returns the signature as a base64
                // encoded string.
                String signature = bsqKeyPair.signMessage(payloadAsJson);
                compensationRequestPayload.setSignature(signature);

                String dataAndSig = payloadAsJson + signature;
                byte[] dataAndSigAsBytes = dataAndSig.getBytes();
                outputStream.write(Version.COMPENSATION_REQUEST_VERSION);
                outputStream.write(Utils.sha256hash160(dataAndSigAsBytes));
                byte hash[] = outputStream.toByteArray();
                //TODO should we store the hash in the compensationRequestPayload object?


                //TODO 1 Btc output (small payment to own compensation receiving address)
                Transaction txWithBtcFee = btcWalletService.completePreparedBsqTx(preparedSendTx, false, hash);
                Transaction signedTx = bsqWalletService.signTx(txWithBtcFee);
                Coin miningFee = signedTx.getFee();
                int txSize = signedTx.bitcoinSerialize().length;
                new Popup().headLine(Res.get("dao.compensation.create.confirm"))
                        .confirmation(Res.get("dao.tx.summary",
                                btcFormatter.formatCoinWithCode(createCompensationRequestFee),
                                btcFormatter.formatCoinWithCode(miningFee),
                                CoinUtil.getFeePerByte(miningFee, txSize),
                                (txSize / 1000d)))
                        .actionButtonText(Res.get("shared.yes"))
                        .onAction(() -> {
                            try {
                                bsqWalletService.commitTx(txWithBtcFee);
                                // We need to create another instance, otherwise the tx would trigger an invalid state exception 
                                // if it gets committed 2 times 
                                btcWalletService.commitTx(btcWalletService.getClonedTransaction(txWithBtcFee));
                                bsqWalletService.broadcastTx(signedTx, new FutureCallback<Transaction>() {
                                    @Override
                                    public void onSuccess(@Nullable Transaction transaction) {
                                        checkNotNull(transaction, "Transaction must not be null at broadcastTx callback.");
                                        compensationRequestPayload.setFeeTxId(transaction.getHashAsString());
                                        compensationRequestManager.addToP2PNetwork(compensationRequestPayload);
                                        compensationRequestDisplay.clearForm();
                                        new Popup<>().confirmation(Res.get("dao.tx.published.success")).show();
                                    }

                                    @Override
                                    public void onFailure(@NotNull Throwable t) {
                                        log.error(t.toString());
                                        new Popup<>().warning(t.toString()).show();
                                    }
                                });
                            } catch (WalletException | TransactionVerificationException e) {
                                log.error(e.toString());
                                e.printStackTrace();
                                new Popup<>().warning(e.toString());
                            }
                        })
                        .closeButtonText(Res.get("shared.cancel"))
                        .show();
            } catch (InsufficientFundsException | IOException |
                    TransactionVerificationException | WalletException | InsufficientMoneyException | ChangeBelowDustException e) {
                log.error(e.toString());
                e.printStackTrace();
                new Popup<>().warning(e.toString()).show();
            }
        });
    }

    @Override
    protected void deactivate() {
        createButton.setOnAction(null);
    }
}

