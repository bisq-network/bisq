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

package io.bitsquare.gui.main.dao.proposals.create;

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
import io.bitsquare.dao.proposals.ProposalManager;
import io.bitsquare.dao.proposals.ProposalPayload;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.main.dao.proposals.ProposalDisplay;
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
public class CreateProposalView extends ActivatableView<GridPane, Void> {

    private ProposalDisplay proposalDisplay;
    private Button createButton;

    private final NodeAddress nodeAddress;
    private final PublicKey p2pStorageSignaturePubKey;
    private final SquWalletService squWalletService;
    private final BtcWalletService btcWalletService;
    private final FeeService feeService;
    private final ProposalManager proposalManager;
    private final BSFormatter btcFormatter;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private CreateProposalView(SquWalletService squWalletService, BtcWalletService btcWalletService, FeeService feeService,
                               ProposalManager proposalManager, P2PService p2PService, KeyRing keyRing, BSFormatter btcFormatter) {
        this.squWalletService = squWalletService;
        this.btcWalletService = btcWalletService;
        this.feeService = feeService;
        this.proposalManager = proposalManager;
        this.btcFormatter = btcFormatter;

        nodeAddress = p2PService.getAddress();
        p2pStorageSignaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
    }


    @Override
    public void initialize() {
        proposalDisplay = new ProposalDisplay(root);
        proposalDisplay.removeAllFields();
        proposalDisplay.createAllFields();
        createButton = addButtonAfterGroup(root, proposalDisplay.incrementAndGetGridRow(), "Create proposal");
    }

    @Override
    protected void activate() {
        proposalDisplay.fillWithMock();
        createButton.setOnAction(event -> {
            DeterministicKey squKeyPair = squWalletService.getWallet().freshKey(KeyChain.KeyPurpose.AUTHENTICATION);
            checkNotNull(squKeyPair, "squKeyPair must not be null");

            //TODO
            Date startDate = new Date();
            Date endDate = new Date();

            ProposalPayload proposalPayload = new ProposalPayload(UUID.randomUUID().toString(),
                    proposalDisplay.nameTextField.getText(),
                    proposalDisplay.titleTextField.getText(),
                    proposalDisplay.categoryTextField.getText(),
                    proposalDisplay.descriptionTextField.getText(),
                    proposalDisplay.linkTextField.getText(),
                    startDate,
                    endDate,
                    btcFormatter.parseToCoin(proposalDisplay.requestedBTCTextField.getText()),
                    proposalDisplay.btcAddressTextField.getText(),
                    nodeAddress,
                    p2pStorageSignaturePubKey,
                    squKeyPair.getPubKey()
            );
            Sha256Hash hash = Sha256Hash.of(ByteArrayUtils.objectToByteArray(proposalPayload));
            proposalPayload.setSignature(squKeyPair.sign(hash).encodeToDER());
            hash = Sha256Hash.of(ByteArrayUtils.objectToByteArray(proposalPayload));
            proposalPayload.setHash(hash.getBytes());

            try {
                Coin createProposalFee = feeService.getCreateProposalFee();
                Transaction preparedSendTx = squWalletService.getPreparedProposalFeeTx(createProposalFee);
                Transaction txWithBtcFee = btcWalletService.addInputAndOutputToPreparedSquProposalFeeTx(preparedSendTx, hash.getBytes());
                squWalletService.signAndBroadcastProposalFeeTx(txWithBtcFee, new FutureCallback<Transaction>() {
                    @Override
                    public void onSuccess(@Nullable Transaction transaction) {
                        checkNotNull(transaction, "Transaction must not be null at signAndBroadcastProposalFeeTx callback.");
                        proposalPayload.setFeeTxId(transaction.getHashAsString());
                        publishToP2PNetwork(proposalPayload);
                        proposalDisplay.clearForm();
                        new Popup<>().confirmation("Your proposalPayload has been successfully published.").show();
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

    private void publishToP2PNetwork(ProposalPayload proposalPayload) {
        proposalManager.addToP2PNetwork(proposalPayload);
    }

    @Override
    protected void deactivate() {
        createButton.setOnAction(null);
    }
}

