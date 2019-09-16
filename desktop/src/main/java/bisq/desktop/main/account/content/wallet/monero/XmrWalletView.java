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

package bisq.desktop.main.account.content.wallet.monero;

import javax.inject.Inject;

import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.desktop.Navigation;
import bisq.desktop.common.model.Activatable;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.common.view.View;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.main.MainView;
import bisq.desktop.main.account.AccountView;
import bisq.desktop.main.account.content.wallet.AltCoinWalletsView;
import bisq.desktop.main.account.content.wallet.monero.receive.XmrReceiveView;
import bisq.desktop.main.account.content.wallet.monero.send.XmrSendView;
import bisq.desktop.main.account.content.wallet.monero.tx.XmrTxView;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

@FxmlView
public class XmrWalletView extends ActivatableViewAndModel<TabPane, Activatable> {

    @FXML
    private Tab xmrSendTab, xmrReceiveTab, xmrTxTab;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private Tab selectedTab;
    private Preferences preferences;

    @Inject
    private XmrWalletView(CachingViewLoader viewLoader, Navigation navigation, Preferences preferences) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
        this.preferences = preferences;
    }

    @Override
    public void initialize() {
    	log.debug("XmrWalletView.initialize({})", selectedTab);
    	root.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        xmrSendTab.setText(Res.get("shared.account.wallet.menuItem.send").toUpperCase());
        xmrReceiveTab.setText(Res.get("shared.account.wallet.menuItem.receive").toUpperCase());
        xmrTxTab.setText(Res.get("shared.account.wallet.menuItem.transactions").toUpperCase());

        if(selectedTab == null) {
        	selectedTab = xmrSendTab;
        }
        selectView();
        navigationListener = viewPath -> {
        	log.debug("XmrWalletView.viewPath={}, size={}", viewPath, viewPath.size());
            if (viewPath.size() == 4 && navigation.getCurrentPath().get(3) == XmrWalletView.class && 
            		navigation.getCurrentPath().get(2) == AltCoinWalletsView.class && navigation.getCurrentPath().get(1) == AccountView.class) {
            	selectView();
            }
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
        	selectedTab = newValue;
            if (newValue == xmrSendTab) {
                loadView(XmrSendView.class);
            } else if (newValue == xmrReceiveTab) {
                loadView(XmrReceiveView.class);
            } else if (newValue == xmrTxTab) {
                loadView(XmrTxView.class);
            } else {
                loadView(XmrSendView.class);
            }
        };       
    }

    private void selectView() {
        if (selectedTab == xmrSendTab) {
            loadView(XmrSendView.class);
        } else if (selectedTab == xmrReceiveTab) {
            loadView(XmrReceiveView.class);
        } else if (selectedTab == xmrTxTab) {
            loadView(XmrTxView.class);
        } else {
            loadView(XmrSendView.class);
        }
	}

	@Override
    protected void activate() {
    	log.debug("XmrWalletView.activate({})", selectedTab);
    	navigation.addListener(navigationListener);
        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);

        if (navigation.getCurrentPath().size() == 5 && navigation.getCurrentPath().get(3) == XmrWalletView.class && 
        		navigation.getCurrentPath().get(2) == AltCoinWalletsView.class && navigation.getCurrentPath().get(1) == AccountView.class) {
            Tab selectedItem = root.getSelectionModel().getSelectedItem();
            if (selectedItem == xmrSendTab)
             	navigation.navigateTo(MainView.class, AccountView.class, AltCoinWalletsView.class, XmrWalletView.class, XmrSendView.class);
            else if (selectedItem == xmrReceiveTab)
               	navigation.navigateTo(MainView.class, AccountView.class, AltCoinWalletsView.class, XmrWalletView.class, XmrReceiveView.class);
            else if (selectedItem == xmrTxTab)
               	navigation.navigateTo(MainView.class, AccountView.class, AltCoinWalletsView.class, XmrWalletView.class, XmrTxView.class);
            loadView(navigation.getCurrentPath().get(4));
        }
        //TODO(niyid) Use preferences to determine which wallet to load in XmrWalletRpcViewHelper
    }

    @Override
    protected void deactivate() {
    	log.debug("XmrWalletView.deactivate()");
        navigation.removeListener(navigationListener);
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
    }

    private void loadView(Class<? extends View> viewClass) {
    	log.debug("XmrWalletView.loadView: " + viewClass);
        if (selectedTab != null && selectedTab.getContent() != null) {
            if (selectedTab.getContent() instanceof ScrollPane) {
                ((ScrollPane) selectedTab.getContent()).setContent(null);
            } else {
                selectedTab.setContent(null);
            }
        }

        View view = viewLoader.load(viewClass);
        if (view instanceof XmrSendView) {
            selectedTab = xmrSendTab;
        } else if (view instanceof XmrReceiveView) {
            selectedTab = xmrReceiveTab;
        } else if (view instanceof XmrTxView) {
            selectedTab = xmrTxTab;
        }

        selectedTab.setContent(view.getRoot());
        root.getSelectionModel().select(selectedTab);
    }
}

