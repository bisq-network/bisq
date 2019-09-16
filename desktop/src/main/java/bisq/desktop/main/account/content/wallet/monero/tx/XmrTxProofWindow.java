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

package bisq.desktop.main.account.content.wallet.monero.tx;

import static bisq.desktop.util.FormBuilder.addLabelWalletAddressTextField;

import bisq.common.UserThread;
import bisq.common.util.Tuple3;
import bisq.core.locale.Res;
import bisq.core.xmr.wallet.TxProofHandler;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.WalletAddressTextField;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.Layout;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class XmrTxProofWindow extends Overlay<XmrTxProofWindow> implements TxProofHandler {
	private WalletAddressTextField messageTextField;
    private WalletAddressTextField txIdTextField;
    private WalletAddressTextField signatureTextField;
    private BusyAnimation busyAnimation = new BusyAnimation(false);
    private int gridRow = 0;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface
    ///////////////////////////////////////////////////////////////////////////////////////////

    XmrTxProofWindow() {
        type = Type.Attention;
        width = 900;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void show() {
        if (gridPane != null) {
            rowIndex = -1;
            gridPane.getChildren().clear();
        }

        if (headLine == null)
            headLine = Res.get("shared.account.wallet.tx.proof");

        createGridPane();
        addHeadLine();
        addInputFields();
        addButtons();
        applyStyles();
        display();
    }

    private void addInputFields() {
        Tuple3<Label, WalletAddressTextField, VBox> tupleAddress = addLabelWalletAddressTextField(gridPane, ++gridRow,
                Res.get("shared.account.wallet.tx.item.message"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        messageTextField = tupleAddress.second;
        VBox addressVBox = tupleAddress.third;
        GridPane.setColumnSpan(addressVBox, 3);
        
        Tuple3<Label, WalletAddressTextField, VBox> tupleTxId = addLabelWalletAddressTextField(gridPane, ++gridRow,
                Res.get("shared.account.wallet.tx.item.txId"),
                Layout.GROUP_DISTANCE);
        txIdTextField = tupleTxId.second;
        VBox txIdVBox = tupleTxId.third;
        GridPane.setColumnSpan(txIdVBox, 3);

        Tuple3<Label, WalletAddressTextField, VBox> tupleSignature = addLabelWalletAddressTextField(gridPane, ++gridRow,
                Res.get("shared.account.wallet.tx.item.signature"),
                Layout.GROUP_DISTANCE);
        signatureTextField = tupleSignature.second;
        VBox signatureVBox = tupleSignature.third;
        GridPane.setColumnSpan(signatureVBox, 3);
	}


	@Override
    public void update(String txId, String message, String signature) {
        txIdTextField.setAddress(txId);
        messageTextField.setAddress(message);
        signatureTextField.setAddress(signature);
    }

	@Override
	public void playAnimation() {
    	UserThread.execute(() -> {
    		busyAnimation.setVisible(true);
			busyAnimation.play();
        });
	}

	@Override
	public void stopAnimation() {
    	UserThread.execute(() -> {
			busyAnimation.setVisible(false);
			busyAnimation.stop();
        });
	}

	@Override
	public void popupErrorWindow(String resourceMessage) {
    	UserThread.execute(() -> {
			new Popup<>().error(resourceMessage).show();
        });
	}
}
