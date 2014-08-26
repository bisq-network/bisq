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

package io.bitsquare.gui.components.btc;

import io.bitsquare.BitSquare;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.util.BitSquareFormatter;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.uri.BitcoinURI;

import java.awt.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.net.URI;

import javafx.scene.control.Label;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import net.glxn.qrgen.QRCode;
import net.glxn.qrgen.image.ImageType;

import org.controlsfx.control.PopOver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddressTextField extends AnchorPane {
    private static final Logger log = LoggerFactory.getLogger(AddressTextField.class);

    private final Label copyIcon;
    private final Label addressLabel;
    private final Label qrCode;
    private String address;
    private String amountToPay;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AddressTextField() {
        addressLabel = new Label();
        addressLabel.setFocusTraversable(false);
        addressLabel.setId("address-label");

        copyIcon = new Label();
        copyIcon.setLayoutY(3);
        copyIcon.setId("copy-icon");
        Tooltip.install(copyIcon, new Tooltip("Copy address to clipboard"));
        AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
        copyIcon.setOnMouseClicked(e -> {
            if (address != null && address.length() > 0) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(address);
                clipboard.setContent(content);
            }
        });


        qrCode = new Label();
        qrCode.setId("qr-code-icon");
        qrCode.setLayoutY(3);
        AwesomeDude.setIcon(qrCode, AwesomeIcon.QRCODE);
        Tooltip.install(qrCode, new Tooltip("Show QR code for this address"));
        qrCode.setOnMouseClicked(e -> {
            if (address != null && address.length() > 0) {
                final byte[] imageBytes = QRCode
                        .from(getBitcoinURI())
                        .withSize(300, 220)
                        .to(ImageType.PNG)
                        .stream()
                        .toByteArray();
                Image qrImage = new Image(new ByteArrayInputStream(imageBytes));
                ImageView view = new ImageView(qrImage);

                Pane pane = new Pane(view);
                pane.setPrefSize(320, 240);
                view.relocate(10, 10);

                PopOver popOver = new PopOver(pane);
                popOver.setDetachedTitle("Scan QR code for this address");
                popOver.setDetached(true);

                Window window = getScene().getWindow();
                double x = Math.round(window.getX() + (window.getWidth() - 320) / 2);
                double y = Math.round(window.getY() + (window.getHeight() - 240) / 2);
                popOver.show(getScene().getWindow(), x, y);
            }
        });

        AnchorPane.setRightAnchor(qrCode, 5.0);
        AnchorPane.setRightAnchor(copyIcon, 30.0);
        AnchorPane.setRightAnchor(addressLabel, 55.0);
        AnchorPane.setLeftAnchor(addressLabel, 0.0);

        getChildren().addAll(addressLabel, copyIcon, qrCode);

        addressLabel.setOnMouseClicked(mouseEvent -> {
            try {
                if (address != null)
                    Desktop.getDesktop().browse(URI.create(getBitcoinURI()));
            } catch (IOException e) {
                log.warn(e.getMessage());
                Popups.openWarningPopup("Opening wallet app failed", "Perhaps you don't have one installed?");
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters/Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setAddress(String address) {
        this.address = address;
        addressLabel.setText(address);
    }

    public void setAmountToPay(String amountToPay) {
        this.amountToPay = amountToPay;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private String getBitcoinURI() {
        Coin d = BitSquareFormatter.parseToCoin(amountToPay);
        return BitcoinURI.convertToBitcoinURI(
                address, BitSquareFormatter.parseToCoin(amountToPay), BitSquare.getAppName(), null);
    }


}
