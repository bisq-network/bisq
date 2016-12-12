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
import io.bitsquare.dao.proposals.Proposal;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Layout;
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
import static io.bitsquare.gui.util.FormBuilder.*;

@FxmlView
public class CreateProposalView extends ActivatableView<GridPane, Void> {

    private final NodeAddress nodeAddress;
    private final PublicKey p2pStorageSignaturePubKey;
    private InputTextField nameTextField, titleTextField, categoryTextField, descriptionTextField, linkTextField,
            startDateTextField, endDateTextField, requestedBTCTextField, btcAddressTextField;
    private Button createButton;

    private final SquWalletService squWalletService;
    private final BtcWalletService btcWalletService;
    private FeeService feeService;
    private final P2PService p2PService;
    private final BSFormatter btcFormatter;
    private int gridRow = 0;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private CreateProposalView(SquWalletService squWalletService, BtcWalletService btcWalletService, FeeService feeService, P2PService p2PService, KeyRing keyRing, BSFormatter btcFormatter) {
        this.squWalletService = squWalletService;
        this.btcWalletService = btcWalletService;
        this.feeService = feeService;
        this.p2PService = p2PService;
        this.btcFormatter = btcFormatter;

        nodeAddress = p2PService.getAddress();
        p2pStorageSignaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
    }


    @Override
    public void initialize() {
        addTitledGroupBg(root, gridRow, 9, "Create new funding proposal");
        nameTextField = addLabelInputTextField(root, gridRow, "Name/nickname:", Layout.FIRST_ROW_DISTANCE).second;
        titleTextField = addLabelInputTextField(root, ++gridRow, "Title:").second;
        categoryTextField = addLabelInputTextField(root, ++gridRow, "Category:").second;
        descriptionTextField = addLabelInputTextField(root, ++gridRow, "Description:").second;
        linkTextField = addLabelInputTextField(root, ++gridRow, "Link to detail info:").second;
        startDateTextField = addLabelInputTextField(root, ++gridRow, "Start date:").second;
        endDateTextField = addLabelInputTextField(root, ++gridRow, "Delivery date:").second;
        requestedBTCTextField = addLabelInputTextField(root, ++gridRow, "Requested funds in BTC:").second;
        btcAddressTextField = addLabelInputTextField(root, ++gridRow, "Bitcoin address:").second;
        createButton = addButtonAfterGroup(root, ++gridRow, "Create proposal");

        //TODO
        nameTextField.setText("Mock name");
        titleTextField.setText("Mock Title");
        categoryTextField.setText("Mock Category");
        descriptionTextField.setText("Mock Description");
        linkTextField.setText("Mock Link");
        startDateTextField.setText("Mock Start date");
        endDateTextField.setText("Mock Delivery date");
        requestedBTCTextField.setText("Mock Requested funds");
        btcAddressTextField.setText("Mock Bitcoin address");
    }

    @Override
    protected void activate() {
        createButton.setOnAction(event -> {
            DeterministicKey squKeyPair = squWalletService.getWallet().freshKey(KeyChain.KeyPurpose.AUTHENTICATION);
            checkNotNull(squKeyPair, "squKeyPair must not be null");

            //TODO
            Date startDate = new Date();
            Date endDate = new Date();

            Proposal proposal = new Proposal(UUID.randomUUID().toString(),
                    nameTextField.getText(),
                    titleTextField.getText(),
                    categoryTextField.getText(),
                    descriptionTextField.getText(),
                    linkTextField.getText(),
                    startDate,
                    endDate,
                    btcFormatter.parseToCoin(requestedBTCTextField.getText()),
                    btcAddressTextField.getText(),
                    nodeAddress,
                    p2pStorageSignaturePubKey,
                    squKeyPair.getPubKey()
            );
            Sha256Hash hash = Sha256Hash.of(ByteArrayUtils.objectToByteArray(proposal));
            proposal.setSignature(squKeyPair.sign(hash).encodeToDER());
            hash = Sha256Hash.of(ByteArrayUtils.objectToByteArray(proposal));
            proposal.setHash(hash.getBytes());

            try {
                Coin createProposalFee = feeService.getCreateProposalFee();
                Transaction preparedSendTx = squWalletService.getPreparedProposalFeeTx(createProposalFee);
                Transaction txWithBtcFee = btcWalletService.addInputAndOutputToPreparedSquProposalFeeTx(preparedSendTx, hash.getBytes());
                squWalletService.signAndBroadcastProposalFeeTx(txWithBtcFee, new FutureCallback<Transaction>() {
                    @Override
                    public void onSuccess(@Nullable Transaction result) {
                        checkNotNull(result, "Transaction must not be null at signAndBroadcastProposalFeeTx callback.");
                        publishToP2PNetwork(proposal);
                        clearForm();
                        new Popup<>().confirmation("Your proposal has been successfully published.").show();
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

    private void clearForm() {
        nameTextField.setText("");
        titleTextField.setText("");
        categoryTextField.setText("");
        descriptionTextField.setText("");
        linkTextField.setText("");
        startDateTextField.setText("");
        endDateTextField.setText("");
        requestedBTCTextField.setText("");
        btcAddressTextField.setText("");
    }

    private void publishToP2PNetwork(Proposal proposal) {
        p2PService.addData(proposal, true);
    }

    @Override
    protected void deactivate() {
        createButton.setOnAction(null);
    }
}

