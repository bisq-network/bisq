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

package bisq.desktop.main.account.content.wallet.monero.send;

import static bisq.desktop.util.FormBuilder.addButtonBusyAnimationLabelAfterGroup;
import static bisq.desktop.util.FormBuilder.addInputTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static bisq.desktop.util.FormBuilder.addTopLabelComboBox;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;

import javax.inject.Inject;

import bisq.asset.CryptoNoteAddressValidator;
import bisq.common.UserThread;
import bisq.common.handlers.ResultHandler;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple4;
import bisq.core.locale.Res;
import bisq.core.xmr.XmrFormatter;
import bisq.core.xmr.jsonrpc.MoneroSendPriority;
import bisq.core.xmr.wallet.XmrWalletRpcWrapper;
import bisq.core.xmr.wallet.listeners.WalletUiListener;
import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.MainView;
import bisq.desktop.main.account.AccountView;
import bisq.desktop.main.account.content.wallet.AltCoinWalletsView;
import bisq.desktop.main.account.content.wallet.monero.XmrBalanceUtil;
import bisq.desktop.main.account.content.wallet.monero.XmrWalletView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.XmrValidator;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

@FxmlView
public class XmrSendView extends ActivatableView<GridPane, Void> implements WalletUiListener {
	private final XmrWalletRpcWrapper walletWrapper;
    private final XmrFormatter xmrFormatter;
    private final Navigation navigation;
    private final XmrBalanceUtil xmrBalanceUtil;
    private final XmrValidator xmrValidator;//TODO(niyid) Replace with XMR equivalent
    private final CryptoNoteAddressValidator addressValidator;//TODO(niyid) Replace with XMR equivalent

    private int gridRow = 0;
    private InputTextField amountInputTextField;
    private InputTextField feeInputTextField;
    private Button sendXmrButton;
    private InputTextField receiversAddressInputTextField;
    private ChangeListener<Boolean> focusOutListener;
    private ChangeListener<String> inputTextFieldListener;
    private ComboBox<MoneroSendPriority> priorityComboBox;
    private BusyAnimation busyAnimation;

    @Inject
    private XmrSendView(XmrWalletRpcWrapper walletWrapper,
    					XmrFormatter xmrFormatter, Navigation navigation,
                        XmrBalanceUtil xmrBalanceUtil, XmrValidator xmrValidator) {
        this.walletWrapper = walletWrapper;
        this.xmrFormatter = xmrFormatter;
        this.navigation = navigation;
        this.xmrBalanceUtil = xmrBalanceUtil;
        this.xmrValidator = xmrValidator;
        this.addressValidator = new CryptoNoteAddressValidator(true, 24, 36, 53, 63);//TODO(niyid) Only allow testnet/stagenet addresses
//        this.addressValidator = new CryptoNoteAddressValidator(true, 18, 42);//TODO(niyid) Only allow mainnet addresses
    }

    @Override
    public void initialize() {
    	if(!walletWrapper.isXmrWalletRpcRunning()) {
        	walletWrapper.openWalletRpcInstance(this);
    	}

    	root.setPadding(new Insets(10));
        gridRow = xmrBalanceUtil.addGroup(root, gridRow);

        addSendXmrGroup();

        focusOutListener = (observable, oldValue, newValue) -> {
            if (!newValue) {
                onUpdateBalances();
            }
        };
        inputTextFieldListener = (observable, oldValue, newValue) -> onUpdateBalances();
    }

    @Override
    protected void activate() {
        xmrBalanceUtil.activate();

        receiversAddressInputTextField.focusedProperty().addListener(focusOutListener);
        amountInputTextField.focusedProperty().addListener(focusOutListener);

        receiversAddressInputTextField.textProperty().addListener(inputTextFieldListener);
        amountInputTextField.textProperty().addListener(inputTextFieldListener);
    }

    @Override
    protected void deactivate() {
        xmrBalanceUtil.deactivate();

        receiversAddressInputTextField.focusedProperty().removeListener(focusOutListener);
        amountInputTextField.focusedProperty().removeListener(focusOutListener);

        receiversAddressInputTextField.textProperty().removeListener(inputTextFieldListener);
        amountInputTextField.textProperty().removeListener(inputTextFieldListener);
    }

    @Override
    public void onUpdateBalances(HashMap<String, Object> walletRpcData) {
    	log.debug("onUpdateBalances => {}", walletRpcData);
        BigInteger fee = walletRpcData.get("getFee") != null ? (BigInteger) walletRpcData.get("getFee") : BigInteger.ZERO;
        BigInteger unlockedBalance = walletRpcData.get("getUnlockedBalance") != null ? (BigInteger) walletRpcData.get("getUnlockedBalance") : BigInteger.ZERO; 
		Long size = walletRpcData.get("getSize") != null ? (Long) walletRpcData.get("getSize") : 0;
		Double sizeKbs = size != null ? size.doubleValue() / 1024.0 : 0.0;
		feeInputTextField.setText(xmrFormatter.formatBigInteger(fee));
		BigInteger amountToSend = walletRpcData.get("getAmount") != null
				? (BigInteger) walletRpcData.get("getAmount")
				: BigInteger.ZERO;
		if(unlockedBalance.subtract(amountToSend).subtract(fee).compareTo(BigInteger.ZERO) < 0) {
			handleError(new Exception("Balance too low."));
		} else {
			showPublishTxPopup(amountToSend, receiversAddressInputTextField.getText(), fee, size, sizeKbs, amountToSend,
					xmrFormatter, () -> {
						String txToRelay = (String) walletRpcData.get("txToRelay");
						HashMap<String, Object> dataToRelay = new HashMap<>();
						dataToRelay.put("txToRelay", txToRelay);
						walletWrapper.relayTx(XmrSendView.this, dataToRelay);
						receiversAddressInputTextField.setText("");
						amountInputTextField.setText("");
					});
		}
        onUpdateBalances();
        xmrBalanceUtil.onUpdateBalances(walletRpcData);
    }
    
