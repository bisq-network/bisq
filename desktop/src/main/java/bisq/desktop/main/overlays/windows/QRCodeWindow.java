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

import bisq.desktop.main.overlays.Overlay;

import bisq.core.locale.Res;

import net.glxn.qrgen.QRCode;
import net.glxn.qrgen.image.ImageType;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;

import javafx.geometry.HPos;

import java.io.ByteArrayInputStream;

public class QRCodeWindow extends Overlay<QRCodeWindow> {
    private ImageView qrCodeImageView;
    private final String bitcoinAddressOrURI;
    private boolean showTextRepresentation = true;
    private double prefWidth = 500, prefHeight = 500;

    public QRCodeWindow(String bitcoinAddressOrURI) {
        this.bitcoinAddressOrURI = bitcoinAddressOrURI;
        type = Type.Information;
        headLine(Res.get("qRCodeWindow.headline"));
        message(Res.get("qRCodeWindow.msg"));
    }

    @Override
    public void show() {
        createGridPane();
        gridPane.setPrefWidth(prefWidth);
        gridPane.setMinHeight(prefHeight);
        addHeadLine();

        Region spacer = new Region();
        spacer.setMinHeight(prefHeight / 8);
        gridPane.add(spacer, 0, ++rowIndex);

        final byte[] imageBytes = QRCode
                .from(bitcoinAddressOrURI)
                .withSize((int) prefWidth / 2, (int) prefHeight / 2)
                .to(ImageType.PNG)
                .stream()
                .toByteArray();
        Image qrImage = new Image(new ByteArrayInputStream(imageBytes));
        qrCodeImageView = new ImageView(qrImage);

        GridPane.setRowIndex(qrCodeImageView, ++rowIndex);
        GridPane.setColumnSpan(qrCodeImageView, 2);
        GridPane.setHalignment(qrCodeImageView, HPos.CENTER);
        gridPane.getChildren().add(qrCodeImageView);

        message = bitcoinAddressOrURI.replace("%20", " ").replace("?", "\n?").replace("&", "\n&");
        setTruncatedMessage();
        if (showTextRepresentation) {
            addMessage();
            GridPane.setHalignment(messageLabel, HPos.CENTER);
        }

        addButtons();
        applyStyles();
        display();
    }

    public QRCodeWindow withoutText() {
        showTextRepresentation = false;
        return this;
    }

    public QRCodeWindow setWindowDimensions(double width, double height) {
        this.prefWidth = width;
        this.prefHeight = height;
        return this;
    }
}
