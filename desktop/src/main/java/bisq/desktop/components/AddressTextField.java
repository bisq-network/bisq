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

package bisq.desktop.components;

import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.GUIUtil;

import bisq.core.locale.Res;

import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import com.jfoenix.controls.JFXTextField;

import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddressTextField extends AnchorPane {
    private static final Logger log = LoggerFactory.getLogger(AddressTextField.class);

    private final StringProperty address = new SimpleStringProperty();
    private final StringProperty paymentLabel = new SimpleStringProperty();
    private final ObjectProperty<Coin> amountAsCoin = new SimpleObjectProperty<>(Coin.ZERO);
    private boolean wasPrimaryButtonDown;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AddressTextField(String label) {
        JFXTextField textField = new BisqTextField();
        textField.setId("address-text-field");
        textField.setEditable(false);
        textField.setLabelFloat(true);
        textField.setPromptText(label);

        textField.textProperty().bind(address);
        String tooltipText = Res.get("addressTextField.openWallet");
        textField.setTooltip(new Tooltip(tooltipText));

        textField.setOnMousePressed(event -> wasPrimaryButtonDown = event.isPrimaryButtonDown());
        textField.setOnMouseReleased(event -> {
            if (wasPrimaryButtonDown)
                GUIUtil.showFeeInfoBeforeExecute(AddressTextField.this::openWallet);

            wasPrimaryButtonDown = false;
        });

        textField.focusTraversableProperty().set(focusTraversableProperty().get());
        Label extWalletIcon = new Label();
        extWalletIcon.setLayoutY(3);
        extWalletIcon.getStyleClass().addAll("icon", "highlight");
        extWalletIcon.setTooltip(new Tooltip(tooltipText));
        AwesomeDude.setIcon(extWalletIcon, AwesomeIcon.SIGNIN);
        extWalletIcon.setOnMouseClicked(e -> GUIUtil.showFeeInfoBeforeExecute(this::openWallet));

        Label copyIcon = new Label();
        copyIcon.setLayoutY(3);
        copyIcon.getStyleClass().addAll("icon", "highlight");
        Tooltip.install(copyIcon, new Tooltip(Res.get("addressTextField.copyToClipboard")));
        AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
        copyIcon.setOnMouseClicked(e -> GUIUtil.showFeeInfoBeforeExecute(() -> {
            if (address.get() != null && address.get().length() > 0)
                Utilities.copyToClipboard(address.get());
        }));

        AnchorPane.setRightAnchor(copyIcon, 30.0);
        AnchorPane.setRightAnchor(extWalletIcon, 5.0);
        AnchorPane.setRightAnchor(textField, 55.0);
        AnchorPane.setLeftAnchor(textField, 0.0);

        getChildren().addAll(textField, copyIcon, extWalletIcon);
    }

    private void openWallet() {
        try {
            Utilities.openURI(URI.create(getBitcoinURI()));
        } catch (Exception e) {
            log.warn(e.getMessage());
            new Popup().warning(Res.get("addressTextField.openWallet.failed")).show();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters/Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setAddress(String address) {
        this.address.set(address);
    }

    public String getAddress() {
        return address.get();
    }

    public StringProperty addressProperty() {
        return address;
    }

    public Coin getAmountAsCoin() {
        return amountAsCoin.get();
    }

    public ObjectProperty<Coin> amountAsCoinProperty() {
        return amountAsCoin;
    }

    public void setAmountAsCoin(Coin amountAsCoin) {
        this.amountAsCoin.set(amountAsCoin);
    }

    public String getPaymentLabel() {
        return paymentLabel.get();
    }

    public StringProperty paymentLabelProperty() {
        return paymentLabel;
    }

    public void setPaymentLabel(String paymentLabel) {
        this.paymentLabel.set(paymentLabel);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private String getBitcoinURI() {
        if (amountAsCoin.get().isNegative()) {
            log.warn("Amount must not be negative");
            setAmountAsCoin(Coin.ZERO);
        }
        return GUIUtil.getBitcoinURI(address.get(), amountAsCoin.get(),
                paymentLabel.get());
    }
}
