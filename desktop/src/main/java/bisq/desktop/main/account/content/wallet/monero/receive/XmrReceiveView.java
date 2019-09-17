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

package bisq.desktop.main.account.content.wallet.monero.receive;

import static bisq.desktop.util.FormBuilder.addLabelWalletAddressTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

import java.util.HashMap;

import javax.inject.Inject;

import bisq.common.UserThread;
import bisq.common.util.Tuple3;
import bisq.core.locale.Res;
import bisq.core.xmr.wallet.XmrWalletRpcWrapper;
import bisq.core.xmr.wallet.listeners.WalletUiListener;
import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.components.WalletAddressTextField;
import bisq.desktop.main.account.content.wallet.monero.XmrBalanceUtil;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.Layout;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

@FxmlView
public class XmrReceiveView extends ActivatableView<GridPane, Void> implements WalletUiListener {

    private WalletAddressTextField addressTextField;
    private final XmrWalletRpcWrapper walletWrapper;
    private int gridRow = 0;
    private final XmrBalanceUtil xmrBalanceUtil;
    private BusyAnimation busyAnimation = new BusyAnimation(false);
    private VBox walletTuple3VBox;
    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private XmrReceiveView(XmrWalletRpcWrapper walletWrapper, XmrBalanceUtil xmrBalanceUtil) {
        this.walletWrapper = walletWrapper;
        this.xmrBalanceUtil = xmrBalanceUtil;
    }

    @Override
    public void initialize() {
    	if(!walletWrapper.isXmrWalletRpcRunning()) {
        	walletWrapper.openWalletRpcInstance(this);
    	}
    	
    	root.setPadding(new Insets(10));
    	gridRow = xmrBalanceUtil.addGroup(root, gridRow);

        TitledGroupBg titledGroupBg = addTitledGroupBg(root, ++gridRow, 1,
                Res.get("shared.account.wallet.receive.fundYourWallet"), Layout.GROUP_DISTANCE);
        titledGroupBg.getStyleClass().add("last");
        GridPane.setColumnSpan(titledGroupBg, 3);
        Tuple3<Label, WalletAddressTextField, VBox> tuple = addLabelWalletAddressTextField(root, gridRow,
                Res.get("shared.account.wallet.receive.walletAddress"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addressTextField = tuple.second;
        walletTuple3VBox = tuple.third;
        GridPane.setColumnSpan(walletTuple3VBox, 3);
        walletTuple3VBox.getChildren().add(busyAnimation);
        HashMap<String, Object> data = new HashMap<>();
        data.put("getBalance", null);
        data.put("getUnlockedBalance", null);
        data.put("getPrimaryAddress", null);

        try {
			walletWrapper.update(this, data);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			new Popup<>().error(Res.get("mainView.networkWarning.localhostLost", "Monero")).show();
		} catch (Exception e) {
			e.printStackTrace();
			new Popup<>().error(Res.get("shared.account.wallet.popup.error.startupFailed")).show();
		}
    }

	@Override
	public void onUpdateBalances(HashMap<String, Object> walletRpcData) {
		log.debug("onUpdateBalances => {}", walletRpcData);
    	xmrBalanceUtil.onUpdateBalances(walletRpcData);
    	String address = (String) walletRpcData.get("getPrimaryAddress");
		addressTextField.setAddress(address);
	}
	
	@Override
    public void activate() {
    }

	@Override
    public void deactivate() {
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

