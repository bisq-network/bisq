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

package io.bitsquare.gui.main.dao.compensation.create;

import com.google.common.util.concurrent.FutureCallback;
import io.bitsquare.app.Version;
import io.bitsquare.btc.InsufficientFundsException;
import io.bitsquare.btc.exceptions.TransactionVerificationException;
import io.bitsquare.btc.exceptions.WalletException;
import io.bitsquare.btc.provider.fee.FeeService;
import io.bitsquare.btc.wallet.BtcWalletService;
import io.bitsquare.btc.wallet.ChangeBelowDustException;
import io.bitsquare.btc.wallet.SquWalletService;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.util.MathUtils;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.dao.compensation.CompensationRequestManager;
import io.bitsquare.dao.compensation.CompensationRequestPayload;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.main.dao.compensation.CompensationRequestDisplay;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.P2PService;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.wallet.KeyChain;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.PublicKey;
import java.util.Date;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.gui.util.FormBuilder.addButtonAfterGroup;

@FxmlView
public class CreateCompensationRequestView extends ActivatableView<GridPane, Void> {

    private CompensationRequestDisplay CompensationRequestDisplay;
    private Button createButton;

    private final PublicKey p2pStorageSignaturePubKey;
    private final SquWalletService squWalletService;
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
    private CreateCompensationRequestView(SquWalletService squWalletService, BtcWalletService btcWalletService, FeeService feeService,
                                          CompensationRequestManager compensationRequestManager, P2PService p2PService, KeyRing keyRing, BSFormatter btcFormatter) {
        this.squWalletService = squWalletService;
        this.btcWalletService = btcWalletService;
        this.feeService = feeService;
        this.compensationRequestManager = compensationRequestManager;
        this.p2PService = p2PService;
        this.btcFormatter = btcFormatter;

        p2pStorageSignaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
    }


    @Override
    public void initialize() {
        CompensationRequestDisplay = new CompensationRequestDisplay(root);
        CompensationRequestDisplay.removeAllFields();
        CompensationRequestDisplay.createAllFields("Create new compensation request", 0);
        createButton = addButtonAfterGroup(root, CompensationRequestDisplay.incrementAndGetGridRow(), "Create compensation request");
    }

    @Override
    protected void activate() {
        CompensationRequestDisplay.fillWithMock();
        createButton.setOnAction(event -> {
            DeterministicKey squKeyPair = squWalletService.getWallet().freshKey(KeyChain.KeyPurpose.AUTHENTICATION);
            checkNotNull(squKeyPair, "squKeyPair must not be null");

            //TODO
            Date startDate = new Date();
            Date endDate = new Date();

            // TODO can be null if we are still not full connected
            nodeAddress = p2PService.getAddress();
            if (nodeAddress != null) {
                CompensationRequestPayload compensationRequestPayload = new CompensationRequestPayload(UUID.randomUUID().toString(),
                        CompensationRequestDisplay.nameTextField.getText(),
                        CompensationRequestDisplay.titleTextField.getText(),
                        CompensationRequestDisplay.categoryTextField.getText(),
                        CompensationRequestDisplay.descriptionTextField.getText(),
                        CompensationRequestDisplay.linkTextField.getText(),
                        startDate,
                        endDate,
                        btcFormatter.parseToCoin(CompensationRequestDisplay.requestedBTCTextField.getText()),
                        CompensationRequestDisplay.btcAddressTextField.getText(),
                        nodeAddress,
                        p2pStorageSignaturePubKey
                );
                // We get the JSON of the object excluding signature and feeTxId
                String payloadAsJson = StringUtils.deleteWhitespace(Utilities.objectToJson(compensationRequestPayload));
                log.error(payloadAsJson);
                // Signs a text message using the standard Bitcoin messaging signing format and returns the signature as a base64
                // encoded string.
                String signature = squKeyPair.signMessage(payloadAsJson);
                compensationRequestPayload.setSignature(signature);

                try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    String dataAndSig = payloadAsJson + signature;
                    byte[] dataAndSigAsBytes = dataAndSig.getBytes();
                    outputStream.write(Version.COMPENSATION_REQUEST_VERSION);
                    outputStream.write(Utils.sha256hash160(dataAndSigAsBytes));
                    byte hash[] = outputStream.toByteArray();
                    //TODO should we store the hash in the compensationRequestPayload object?

                    Coin createCompensationRequestFee = feeService.getCreateCompensationRequestFee();
                    Transaction preparedSendTx = squWalletService.getPreparedBurnFeeTx(createCompensationRequestFee);

                    //TODO 1 Btc output (small payment to own compensation receiving address)
                    Transaction txWithBtcFee = btcWalletService.completePreparedSquTx(preparedSendTx, false, hash);
                    Transaction signedTx = squWalletService.signTx(txWithBtcFee);
                    Coin miningFee = signedTx.getFee();
                    int txSize = signedTx.bitcoinSerialize().length;
                    new Popup().headLine("Confirm compensation request fee payment transaction")
                            .confirmation("Voting fee: " + btcFormatter.formatCoinWithCode(createCompensationRequestFee) + "\n" +
                                    "Mining fee: " + btcFormatter.formatCoinWithCode(miningFee) + " (" +
                                    MathUtils.roundDouble(((double) miningFee.value / (double) txSize), 2) +
                                    " Satoshis/byte)\n" +
                                    "Transaction size: " + (txSize / 1000d) + " Kb\n\n" +
                                    "Are you sure you want to send the transaction?")
                            .actionButtonText("Yes")
                            .onAction(() -> {
                                try {
                                    squWalletService.commitTx(txWithBtcFee);
                                    // We need to create another instance, otherwise the tx would trigger an invalid state exception 
                                    // if it gets committed 2 times 
                                    btcWalletService.commitTx(btcWalletService.getClonedTransaction(txWithBtcFee));
                                    squWalletService.broadcastTx(signedTx, new FutureCallback<Transaction>() {
                                        @Override
                                        public void onSuccess(@Nullable Transaction transaction) {
                                            checkNotNull(transaction, "Transaction must not be null at broadcastTx callback.");
                                            compensationRequestPayload.setFeeTxId(transaction.getHashAsString());
                                            compensationRequestManager.addToP2PNetwork(compensationRequestPayload);
                                            CompensationRequestDisplay.clearForm();
                                            new Popup<>().confirmation("Your transaction has been successfully published.").show();
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
                            .closeButtonText("Cancel")
                            .show();
                } catch (InsufficientFundsException | IOException |
                        TransactionVerificationException | WalletException | InsufficientMoneyException | ChangeBelowDustException e) {
                    log.error(e.toString());
                    e.printStackTrace();
                    new Popup<>().warning(e.toString()).show();
                }
            } else {
                //TODO check if that can happen
                new Popup<>().warning("Your node address is not known yet. Please try again after your hidden service is published.").show();
            }
        });
    }

    @Override
    protected void deactivate() {
        createButton.setOnAction(null);
    }
}

