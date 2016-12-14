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
import io.bitsquare.btc.InsufficientFundsException;
import io.bitsquare.btc.exceptions.TransactionVerificationException;
import io.bitsquare.btc.exceptions.WalletException;
import io.bitsquare.btc.provider.fee.FeeService;
import io.bitsquare.btc.wallet.BtcWalletService;
import io.bitsquare.btc.wallet.ChangeBelowDustException;
import io.bitsquare.btc.wallet.SquWalletService;
import io.bitsquare.common.ByteArrayUtils;
import io.bitsquare.common.crypto.KeyRing;
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
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.wallet.KeyChain;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.security.PublicKey;
import java.util.Date;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.gui.util.FormBuilder.addButtonAfterGroup;

@FxmlView
public class CreateCompensationRequestView extends ActivatableView<GridPane, Void> {

    private CompensationRequestDisplay CompensationRequestDisplay;
    private Button createButton;

    private final NodeAddress nodeAddress;
    private final PublicKey p2pStorageSignaturePubKey;
    private final SquWalletService squWalletService;
    private final BtcWalletService btcWalletService;
    private final FeeService feeService;
    private final CompensationRequestManager compensationRequestManager;
    private final BSFormatter btcFormatter;

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
        this.btcFormatter = btcFormatter;

        nodeAddress = p2PService.getAddress();
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
                    p2pStorageSignaturePubKey,
                    squKeyPair.getPubKey()
            );
            Sha256Hash hash = Sha256Hash.of(ByteArrayUtils.objectToByteArray(compensationRequestPayload));
            compensationRequestPayload.setSignature(squKeyPair.sign(hash).encodeToDER());
            hash = Sha256Hash.of(ByteArrayUtils.objectToByteArray(compensationRequestPayload));
            compensationRequestPayload.setHash(hash.getBytes());

            try {
                Coin createCompensationRequestFee = feeService.getCreateCompensationRequestFee();
                Transaction preparedSendTx = squWalletService.getPreparedCompensationRequestFeeTx(createCompensationRequestFee);
                Transaction txWithBtcFee = btcWalletService.addInputAndOutputToPreparedSquCompensationRequestFeeTx(preparedSendTx, hash.getBytes());
                squWalletService.signAndBroadcastCompensationRequestFeeTx(txWithBtcFee, new FutureCallback<Transaction>() {
                    @Override
                    public void onSuccess(@Nullable Transaction transaction) {
                        checkNotNull(transaction, "Transaction must not be null at signAndBroadcastCompensationRequestFeeTx callback.");
                        compensationRequestPayload.setFeeTxId(transaction.getHashAsString());
                        publishToP2PNetwork(compensationRequestPayload);
                        CompensationRequestDisplay.clearForm();
                        new Popup<>().confirmation("Your compensationRequestPayload has been successfully published.").show();
                    }

                    @Override
                    public void onFailure(@NotNull Throwable t) {
                        log.error(t.toString());
                        new Popup<>().warning(t.toString()).show();
                    }
                });
            } catch (InsufficientFundsException |
                    TransactionVerificationException | WalletException | InsufficientMoneyException | ChangeBelowDustException e) {
                log.error(e.toString());
                e.printStackTrace();
                new Popup<>().warning(e.toString()).show();
            }
        });
    }

    private void publishToP2PNetwork(CompensationRequestPayload compensationRequestPayload) {
        compensationRequestManager.addToP2PNetwork(compensationRequestPayload);
    }

    @Override
    protected void deactivate() {
        createButton.setOnAction(null);
    }
}

