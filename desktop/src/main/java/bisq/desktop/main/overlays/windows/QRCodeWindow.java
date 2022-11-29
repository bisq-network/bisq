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

import javafx.geometry.HPos;

import java.io.ByteArrayInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QRCodeWindow extends Overlay<QRCodeWindow> {
    private static final Logger log = LoggerFactory.getLogger(QRCodeWindow.class);
    private final ImageView qrCodeImageView;
    private final String bitcoinAddressOrURI;

    public QRCodeWindow(String bitcoinAddressOrURI) {
        this.bitcoinAddressOrURI = bitcoinAddressOrURI;
        final byte[] imageBytes = QRCode
                .from(bitcoinAddressOrURI)
                .withSize(250, 250)
                .to(ImageType.PNG)
                .stream()
                .toByteArray();
        Image qrImage = new Image(new ByteArrayInputStream(imageBytes));
        qrCodeImageView = new ImageView(qrImage);

        type = Type.Information;
        width = 468;
        headLine(Res.get("qRCodeWindow.headline"));
        message(Res.get("qRCodeWindow.msg"));
    }

    @Override
    public void show() {
        createGridPane();
        addHeadLine();

        GridPane.setRowIndex(qrCodeImageView, ++rowIndex);
        GridPane.setColumnSpan(qrCodeImageView, 2);
        GridPane.setHalignment(qrCodeImageView, HPos.CENTER);
        gridPane.getChildren().add(qrCodeImageView);

        message = bitcoinAddressOrURI.replace("%20", " ").replace("?", "\n?").replace("&", "\n&");
        setTruncatedMessage();
        addMessage();
        GridPane.setHalignment(messageLabel, HPos.CENTER);

        addButtons();
        applyStyles();
        display();
    }
}
