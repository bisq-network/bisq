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

package bisq.desktop.main.overlays.windows;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.BisqTextArea;
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.validation.LengthValidator;
import bisq.desktop.util.validation.PercentageNumberValidator;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.locale.Res;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.user.BlockChainExplorer;
import bisq.core.user.Preferences;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.config.Config;
import bisq.common.util.Base64;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.SignatureDecodeException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.script.Script;

import javax.inject.Inject;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;

import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.security.SignatureException;

import java.time.Instant;

import java.nio.charset.Charset;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.*;

// We don't translate here as it is for dev only purpose
public class ManualPayoutTxWindow extends Overlay<ManualPayoutTxWindow> {
    private static final int HEX_HASH_LENGTH = 32 * 2;
    private static final int HEX_PUBKEY_LENGTH = 33 * 2;
    private static final Logger log = LoggerFactory.getLogger(ManualPayoutTxWindow.class);
    private final TradeWalletService tradeWalletService;
    private final P2PService p2PService;
    private final MediationManager mediationManager;
    private final Preferences preferences;
    private final WalletsSetup walletsSetup;
    private final WalletsManager walletsManager;
    GridPane inputsGridPane;
    GridPane importTxGridPane;
    GridPane exportTxGridPane;
    GridPane signTxGridPane;
    GridPane buildTxGridPane;
    GridPane signVerifyMsgGridPane;
    CheckBox depositTxLegacy, recentTickets;
    ComboBox<String> mediationDropDown;
    ObservableList<Dispute> disputeObservableList;
    InputTextField depositTxHex;
    InputTextField amountInMultisig;
    InputTextField buyerPayoutAmount;
    InputTextField sellerPayoutAmount;
    InputTextField txFee;
    InputTextField txFeePct;
    InputTextField buyerAddressString;
    InputTextField sellerAddressString;
    InputTextField buyerPubKeyAsHex;
    InputTextField sellerPubKeyAsHex;
    InputTextField buyerSignatureAsHex;
    InputTextField sellerSignatureAsHex;
    InputTextField privateKeyHex;
    InputTextField signatureHex;
    TextArea importHex;
    TextArea exportHex;
    TextArea finalSignedTxHex;
    private ChangeListener<Boolean> txFeeListener, amountInMultisigListener, buyerPayoutAmountListener, sellerPayoutAmountListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ManualPayoutTxWindow(TradeWalletService tradeWalletService,
                                P2PService p2PService,
                                MediationManager mediationManager,
                                Preferences preferences,
                                WalletsSetup walletsSetup,
                                WalletsManager walletsManager) {
        this.tradeWalletService = tradeWalletService;
        this.p2PService = p2PService;
        this.mediationManager = mediationManager;
        this.preferences = preferences;
        this.walletsSetup = walletsSetup;
        this.walletsManager = walletsManager;
        type = Type.Attention;
    }

