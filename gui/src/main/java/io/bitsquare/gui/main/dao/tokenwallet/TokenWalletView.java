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

package io.bitsquare.gui.main.dao.tokenwallet;

import io.bitsquare.app.DevFlags;
import io.bitsquare.btc.TokenWalletService;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.gui.common.model.Activatable;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.AddressTextField;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.main.overlays.windows.QRCodeWindow;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.GUIUtil;
import io.bitsquare.gui.util.Layout;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import net.glxn.qrgen.QRCode;
import net.glxn.qrgen.image.ImageType;
import org.bitcoinj.core.Coin;
import org.bitcoinj.uri.BitcoinURI;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

import static io.bitsquare.gui.util.FormBuilder.*;

@FxmlView
public class TokenWalletView extends ActivatableViewAndModel<GridPane, Activatable> {

    private int gridRow = 0;
    private ImageView qrCodeImageView;
    private AddressTextField addressTextField;
    private Label addressLabel, amountLabel;
    private Label qrCodeLabel;
    private InputTextField amountTextField;
    private final String paymentLabelString;
    private final TokenWalletService tokenWalletService;
    private final BSFormatter formatter;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TokenWalletView(TokenWalletService tokenWalletService, BSFormatter formatter) {
        super();
        this.tokenWalletService = tokenWalletService;
        this.formatter = formatter;
        paymentLabelString = "Fund Bitsquare token wallet";
    }

    @Override
    public void initialize() {
        addTitledGroupBg(root, gridRow, 3, "Fund your token wallet");

        qrCodeLabel = addLabel(root, gridRow, "", 0);
        //GridPane.setMargin(qrCodeLabel, new Insets(Layout.FIRST_ROW_DISTANCE - 9, 0, 0, 5));

        qrCodeImageView = new ImageView();
        qrCodeImageView.setStyle("-fx-cursor: hand;");
        Tooltip.install(qrCodeImageView, new Tooltip("Open large QR-Code window"));
        qrCodeImageView.setOnMouseClicked(e -> GUIUtil.showFeeInfoBeforeExecute(
                () -> UserThread.runAfter(
                        () -> new QRCodeWindow(getBitcoinURI()).show(),
                        200, TimeUnit.MILLISECONDS)
        ));
        GridPane.setRowIndex(qrCodeImageView, gridRow);
        GridPane.setColumnIndex(qrCodeImageView, 1);
        GridPane.setMargin(qrCodeImageView, new Insets(Layout.FIRST_ROW_DISTANCE, 0, 0, 0));
        root.getChildren().add(qrCodeImageView);

        Tuple2<Label, AddressTextField> addressTuple = addLabelAddressTextField(root, ++gridRow, "Address:");
        addressLabel = addressTuple.first;
        addressTextField = addressTuple.second;
        addressTextField.setPaymentLabel(paymentLabelString);


        Tuple2<Label, InputTextField> amountTuple = addLabelInputTextField(root, ++gridRow, "Amount (optional):");
        amountLabel = amountTuple.first;
        amountTextField = amountTuple.second;
        if (DevFlags.DEV_MODE)
            amountTextField.setText("10");
    }


    @Override
    protected void activate() {
        //TODO
        addressTextField.setAddress(tokenWalletService.getWallet().freshReceiveAddress().toString());
        updateQRCode();
    }

    @Override
    protected void deactivate() {
    }


    private void updateQRCode() {
        if (addressTextField.getAddress() != null && !addressTextField.getAddress().isEmpty()) {
            final byte[] imageBytes = QRCode
                    .from(getBitcoinURI())
                    .withSize(150, 150) // code has 41 elements 8 px is border with 150 we get 3x scale and min. border
                    .to(ImageType.PNG)
                    .stream()
                    .toByteArray();
            Image qrImage = new Image(new ByteArrayInputStream(imageBytes));
            qrCodeImageView.setImage(qrImage);
        }
    }

    private Coin getAmountAsCoin() {
        return formatter.parseToCoin(amountTextField.getText());
    }

    @NotNull
    private String getBitcoinURI() {
        return BitcoinURI.convertToBitcoinURI(addressTextField.getAddress(),
                getAmountAsCoin(),
                paymentLabelString,
                null);
    }
}
