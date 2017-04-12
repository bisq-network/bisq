/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.dao.wallet.receive;

import io.bisq.common.UserThread;
import io.bisq.common.app.DevEnv;
import io.bisq.common.locale.Res;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.gui.common.view.ActivatableView;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.components.AddressTextField;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.main.dao.wallet.BsqBalanceUtil;
import io.bisq.gui.main.overlays.windows.QRCodeWindow;
import io.bisq.gui.util.BsqFormatter;
import io.bisq.gui.util.GUIUtil;
import io.bisq.gui.util.Layout;
import javafx.geometry.Insets;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import net.glxn.qrgen.QRCode;
import net.glxn.qrgen.image.ImageType;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.uri.BitcoinURI;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

import static io.bisq.gui.util.FormBuilder.*;

@FxmlView
public class BsqReceiveView extends ActivatableView<GridPane, Void> {

    private ImageView qrCodeImageView;
    private AddressTextField addressTextField;
    private InputTextField amountTextField;
    private TextField balanceTextField;

    private final BsqWalletService bsqWalletService;
    private final BsqFormatter bsqFormatter;
    private final BsqBalanceUtil bsqBalanceUtil;

    private int gridRow = 0;
    private final String paymentLabelString;
    private Subscription amountTextFieldSubscription;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BsqReceiveView(BsqWalletService bsqWalletService, BsqFormatter bsqFormatter, BsqBalanceUtil bsqBalanceUtil) {
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;
        this.bsqBalanceUtil = bsqBalanceUtil;
        paymentLabelString = Res.get("dao.wallet.receive.fundBSQWallet");
    }

    @Override
    public void initialize() {
        addTitledGroupBg(root, gridRow, 1, Res.get("shared.balance"));
        balanceTextField = addLabelTextField(root, gridRow, Res.get("shared.bsqBalance"), Layout.FIRST_ROW_DISTANCE).second;
        bsqBalanceUtil.setBalanceTextField(balanceTextField);
        bsqBalanceUtil.initialize();

        addTitledGroupBg(root, ++gridRow, 3, Res.get("dao.wallet.receive.fundYourWallet"), Layout.GROUP_DISTANCE);

        qrCodeImageView = new ImageView();
        qrCodeImageView.setStyle("-fx-cursor: hand;");
        Tooltip.install(qrCodeImageView, new Tooltip(Res.get("shared.openLargeQRWindow")));
        GridPane.setRowIndex(qrCodeImageView, gridRow);
        GridPane.setColumnIndex(qrCodeImageView, 1);
        GridPane.setMargin(qrCodeImageView, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, 0, 0, 0));
        root.getChildren().add(qrCodeImageView);

        addressTextField = addLabelAddressTextField(root, ++gridRow, Res.getWithCol("shared.address")).second;
        addressTextField.setPaymentLabel(paymentLabelString);

        amountTextField = addLabelInputTextField(root, ++gridRow, Res.get("dao.wallet.receive.amountOptional")).second;
        if (DevEnv.DEV_MODE)
            amountTextField.setText("10");
    }

    @Override
    protected void activate() {
        bsqBalanceUtil.activate();

        amountTextFieldSubscription = EasyBind.subscribe(amountTextField.textProperty(), t -> {
            addressTextField.setAmountAsCoin(bsqFormatter.parseToCoin(t));
            updateQRCode();
        });
        qrCodeImageView.setOnMouseClicked(e -> GUIUtil.showFeeInfoBeforeExecute(
                () -> UserThread.runAfter(
                        () -> new QRCodeWindow(getBitcoinURI()).show(),
                        200, TimeUnit.MILLISECONDS)
        ));
        addressTextField.setAddress(bsqFormatter.getBsqAddressStringFromAddress(bsqWalletService.freshReceiveAddress()));
        updateQRCode();
    }

    @Override
    protected void deactivate() {
        bsqBalanceUtil.deactivate();

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
        return bsqFormatter.parseToCoin(amountTextField.getText());
    }

    private String getBitcoinURI() {
        Address addressFromBsqAddress = bsqFormatter.getAddressFromBsqAddress(addressTextField.getAddress());
        return BitcoinURI.convertToBitcoinURI(addressFromBsqAddress,
                getAmountAsCoin(),
                paymentLabelString,
                null);
    }
}