    public void show() {
        if (headLine == null)
            headLine = "Emergency MultiSig payout tool"; // We dont translate here as it is for dev only purpose

        width = 1068;
        createGridPane();
        addHeadLine();
        addContent();
        addButtons();
        applyStyles();
        txFeeListener = (observable, oldValue, newValue) -> {
            calculateTxFee();
        };
        buyerPayoutAmountListener = (observable, oldValue, newValue) -> {
            calculateTxFee();
        };
        sellerPayoutAmountListener = (observable, oldValue, newValue) -> {
            calculateTxFee();
        };
        amountInMultisigListener = (observable, oldValue, newValue) -> {
            calculateTxFee();
        };
        txFee.focusedProperty().addListener(txFeeListener);
        buyerPayoutAmount.focusedProperty().addListener(buyerPayoutAmountListener);
        sellerPayoutAmount.focusedProperty().addListener(sellerPayoutAmountListener);
        amountInMultisig.focusedProperty().addListener(amountInMultisigListener);
        display();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void setupKeyHandler(Scene scene) {
        if (!hideCloseButton) {
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    e.consume();
                    doClose();
                }
            });
        }
    }

    @Override
    protected void createGridPane() {
        gridPane = new GridPane();
        gridPane.setHgap(15);
        gridPane.setVgap(15);
        gridPane.setPadding(new Insets(64, 64, 64, 64));
        gridPane.setPrefWidth(width);
        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        ColumnConstraints columnConstraints2 = new ColumnConstraints();
        columnConstraints1.setPercentWidth(25);
        columnConstraints2.setPercentWidth(75);
        gridPane.getColumnConstraints().addAll(columnConstraints1, columnConstraints2);
    }

    @Override
    protected void cleanup() {
        txFee.focusedProperty().removeListener(txFeeListener);
        buyerPayoutAmount.focusedProperty().removeListener(buyerPayoutAmountListener);
        sellerPayoutAmount.focusedProperty().removeListener(sellerPayoutAmountListener);
        amountInMultisig.focusedProperty().removeListener(amountInMultisigListener);
        super.cleanup();
    }

    private void addContent() {
        rowIndex = 1;
        this.disableActionButton = true;
        addLeftPanelButtons();
        addInputsPane();
        addImportPane();
        addExportPane();
        addSignPane();
        addBuildPane();
        signVerifyMsgGridPane = addSignVerifyMsgPane(new GridPane());
        hideAllPanes();
        inputsGridPane.setVisible(true);

        // Notes:
        // Open with alt+g
        // Priv key is only visible if pw protection is removed (wallet details data (alt+j))
        // Take missing buyerPubKeyAsHex and sellerPubKeyAsHex from contract data!
        // Lookup sellerPrivateKeyAsHex associated with sellerPubKeyAsHex (or buyers) in wallet details data
        // sellerPubKeys/buyerPubKeys are auto generated if used the fields below
    }

    private void addLeftPanelButtons() {
        Button buttonInputs = new AutoTooltipButton("Inputs");
        Button buttonImport = new AutoTooltipButton("Import");
        Button buttonExport = new AutoTooltipButton("Export");
        Button buttonSign = new AutoTooltipButton("Sign");
        Button buttonBuild = new AutoTooltipButton("Build");
        Button buttonSignVerifyMsg = new AutoTooltipButton("Sign/Verify Msg");
        VBox vBox = new VBox(12, buttonInputs, buttonImport, buttonExport, buttonSign, buttonBuild, buttonSignVerifyMsg);
        vBox.getChildren().forEach(button -> ((Button) button).setPrefWidth(500));
        gridPane.add(vBox, 0, rowIndex);
        buttonInputs.getStyleClass().add("action-button");
        buttonInputs.setOnAction(e -> { // just show the inputs pane
            hideAllPanes();
            vBox.getChildren().forEach(button -> button.getStyleClass().remove("action-button"));
            buttonInputs.getStyleClass().add("action-button");
            inputsGridPane.setVisible(true);
        });
        buttonImport.setOnAction(e -> { // just show the import pane
            hideAllPanes();
            vBox.getChildren().forEach(button -> button.getStyleClass().remove("action-button"));
            buttonImport.getStyleClass().add("action-button");
            importTxGridPane.setVisible(true);
            importHex.setText("");
        });
        buttonExport.setOnAction(e -> { // show export pane and fill in the data
            hideAllPanes();
            vBox.getChildren().forEach(button -> button.getStyleClass().remove("action-button"));
            buttonExport.getStyleClass().add("action-button");
            exportTxGridPane.setVisible(true);
            exportHex.setText(generateExportText());
        });
        buttonSign.setOnAction(e -> {   // just show the sign pane
            hideAllPanes();
            vBox.getChildren().forEach(button -> button.getStyleClass().remove("action-button"));
            buttonSign.getStyleClass().add("action-button");
            signTxGridPane.setVisible(true);
            privateKeyHex.setText("");
            signatureHex.setText("");
        });
        buttonBuild.setOnAction(e -> {  // just show the build pane
            hideAllPanes();
            vBox.getChildren().forEach(button -> button.getStyleClass().remove("action-button"));
            buttonBuild.getStyleClass().add("action-button");
            buildTxGridPane.setVisible(true);
            finalSignedTxHex.setText("");
        });
        buttonSignVerifyMsg.setOnAction(e -> {  // just show the sign msg pane
            hideAllPanes();
            vBox.getChildren().forEach(button -> button.getStyleClass().remove("action-button"));
            buttonSignVerifyMsg.getStyleClass().add("action-button");
            signVerifyMsgGridPane.setVisible(true);
        });
    }

    private void addInputsPane() {
        inputsGridPane = new GridPane();
        gridPane.add(inputsGridPane, 1, rowIndex);
        int rowIndexA = 0;

        depositTxLegacy = addCheckBox(inputsGridPane, rowIndexA, "depositTxLegacy");

        Tooltip tooltip = new Tooltip(Res.get("txIdTextField.blockExplorerIcon.tooltip"));
        Label blockExplorerIcon = new Label();
        blockExplorerIcon.getStyleClass().addAll("icon", "highlight");
        blockExplorerIcon.setTooltip(tooltip);
        AwesomeDude.setIcon(blockExplorerIcon, AwesomeIcon.EXTERNAL_LINK);
        blockExplorerIcon.setMinWidth(20);
        blockExplorerIcon.setOnMouseClicked(mouseEvent -> openBlockExplorer(depositTxHex.getText()));
        depositTxHex = addInputTextField(inputsGridPane, rowIndexA, "depositTxId");
        HBox hBoxTx = new HBox(12, depositTxHex, blockExplorerIcon);
        hBoxTx.setAlignment(Pos.BASELINE_LEFT);
        hBoxTx.setPrefWidth(800);
        inputsGridPane.add(new Label(""), 0, ++rowIndexA);  // spacer
        inputsGridPane.add(hBoxTx, 0, ++rowIndexA);

        amountInMultisig = addInputTextField(inputsGridPane, ++rowIndexA, "amountInMultisig");
        inputsGridPane.add(new Label(""), 0, ++rowIndexA);  // spacer
        buyerPayoutAmount = addInputTextField(inputsGridPane, rowIndexA, "buyerPayoutAmount");
        sellerPayoutAmount = addInputTextField(inputsGridPane, rowIndexA, "sellerPayoutAmount");
        txFee = addInputTextField(inputsGridPane, rowIndexA, "Tx fee");
        txFee.setEditable(false);
        txFeePct = addInputTextField(inputsGridPane, rowIndexA, "Tx fee %");
        txFeePct.setEditable(false);
        PercentageNumberValidator validator = new PercentageNumberValidator();
        validator.setMaxValue(10D);
        txFeePct.setValidator(validator);

        HBox hBox = new HBox(12, buyerPayoutAmount, sellerPayoutAmount, txFee, txFeePct);
        hBox.setAlignment(Pos.BASELINE_LEFT);
        hBox.setPrefWidth(800);
        inputsGridPane.add(hBox, 0, ++rowIndexA);
        buyerAddressString = addInputTextField(inputsGridPane, ++rowIndexA, "buyerPayoutAddress");
        sellerAddressString = addInputTextField(inputsGridPane, ++rowIndexA, "sellerPayoutAddress");
        buyerPubKeyAsHex = addInputTextField(inputsGridPane, ++rowIndexA, "buyerPubKeyAsHex");
        sellerPubKeyAsHex = addInputTextField(inputsGridPane, ++rowIndexA, "sellerPubKeyAsHex");
        depositTxHex.setPrefWidth(800);
        depositTxLegacy.setAllowIndeterminate(false);
        depositTxLegacy.setSelected(false);
        depositTxHex.setValidator(new LengthValidator(HEX_HASH_LENGTH, HEX_HASH_LENGTH));
        buyerAddressString.setValidator(new LengthValidator(20, 80));
        sellerAddressString.setValidator(new LengthValidator(20, 80));
        buyerPubKeyAsHex.setValidator(new LengthValidator(HEX_PUBKEY_LENGTH, HEX_PUBKEY_LENGTH));
        sellerPubKeyAsHex.setValidator(new LengthValidator(HEX_PUBKEY_LENGTH, HEX_PUBKEY_LENGTH));
    }

    private void addImportPane() {
        int rowIndexB = 0;
        importTxGridPane = new GridPane();
        gridPane.add(importTxGridPane, 1, rowIndex);
        importHex = new BisqTextArea();
        importHex.setEditable(true);
        importHex.setWrapText(true);
        importHex.setPrefSize(800, 150);
        importTxGridPane.add(importHex, 0, ++rowIndexB);
        importTxGridPane.add(new Label(""), 0, ++rowIndexB);  // spacer
        Button buttonImport = new AutoTooltipButton("Import From String");
        buttonImport.setOnAction(e -> {
            // here we need to populate the "inputs" fields from the data contained in the TextArea
            if (doImport(importHex.getText())) {
                // switch back to the inputs pane
                hideAllPanes();
                inputsGridPane.setVisible(true);
            }
        });
        HBox hBox = new HBox(12, buttonImport);
        hBox.setAlignment(Pos.BASELINE_CENTER);
        hBox.setPrefWidth(800);
        importTxGridPane.add(hBox, 0, ++rowIndexB);
        importTxGridPane.add(new Label(""), 0, ++rowIndexB);  // spacer

        final Separator separator = new Separator(Orientation.HORIZONTAL);
        separator.setPadding(new Insets(10, 10, 10, 10));
        importTxGridPane.add(separator, 0, ++rowIndexB);

        importTxGridPane.add(new Label(""), 0, ++rowIndexB);  // spacer
        final Tuple2<Label, ComboBox<String>> xTuple = addTopLabelComboBox(importTxGridPane, rowIndexB, "Mediation Ticket", "", 0);
        mediationDropDown = xTuple.second;
        recentTickets = addCheckBox(importTxGridPane, rowIndexB, "Recent Tickets");
        recentTickets.setSelected(true);
        HBox hBox2 = new HBox(12, mediationDropDown, recentTickets);
        hBox2.setAlignment(Pos.BASELINE_CENTER);
        hBox2.setPrefWidth(800);
        importTxGridPane.add(hBox2, 0, ++rowIndexB);
        populateMediationTicketCombo(recentTickets.isSelected());
        recentTickets.setOnAction(e -> {
            populateMediationTicketCombo(recentTickets.isSelected());
        });
        importTxGridPane.add(new Label(""), 0, ++rowIndexB);  // spacer
        Button buttonImportTicket = new AutoTooltipButton("Import From Mediation Ticket");
        buttonImportTicket.setOnAction(e -> {
            // here we need to populate the "inputs" fields from the chosen mediator ticket
            importFromMediationTicket(mediationDropDown.getValue());
        });
        HBox hBox3 = new HBox(12, buttonImportTicket);
        hBox3.setAlignment(Pos.BASELINE_CENTER);
        hBox3.setPrefWidth(800);
        importTxGridPane.add(hBox3, 0, ++rowIndexB);
    }

    private void addExportPane() {
        exportTxGridPane = new GridPane();
        gridPane.add(exportTxGridPane, 1, rowIndex);
        exportHex = new BisqTextArea();
        exportHex.setEditable(false);
        exportHex.setWrapText(true);
        exportHex.setPrefSize(800, 250);
        exportTxGridPane.add(exportHex, 0, 1);
    }

    private void addSignPane() {
        int rowIndexB = 0;
        signTxGridPane = new GridPane();
        gridPane.add(signTxGridPane, 1, rowIndex);
        privateKeyHex = addInputTextField(inputsGridPane, ++rowIndexB, "privateKeyHex");
        signTxGridPane.add(privateKeyHex, 0, ++rowIndexB);
        signatureHex = addInputTextField(signTxGridPane, ++rowIndexB, "signatureHex");
        signatureHex.setPrefWidth(800);
        signatureHex.setEditable(false);
        Label copyIcon = new Label();
        copyIcon.setTooltip(new Tooltip(Res.get("txIdTextField.copyIcon.tooltip")));
        AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
        copyIcon.getStyleClass().addAll("icon", "highlight");
        copyIcon.setMinWidth(20);
        copyIcon.setOnMouseClicked(mouseEvent -> Utilities.copyToClipboard(signatureHex.getText()));
        HBox hBoxSig = new HBox(12, signatureHex, copyIcon);
        hBoxSig.setAlignment(Pos.BASELINE_LEFT);
        hBoxSig.setPrefWidth(800);
        signTxGridPane.add(new Label(""), 0, ++rowIndexB);  // spacer
        signTxGridPane.add(hBoxSig, 0, ++rowIndexB);
        signTxGridPane.add(new Label(""), 0, ++rowIndexB);  // spacer
        Button buttonLocate = new AutoTooltipButton("Locate key in wallet");
        Button buttonSign = new AutoTooltipButton("Generate Signature");
        HBox hBox = new HBox(12, buttonLocate, buttonSign);
        hBox.setAlignment(Pos.BASELINE_CENTER);
        hBox.setPrefWidth(800);
        signTxGridPane.add(hBox, 0, ++rowIndexB);
        buttonLocate.setOnAction(e -> {
            if (!validateInputFields()) {
                signatureHex.setText("You need to fill in the inputs tab first");
                return;
            }
            String walletInfo = walletsManager.getWalletsAsString(true);
            String privateKeyText = findPrivForPubOrAddress(walletInfo, buyerPubKeyAsHex.getText());
            if (privateKeyText == null) {
                privateKeyText = findPrivForPubOrAddress(walletInfo, sellerPubKeyAsHex.getText());
            }
            if (privateKeyText == null) {
                privateKeyText = "Not found in wallet";
            }
            privateKeyHex.setText(privateKeyText);
        });
        buttonSign.setOnAction(e -> {
            signatureHex.setText(generateSignature());
        });
    }

    private void addBuildPane() {
        buildTxGridPane = new GridPane();
        gridPane.add(buildTxGridPane, 1, rowIndex);
        int rowIndexA = 0;
        buyerSignatureAsHex = addInputTextField(buildTxGridPane, ++rowIndexA, "buyerSignatureAsHex");
        sellerSignatureAsHex = addInputTextField(buildTxGridPane, ++rowIndexA, "sellerSignatureAsHex");
        buildTxGridPane.add(new Label(""), 0, ++rowIndexA);  // spacer
        finalSignedTxHex = new BisqTextArea();
        finalSignedTxHex.setEditable(false);
        finalSignedTxHex.setWrapText(true);
        finalSignedTxHex.setPrefSize(800, 250);
        buildTxGridPane.add(finalSignedTxHex, 0, ++rowIndexA);
        buildTxGridPane.add(new Label(""), 0, ++rowIndexA);  // spacer
        Button buttonBuild = new AutoTooltipButton("Build");
        Button buttonBroadcast = new AutoTooltipButton("Broadcast");
        HBox hBox = new HBox(12, buttonBuild, buttonBroadcast);
        hBox.setAlignment(Pos.BASELINE_CENTER);
        hBox.setPrefWidth(800);
        buildTxGridPane.add(hBox, 0, ++rowIndexA);
        buttonBuild.setOnAction(e -> {
            finalSignedTxHex.setText(buildFinalTx(false));
        });
        buttonBroadcast.setOnAction(e -> {
            finalSignedTxHex.setText(buildFinalTx(true));
        });
    }

    private GridPane addSignVerifyMsgPane(GridPane myGridPane) {
        int rowIndexB = 0;
        gridPane.add(myGridPane, 1, rowIndex);
        TextArea messageText = new BisqTextArea();
        messageText.setPromptText("Message");
        messageText.setEditable(true);
        messageText.setWrapText(true);
        messageText.setPrefSize(800, 150);
        myGridPane.add(messageText, 0, ++rowIndexB);
        myGridPane.add(new Label(""), 0, ++rowIndexB);  // spacer
        InputTextField address = addInputTextField(myGridPane, ++rowIndexB, "Address");
        myGridPane.add(new Label(""), 0, ++rowIndexB);  // spacer
        TextArea messageSig = new BisqTextArea();
        messageSig.setPromptText("Signature");
        messageSig.setEditable(true);
        messageSig.setWrapText(true);
        messageSig.setPrefSize(800, 65);
        myGridPane.add(messageSig, 0, ++rowIndexB);
        myGridPane.add(new Label(""), 0, ++rowIndexB);  // spacer
        Button buttonSign = new AutoTooltipButton("Sign");
        Button buttonVerify = new AutoTooltipButton("Verify");
        HBox buttonBox = new HBox(12, buttonSign, buttonVerify);
        buttonBox.setAlignment(Pos.BASELINE_CENTER);
        buttonBox.setPrefWidth(800);
        myGridPane.add(buttonBox, 0, ++rowIndexB);

        buttonSign.setOnAction(e -> {
            String walletInfo = walletsManager.getWalletsAsString(true);
            String privKeyHex = findPrivForPubOrAddress(walletInfo, address.getText());
            if (privKeyHex == null) {
                messageSig.setText("");
                new Popup().information("Key not found in wallet").show();
            } else {
                ECKey myPrivateKey = ECKey.fromPrivate(Utils.HEX.decode(privKeyHex));
                String signatureBase64 = myPrivateKey.signMessage(messageText.getText());
                messageSig.setText(signatureBase64);
            }
        });
        buttonVerify.setOnAction(e -> {
            try {
                ECKey key = ECKey.signedMessageToKey(messageText.getText(), messageSig.getText());
                Address address1 = Address.fromKey(Config.baseCurrencyNetworkParameters(), key, Script.ScriptType.P2PKH);
                Address address2 = Address.fromKey(Config.baseCurrencyNetworkParameters(), key, Script.ScriptType.P2WPKH);
                if (address.getText().equalsIgnoreCase(address1.toString()) ||
                        address.getText().equalsIgnoreCase(address2.toString())) {
                    new Popup().information("Signature verified").show();
                } else {
                    new Popup().warning("Wrong signature").show();
                }
            } catch (SignatureException ex) {
                log.warn(ex.toString());
                new Popup().warning("Wrong signature").show();
            }
        });
        return myGridPane;
    }

    private void hideAllPanes() {
        inputsGridPane.setVisible(false);
        importTxGridPane.setVisible(false);
        exportTxGridPane.setVisible(false);
        signTxGridPane.setVisible(false);
        buildTxGridPane.setVisible(false);
        signVerifyMsgGridPane.setVisible(false);
    }

    private void populateMediationTicketCombo(boolean recentTicketsOnly) {
        Instant twoWeeksAgo = Instant.ofEpochSecond(Instant.now().getEpochSecond() - TimeUnit.DAYS.toSeconds(14));
        disputeObservableList = mediationManager.getDisputesAsObservableList();
        ObservableList<String> disputeIds = FXCollections.observableArrayList();
        for (Dispute dispute :disputeObservableList) {
            if (dispute.getDisputePayoutTxId() != null)    // only show disputes not paid out
                continue;
            if (recentTicketsOnly && dispute.getOpeningDate().toInstant().isBefore(twoWeeksAgo))
                continue;
            if (!disputeIds.contains(dispute.getTradeId()))
                disputeIds.add(dispute.getTradeId());
        }
        disputeIds.sort((a, b) -> a.compareTo(b));
        mediationDropDown.setItems(disputeIds);
    }

    private void clearInputFields() {
        depositTxHex.setText("");
        amountInMultisig.setText("");
        buyerPayoutAmount.setText("");
        sellerPayoutAmount.setText("");
        buyerAddressString.setText("");
        sellerAddressString.setText("");
        buyerPubKeyAsHex.setText("");
        sellerPubKeyAsHex.setText("");
    }

    private boolean validateInputFields() {
        return (depositTxHex.getText().length() == HEX_HASH_LENGTH &&
                amountInMultisig.getText().length() > 0 &&
                buyerPayoutAmount.getText().length() > 0 &&
                sellerPayoutAmount.getText().length() > 0 &&
                txFee.getText().length() > 0 &&
                buyerAddressString.getText().length() > 0 &&
                sellerAddressString.getText().length() > 0 &&
                buyerPubKeyAsHex.getText().length() == HEX_PUBKEY_LENGTH &&
                sellerPubKeyAsHex.getText().length() == HEX_PUBKEY_LENGTH &&
                txFeePct.getValidator().validate(txFeePct.getText()).isValid);
    }

    private boolean validateInputFieldsAndSignatures() {
        return (validateInputFields() &&
                buyerSignatureAsHex.getText().length() > 0 &&
                sellerSignatureAsHex.getText().length() > 0);
    }

    private Coin getInputFieldAsCoin(InputTextField inputTextField) {
        try {
            return Coin.parseCoin(inputTextField.getText().trim());
        } catch (RuntimeException ignore) {
        }
        return Coin.ZERO;
    }

    private void calculateTxFee() {
        if (buyerPayoutAmount.getText().length() > 0 &&
                sellerPayoutAmount.getText().length() > 0 &&
                amountInMultisig.getText().length() > 0) {
            Coin txFeeValue = getInputFieldAsCoin(amountInMultisig)
                    .subtract(getInputFieldAsCoin(buyerPayoutAmount))
                    .subtract(getInputFieldAsCoin(sellerPayoutAmount));
            txFee.setText(txFeeValue.toPlainString());
            double feePercent = (double) txFeeValue.value / getInputFieldAsCoin(amountInMultisig).value;
            txFeePct.setText(String.format("%.2f", feePercent * 100));
        }
    }

    private void openBlockExplorer(String txId) {
        if (txId.length() != HEX_HASH_LENGTH)
            return;
        if (preferences != null) {
            BlockChainExplorer blockChainExplorer = preferences.getBlockChainExplorer();
            GUIUtil.openWebPage(blockChainExplorer.txUrl + txId, false);
        }
    }

    private String findPrivForPubOrAddress(String walletInfo, String searchKey) {
        // split the walletInfo into lines, strip whitespace
        // look for lines beginning "  addr:" followed by "DeterministicKey{pub HEX=" .... ", priv HEX="
        int lineIndex = 0;
        while (lineIndex < walletInfo.length() && lineIndex != -1) {
            lineIndex = walletInfo.indexOf("  addr:", lineIndex);
            if (lineIndex == -1) {
                return  null;
            }
            int toIndex = walletInfo.indexOf("}", lineIndex);
            if (toIndex == -1) {
                return  null;
            }
            String candidate1 = walletInfo.substring(lineIndex, toIndex);
            lineIndex = toIndex;
            // do we have the search key?
            if (candidate1.indexOf(searchKey, 0) > -1) {
                int startOfPriv = candidate1.indexOf("priv HEX=", 0);
                if (startOfPriv > -1) {
                    return candidate1.substring(startOfPriv + 9, startOfPriv + 9 + HEX_HASH_LENGTH);
                }
            }
        }
        return null;
    }

    private String generateExportText() {
        // check that all input fields have been entered, except signatures
        ArrayList<String> fieldList = new ArrayList<>();
        fieldList.add(depositTxLegacy.isSelected() ? "legacy" : "segwit");
        fieldList.add(depositTxHex.getText());
        fieldList.add(amountInMultisig.getText());
        fieldList.add(buyerPayoutAmount.getText());
        fieldList.add(sellerPayoutAmount.getText());
        fieldList.add(buyerAddressString.getText());
        fieldList.add(sellerAddressString.getText());
        fieldList.add(buyerPubKeyAsHex.getText());
        fieldList.add(sellerPubKeyAsHex.getText());
        for (String item : fieldList) {
            if (item.length() < 1) {
                return "You need to fill in the inputs first";
            }
        }
        String listString = String.join(":", fieldList);
        String base64encoded = Base64.encode(listString.getBytes());
        return base64encoded;
    }

    private boolean doImport(String importedText) {
        try {
            clearInputFields();
            String decoded = new String(Base64.decode(importedText.replaceAll("\\s+", "")), Charset.forName("UTF-8"));
            String splitArray[] = decoded.split(":");
            if (splitArray.length < 9) {
                importHex.setText("Import failed - data format incorrect");
                return false;
            }
            int fieldIndex = 0;
            depositTxLegacy.setSelected(splitArray[fieldIndex++].equalsIgnoreCase("legacy"));
            depositTxHex.setText(splitArray[fieldIndex++]);
            amountInMultisig.setText(splitArray[fieldIndex++]);
            buyerPayoutAmount.setText(splitArray[fieldIndex++]);
            sellerPayoutAmount.setText(splitArray[fieldIndex++]);
            buyerAddressString.setText(splitArray[fieldIndex++]);
            sellerAddressString.setText(splitArray[fieldIndex++]);
            buyerPubKeyAsHex.setText(splitArray[fieldIndex++]);
            sellerPubKeyAsHex.setText(splitArray[fieldIndex++]);
            calculateTxFee();
        } catch (IllegalArgumentException e) {
            importHex.setText("Import failed - base64 string incorrect");
            return false;
        }
        return true;
    }

    private void importFromMediationTicket(String tradeId) {
        clearInputFields();
        Optional<Dispute> optionalDispute = mediationManager.findDispute(tradeId);
        if (optionalDispute.isPresent()) {
            Dispute dispute = optionalDispute.get();
            depositTxHex.setText(dispute.getDepositTxId());
            if (dispute.disputeResultProperty().get() != null) {
                buyerPayoutAmount.setText(dispute.disputeResultProperty().get().getBuyerPayoutAmount().toPlainString());
                sellerPayoutAmount.setText(dispute.disputeResultProperty().get().getSellerPayoutAmount().toPlainString());
            }
            buyerAddressString.setText(dispute.getContract().getBuyerPayoutAddressString());
            sellerAddressString.setText(dispute.getContract().getSellerPayoutAddressString());
            buyerPubKeyAsHex.setText(Utils.HEX.encode(dispute.getContract().getBuyerMultiSigPubKey()));
            sellerPubKeyAsHex.setText(Utils.HEX.encode(dispute.getContract().getSellerMultiSigPubKey()));
            // switch back to the inputs pane
            hideAllPanes();
            inputsGridPane.setVisible(true);
            UserThread.execute(() -> new Popup().warning("Ticket imported.  You still need to enter the multisig amount and specify if it is a legacy Tx").show());
        }
    }

    private String generateSignature() {
        calculateTxFee();
        // check that all input fields have been entered, except signatures
        if (!validateInputFields() || privateKeyHex.getText().length() < 1) {
            return "You need to fill in the inputs first";
        }

        String retVal = "";
        try {
            Tuple2<String, String> combined = tradeWalletService.emergencyBuildPayoutTxFrom2of2MultiSig(depositTxHex.getText(),
                    getInputFieldAsCoin(buyerPayoutAmount),
                    getInputFieldAsCoin(sellerPayoutAmount),
                    getInputFieldAsCoin(txFee),
                    buyerAddressString.getText(),
                    sellerAddressString.getText(),
                    buyerPubKeyAsHex.getText(),
                    sellerPubKeyAsHex.getText(),
                    depositTxLegacy.isSelected());
            String redeemScriptHex = combined.first;
            String unsignedTxHex =  combined.second;
            retVal = tradeWalletService.emergencyGenerateSignature(
                    unsignedTxHex,
                    redeemScriptHex,
                    getInputFieldAsCoin(amountInMultisig),
                    privateKeyHex.getText());
        } catch (IllegalArgumentException ee) {
            log.error(ee.toString());
            ee.printStackTrace();
            UserThread.execute(() -> new Popup().warning(ee.toString()).show());
        }
        return retVal;
    }

    private String buildFinalTx(boolean broadcastIt) {
        String retVal = "";
        calculateTxFee();
        // check that all input fields have been entered, including signatures
        if (!validateInputFieldsAndSignatures()) {
            retVal = "You need to fill in the inputs first";
        } else {
            try {
                // grab data from the inputs pane, build an unsigned tx and write it to the TextArea
                Tuple2<String, String> combined = tradeWalletService.emergencyBuildPayoutTxFrom2of2MultiSig(depositTxHex.getText(),
                        getInputFieldAsCoin(buyerPayoutAmount),
                        getInputFieldAsCoin(sellerPayoutAmount),
                        getInputFieldAsCoin(txFee),
                        buyerAddressString.getText(),
                        sellerAddressString.getText(),
                        buyerPubKeyAsHex.getText(),
                        sellerPubKeyAsHex.getText(),
                        depositTxLegacy.isSelected());
                String redeemScriptHex = combined.first;
                String unsignedTxHex =  combined.second;
                Tuple2<String, String> txIdAndHex = tradeWalletService.emergencyApplySignatureToPayoutTxFrom2of2MultiSig(
                        unsignedTxHex,
                        redeemScriptHex,
                        buyerSignatureAsHex.getText(),
                        sellerSignatureAsHex.getText(),
                        depositTxLegacy.isSelected());
                retVal = "txId:{" + txIdAndHex.first + "}\r\ntxHex:{" + txIdAndHex.second + "}";

                if (broadcastIt) {
                    TxBroadcaster.Callback callback = new TxBroadcaster.Callback() {
                        @Override
                        public void onSuccess(@Nullable Transaction result) {
                            log.info("onSuccess");
                            UserThread.execute(() -> {
                                String txId = result != null ? result.getTxId().toString() : "null";
                                new Popup().information("Transaction successfully published. Transaction ID: " + txId).show();
                            });
                        }
                        @Override
                        public void onFailure(TxBroadcastException exception) {
                            log.error(exception.toString());
                            UserThread.execute(() -> new Popup().warning(exception.toString()).show());
                        }
                    };

                    if (GUIUtil.isReadyForTxBroadcastOrShowPopup(p2PService, walletsSetup)) {
                        try {
                            tradeWalletService.emergencyPublishPayoutTxFrom2of2MultiSig(
                                    txIdAndHex.second,
                                    callback);
                        } catch (AddressFormatException | WalletException | TransactionVerificationException ee) {
                            log.error(ee.toString());
                            ee.printStackTrace();
                            UserThread.execute(() -> new Popup().warning(ee.toString()).show());
                        }
                    }
                }
            } catch (IllegalArgumentException | SignatureDecodeException | VerificationException ee) {
                log.error(ee.toString());
                ee.printStackTrace();
                retVal = ee.toString();
            }
        }
        return retVal;
    }

}
