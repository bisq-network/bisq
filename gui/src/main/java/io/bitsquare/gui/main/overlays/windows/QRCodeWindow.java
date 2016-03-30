package io.bitsquare.gui.main.overlays.windows;

import io.bitsquare.gui.main.overlays.Overlay;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import net.glxn.qrgen.QRCode;
import net.glxn.qrgen.image.ImageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;

public class QRCodeWindow extends Overlay<QRCodeWindow> {
    private static final Logger log = LoggerFactory.getLogger(QRCodeWindow.class);
    private final ImageView qrCodeImageView;
    private String bitcoinURI;

    public QRCodeWindow(String bitcoinURI) {
        this.bitcoinURI = bitcoinURI;
        final byte[] imageBytes = QRCode
                .from(bitcoinURI)
                .withSize(250, 250)
                .to(ImageType.PNG)
                .stream()
                .toByteArray();
        Image qrImage = new Image(new ByteArrayInputStream(imageBytes));
        qrCodeImageView = new ImageView(qrImage);

        type = Type.Information;
        width = 400;
        headLine("QR-Code");
        message("Please use that QR-Code for funding your Bitsquare wallet from your external wallet.");
    }

    @Override
    public void show() {
        createGridPane();
        addHeadLine();
        addSeparator();
        addMessage();

        GridPane.setRowIndex(qrCodeImageView, ++rowIndex);
        GridPane.setColumnSpan(qrCodeImageView, 2);
        GridPane.setHalignment(qrCodeImageView, HPos.CENTER);
        gridPane.getChildren().add(qrCodeImageView);

        Label infoLabel = new Label("Payment request:\n" + bitcoinURI);
        infoLabel.setMouseTransparent(true);
        infoLabel.setWrapText(true);
        infoLabel.setId("popup-qr-code-info");
        GridPane.setHalignment(infoLabel, HPos.CENTER);
        GridPane.setHgrow(infoLabel, Priority.ALWAYS);
        GridPane.setMargin(infoLabel, new Insets(3, 0, 0, 0));
        GridPane.setRowIndex(infoLabel, ++rowIndex);
        GridPane.setColumnIndex(infoLabel, 0);
        GridPane.setColumnSpan(infoLabel, 2);
        gridPane.getChildren().add(infoLabel);

        addCloseButton();
        applyStyles();
        display();
    }
}
