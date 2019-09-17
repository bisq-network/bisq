package bisq.desktop.main.account.content.wallet.monero;

import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

import java.math.BigInteger;
import java.util.HashMap;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bisq.common.UserThread;
import bisq.core.locale.Res;
import bisq.core.xmr.XmrFormatter;
import bisq.core.xmr.wallet.XmrWalletRpcWrapper;
import bisq.core.xmr.wallet.listeners.WalletUiListener;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.Layout;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

public class XmrBalanceUtil implements WalletUiListener {
    // Displaying general XMR info
    private TextField actualBalanceTextField, unlockedBalanceTextField;
    private XmrWalletRpcWrapper walletWrapper;
    private XmrFormatter xmrFormatter;
    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    @Inject
    private XmrBalanceUtil(XmrWalletRpcWrapper walletWrapper, XmrFormatter xmrFormatter) {
    	this.walletWrapper = walletWrapper;
    	this.xmrFormatter = xmrFormatter;
    }
    
    public int addGroup(GridPane gridPane, int gridRow) {
        addTitledGroupBg(gridPane, gridRow, 4, Res.get("shared.account.wallet.dashboard.myBalance"));
        actualBalanceTextField = FormBuilder.addTopLabelReadOnlyTextField(gridPane, gridRow,
                Res.get("shared.account.wallet.dashboard.actualBalance"), Layout.FIRST_ROW_DISTANCE).second;
        unlockedBalanceTextField = FormBuilder.addTopLabelReadOnlyTextField(gridPane, ++gridRow,
                Res.get("shared.account.wallet.dashboard.unlockedBalance")).second;

        return gridRow;
    }
    
	public void postSendUpdate(HashMap<String, Object> walletRpcData) {
		log.debug("postSendUpdate => {}", walletRpcData);
        actualBalanceTextField.setText(xmrFormatter.formatBigInteger(walletWrapper.getWalletRpc().getBalance()));
        unlockedBalanceTextField.setText(xmrFormatter.formatBigInteger(walletWrapper.getWalletRpc().getUnlockedBalance()));
	}
    
	@Override
	public void onUpdateBalances(HashMap<String, Object> walletRpcData) {
		log.debug("onUpdateBalances => {}", walletRpcData);
		if(walletRpcData.get("getBalance") != null) {
			BigInteger balance = (BigInteger) walletRpcData.get("getBalance");
	        actualBalanceTextField.setText(xmrFormatter.formatBigInteger(balance));
		}
		if(walletRpcData.get("getUnlockedBalance") != null) {
			BigInteger unlockedBalance = (BigInteger) walletRpcData.get("getUnlockedBalance"); 
	        unlockedBalanceTextField.setText(xmrFormatter.formatBigInteger(unlockedBalance));
		}
	}
	
    public void activate() {
        HashMap<String, Object> data = new HashMap<>();
        data.put("getBalance", null);
        data.put("getUnlockedBalance", null);
    	try {
			walletWrapper.update(this, data);
	        triggerUpdate();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			new Popup<>().error(Res.get("mainView.networkWarning.localhostLost", "Monero")).show();
		} catch (Exception e) {
			e.printStackTrace();
			new Popup<>().error(Res.get("shared.account.wallet.popup.error.startupFailed")).show();
		}
    }

    public void deactivate() {
    }

    private void triggerUpdate() {
    }

	@Override
	public void playAnimation() {
		//Do nothing for now
		
	}

	@Override
	public void stopAnimation() {
		//Do nothing for now
	}

	@Override
	public void popupErrorWindow(String resourceMessage) {
    	UserThread.execute(() -> {
			new Popup<>().error(resourceMessage).show();
        });
	}

}
