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

package io.bitsquare.gui.main.dao.wallet.receive;

import io.bitsquare.app.DevFlags;
import io.bitsquare.btc.BitcoinWalletService;
import io.bitsquare.common.UserThread;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.AddressTextField;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.main.overlays.windows.QRCodeWindow;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.GUIUtil;
import io.bitsquare.gui.util.Layout;
import javafx.geometry.Insets;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import net.glxn.qrgen.QRCode;
import net.glxn.qrgen.image.ImageType;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.uri.BitcoinURI;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

import static io.bitsquare.gui.util.FormBuilder.*;

@FxmlView
public class TokenReceiveView extends ActivatableView<GridPane, Void> {
    private final Wallet tokenWallet;
    private final BSFormatter formatter;
    private int gridRow = 0;
    private ImageView qrCodeImageView;
    private AddressTextField addressTextField;
    private InputTextField amountTextField;
    private final String paymentLabelString;
    private Subscription amountTextFieldSubscription;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private TokenReceiveView(BitcoinWalletService walletService, BSFormatter formatter) {
        tokenWallet = walletService.getTokenWallet();
        this.formatter = formatter;
        paymentLabelString = "Fund Bitsquare token wallet";
    }

    @Override
    public void initialize() {
        addTitledGroupBg(root, gridRow, 3, "Fund your token wallet");

        qrCodeImageView = new ImageView();
        qrCodeImageView.setStyle("-fx-cursor: hand;");
        Tooltip.install(qrCodeImageView, new Tooltip("Open large QR-Code window"));
        GridPane.setRowIndex(qrCodeImageView, gridRow);
        GridPane.setColumnIndex(qrCodeImageView, 1);
        GridPane.setMargin(qrCodeImageView, new Insets(Layout.FIRST_ROW_DISTANCE, 0, 0, 0));
        root.getChildren().add(qrCodeImageView);

        addressTextField = addLabelAddressTextField(root, ++gridRow, "Address:").second;
        addressTextField.setPaymentLabel(paymentLabelString);

        amountTextField = addLabelInputTextField(root, ++gridRow, "Amount (optional):").second;
        if (DevFlags.DEV_MODE)
            amountTextField.setText("10");
    }

    @Override
    protected void activate() {
        amountTextFieldSubscription = EasyBind.subscribe(amountTextField.textProperty(), t -> {
            addressTextField.setAmountAsCoin(formatter.parseToCoin(t));
            updateQRCode();
        });
        qrCodeImageView.setOnMouseClicked(e -> GUIUtil.showFeeInfoBeforeExecute(
                () -> UserThread.runAfter(
                        () -> new QRCodeWindow(getBitcoinURI()).show(),
                        200, TimeUnit.MILLISECONDS)
        ));
        addressTextField.setAddress(tokenWallet.freshReceiveAddress().toString());
        updateQRCode();
    }

    @Override
    protected void deactivate() {
        qrCodeImageView.setOnMouseClicked(null);
        amountTextFieldSubscription.unsubscribe();
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