    public void onUpdateBalances() {
        //TODO(niyid) Replace with XmrAddressValidator
        boolean isValid = addressValidator.validate(receiversAddressInputTextField.getText()).isValid() &&
                xmrValidator.validate(amountInputTextField.getText()).isValid;
        sendXmrButton.setDisable(!isValid);
        
    }

    private void addSendXmrGroup() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(root, ++gridRow, 2, Res.get("shared.account.wallet.send.sendFunds"), Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(titledGroupBg, 3);

        receiversAddressInputTextField = addInputTextField(root, gridRow,
                Res.get("shared.account.wallet.send.receiverAddress"), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        GridPane.setColumnSpan(receiversAddressInputTextField, 3);

        amountInputTextField = addInputTextField(root, ++gridRow, Res.get("shared.account.wallet.send.setAmount", xmrFormatter.formatBigInteger(XmrFormatter.MINIMUM_SENDABLE_AMOUNT)));
        amountInputTextField.setValidator(xmrValidator);
        GridPane.setColumnSpan(amountInputTextField, 3);
        
        feeInputTextField = addInputTextField(root, ++gridRow, Res.get("shared.account.wallet.send.feeAmount"));
        GridPane.setColumnSpan(feeInputTextField, 3);
        feeInputTextField.setEditable(false);
        
        //TODO(niyid) Add priority field list
        Tuple2<Label, ComboBox<MoneroSendPriority>> topLabelComboBox = addTopLabelComboBox(root, ++gridRow, Res.get("shared.account.wallet.send.priorityLabel"), Res.get("shared.account.wallet.send.priority"), (int) Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        ObservableList<MoneroSendPriority> priorityOptions = FXCollections.observableArrayList();
        priorityOptions.addAll(MoneroSendPriority.values());
        priorityComboBox = topLabelComboBox.second;
        priorityComboBox.setItems(priorityOptions);
        GridPane.setColumnSpan(priorityComboBox, 3);

        focusOutListener = (observable, oldValue, newValue) -> {
            if (!newValue) {
                onUpdateBalances();
            }
        };

        Tuple4<Button, BusyAnimation, Label, HBox> actionButtonTuple4 = addButtonBusyAnimationLabelAfterGroup(root, ++gridRow, Res.get("shared.account.wallet.send.send"));
        
        sendXmrButton = actionButtonTuple4.first;
        busyAnimation = actionButtonTuple4.second;

        sendXmrButton.setOnAction((event) -> {
			Integer accountIndex = 0;
			String address = receiversAddressInputTextField.getText();
			BigDecimal amt = new BigDecimal(amountInputTextField.getText());
			BigInteger amount = amt.movePointRight(12).toBigInteger();//Convert input number to scale compatible with XmrWalletRpc 
			MoneroSendPriority priority = priorityComboBox.getSelectionModel().getSelectedItem();
			HashMap<String, Object> data = new HashMap<>();
			data.put("getBalance", null);
			data.put("getUnlockedBalance", null);
			data.put("getFee", null);
			data.put("getNumConfirmations", null);
			data.put("getId", null);
			data.put("getTimestamp", null);
			data.put("getPaymentId", null);
			data.put("getUnlockTime", null);
			data.put("getAmount", null);

			try {
				walletWrapper.createTx(XmrSendView.this, accountIndex, address, amount, priority != null ? priority : MoneroSendPriority.NORMAL, true, data);
		        xmrBalanceUtil.postSendUpdate(data);
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
				new Popup<>().error(Res.get("mainView.networkWarning.localhostLost", "Monero")).show();
			} catch (Exception e) {
				e.printStackTrace();
				new Popup<>().error(Res.get("shared.account.wallet.popup.error.startupFailed")).show();
			}
        });
    }

    private void handleError(Throwable t) {
        new Popup<>().error(Res.get("shared.account.wallet.popup.error.transactionFailed", t.getLocalizedMessage()))
        .actionButtonTextWithGoTo("account.menu.wallets.monero.navigation.funds.depositFunds")
        .onAction(() -> navigation.navigateTo(MainView.class, AccountView.class, 
        		AltCoinWalletsView.class, XmrWalletView.class)).show();
        log.error(t.toString());
        t.printStackTrace();
    }

    private void showPublishTxPopup(BigInteger outgoingAmount, String address, 
    								BigInteger fee, Long size, Double sizeKbs,
    								BigInteger amountToReceive, XmrFormatter amountFormatter,
                                    ResultHandler resultHandler) {
        new Popup<>().headLine(Res.get("shared.account.wallet.send.sendFunds.headline"))
                .confirmation(Res.get("shared.account.wallet.send.sendFunds.details",
                        xmrFormatter.formatBigInteger(outgoingAmount),
                        address, xmrFormatter.formatBigInteger(fee),  size != null ? (new BigDecimal(fee, 12).doubleValue() / size.doubleValue()) : 0, sizeKbs, xmrFormatter.formatBigInteger(amountToReceive)))
                .actionButtonText(Res.get("shared.yes"))
                .onAction(() -> {
                    resultHandler.handleResult();
                })
                .closeButtonText(Res.get("shared.cancel"))
                .show();
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

