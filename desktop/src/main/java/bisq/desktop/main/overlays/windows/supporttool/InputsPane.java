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

package bisq.desktop.main.overlays.windows.supporttool;

import bisq.desktop.components.InputTextField;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.validation.LengthValidator;
import bisq.desktop.util.validation.PercentageNumberValidator;

import bisq.core.locale.Res;
import bisq.core.support.dispute.Dispute;

import bisq.common.util.Base64;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Utils;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import javafx.geometry.Pos;

import javafx.beans.value.ChangeListener;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;

import lombok.Getter;

import static bisq.desktop.util.FormBuilder.addCheckBox;
import static bisq.desktop.util.FormBuilder.addInputTextField;

public class InputsPane extends CommonPane {

    @Getter private final CheckBox depositTxLegacy;
    @Getter private final InputTextField depositTxHex;
    @Getter private final InputTextField amountInMultisig;
    @Getter private final InputTextField buyerPayoutAmount;
    @Getter private final InputTextField sellerPayoutAmount;
    @Getter private final InputTextField txFee;
    private final InputTextField txFeePct;
    @Getter private final InputTextField buyerAddressString;
    @Getter private final InputTextField sellerAddressString;
    @Getter private final InputTextField buyerPubKeyAsHex;
    @Getter private final InputTextField sellerPubKeyAsHex;
    private ChangeListener<Boolean> txFeeListener, amountInMultisigListener, buyerPayoutAmountListener, sellerPayoutAmountListener;

    InputsPane() {
        int rowIndexA = 0;

        depositTxLegacy = addCheckBox(this, rowIndexA, "depositTxLegacy");
        depositTxHex = addInputTextField(this, rowIndexA, "depositTxId");
        Tooltip tooltip = new Tooltip(Res.get("txIdTextField.blockExplorerIcon.tooltip"));
        Label blockExplorerIcon = new Label();
        blockExplorerIcon.getStyleClass().addAll("icon", "highlight");
        blockExplorerIcon.setTooltip(tooltip);
        AwesomeDude.setIcon(blockExplorerIcon, AwesomeIcon.EXTERNAL_LINK);
        blockExplorerIcon.setMinWidth(20);
        blockExplorerIcon.setOnMouseClicked(mouseEvent -> {
            if (depositTxHex.getText().length() == HEX_HASH_LENGTH) {
                GUIUtil.openTxInBlockExplorer(depositTxHex.getText());
            }
        });
        HBox hBoxTx = new HBox(12, depositTxHex, blockExplorerIcon);
        hBoxTx.setAlignment(Pos.BASELINE_LEFT);
        hBoxTx.setPrefWidth(800);
        add(new Label(""), 0, ++rowIndexA);  // spacer
        add(hBoxTx, 0, ++rowIndexA);

        amountInMultisig = addInputTextField(this, ++rowIndexA, "amountInMultisig");
        add(new Label(""), 0, ++rowIndexA);  // spacer
        buyerPayoutAmount = addInputTextField(this, rowIndexA, "buyerPayoutAmount");
        sellerPayoutAmount = addInputTextField(this, rowIndexA, "sellerPayoutAmount");
        txFee = addInputTextField(this, rowIndexA, "Tx fee");
        txFee.setEditable(false);
        txFeePct = addInputTextField(this, rowIndexA, "Tx fee %");
        txFeePct.setEditable(false);
        PercentageNumberValidator validator = new PercentageNumberValidator();
        validator.setMaxValue(10D);
        txFeePct.setValidator(validator);

        HBox hBox = new HBox(12, buyerPayoutAmount, sellerPayoutAmount, txFee, txFeePct);
        hBox.setAlignment(Pos.BASELINE_LEFT);
        hBox.setPrefWidth(800);
        add(hBox, 0, ++rowIndexA);
        buyerAddressString = addInputTextField(this, ++rowIndexA, "buyerPayoutAddress");
        sellerAddressString = addInputTextField(this, ++rowIndexA, "sellerPayoutAddress");
        buyerPubKeyAsHex = addInputTextField(this, ++rowIndexA, "buyerPubKeyAsHex");
        sellerPubKeyAsHex = addInputTextField(this, ++rowIndexA, "sellerPubKeyAsHex");
        depositTxHex.setPrefWidth(800);
        depositTxLegacy.setAllowIndeterminate(false);
        depositTxLegacy.setSelected(false);
        depositTxHex.setValidator(new LengthValidator(HEX_HASH_LENGTH, HEX_HASH_LENGTH));
        buyerAddressString.setValidator(new LengthValidator(20, 80));
        sellerAddressString.setValidator(new LengthValidator(20, 80));
        buyerPubKeyAsHex.setValidator(new LengthValidator(HEX_PUBKEY_LENGTH, HEX_PUBKEY_LENGTH));
        sellerPubKeyAsHex.setValidator(new LengthValidator(HEX_PUBKEY_LENGTH, HEX_PUBKEY_LENGTH));
        onShow();
    }

    @Override
    public String getName() {
        return "Inputs";
    }

    @Override
    public void cleanup() {
        txFee.focusedProperty().removeListener(txFeeListener);
        buyerPayoutAmount.focusedProperty().removeListener(buyerPayoutAmountListener);
        sellerPayoutAmount.focusedProperty().removeListener(sellerPayoutAmountListener);
        amountInMultisig.focusedProperty().removeListener(amountInMultisigListener);
        super.cleanup();
    }

    public void calculateTxFee() {
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

    public boolean validateInputFields() {
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

    public String generateExportText() {
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

    public boolean doImport(String importedText) {
        try {
            this.clearInputFields();
            String decoded = new String(Base64.decode(importedText.replaceAll("\\s+", "")), StandardCharsets.UTF_8);
            String splitArray[] = decoded.split(":");
            if (splitArray.length < 9) {
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
            this.calculateTxFee();
        } catch (IllegalArgumentException e) {
            return false;
        }
        return true;
    }

    public void importFromMediationTicket(Dispute dispute) {
        this.clearInputFields();
        depositTxHex.setText(dispute.getDepositTxId());
        if (dispute.disputeResultProperty().get() != null) {
            buyerPayoutAmount.setText(dispute.disputeResultProperty().get().getBuyerPayoutAmount().toPlainString());
            sellerPayoutAmount.setText(dispute.disputeResultProperty().get().getSellerPayoutAmount().toPlainString());
        }
        buyerAddressString.setText(dispute.getContract().getBuyerPayoutAddressString());
        sellerAddressString.setText(dispute.getContract().getSellerPayoutAddressString());
        buyerPubKeyAsHex.setText(Utils.HEX.encode(dispute.getContract().getBuyerMultiSigPubKey()));
        sellerPubKeyAsHex.setText(Utils.HEX.encode(dispute.getContract().getSellerMultiSigPubKey()));
    }

    public static Coin getInputFieldAsCoin(InputTextField inputTextField) {
        try {
            return Coin.parseCoin(inputTextField.getText().trim());
        } catch (RuntimeException ignore) {
        }
        return Coin.ZERO;
    }

    private void onShow() {
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
}
