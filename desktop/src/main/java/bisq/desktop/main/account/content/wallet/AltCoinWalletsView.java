package bisq.desktop.main.account.content.wallet;

import javax.inject.Inject;

import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.common.view.View;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.main.MainView;
import bisq.desktop.main.account.AccountView;
import bisq.desktop.main.account.content.wallet.monero.XmrWalletView;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

@FxmlView
public class AltCoinWalletsView extends ActivatableView<TabPane, Void> {

	@FXML
	Tab moneroWalletTab;
	
    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;
    
    private Tab selectedTab;
    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private Class<? extends View> currentTabView; 
	private Preferences preferences;

    @Inject
    private AltCoinWalletsView(CachingViewLoader viewLoader, Navigation navigation, Preferences preferences) {
        this.navigation = navigation;
        this.viewLoader = viewLoader;
        this.preferences = preferences;
    }

    @Override
    public void initialize() {
    	log.debug("initialize()");
        //TODO Update as altcoinWalletsTab.setVisible(Preferences.PreferencesPayload.useBisqXmrWallet)
        
    	root.setPadding(new Insets(20));
        navigationListener = viewPath -> {
        	log.debug("navigationListener.viewPath1=" + viewPath);
        	if(selectedTab == null) {
        		selectedTab = moneroWalletTab;
        	}
        	if(selectedTab == moneroWalletTab) {
        		currentTabView = XmrWalletView.class;
        	}//TODO Tabs for other altcoins added here
        	navigation.navigateTo(MainView.class, AccountView.class, AltCoinWalletsView.class, currentTabView);
        	log.debug("navigationListener.viewPath2=" + viewPath);
            if (viewPath.size() == 4 && viewPath.indexOf(AccountView.class) == 1) {
            	loadView(viewPath.tip());
            } else {
            	loadView(XmrWalletView.class);
            }
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
        	log.debug("tabChangeListener.oldValue=" + oldValue);
        	log.debug("tabChangeListener.newValue=" + newValue);
            if (newValue == moneroWalletTab && selectedTab != moneroWalletTab) {
            	loadView(XmrWalletView.class);
            }
        };
        root.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        moneroWalletTab.setText(Res.get("account.menu.wallets.moneroWalletView").toUpperCase());
    }
    

    @Override
    protected void activate() {
    	log.debug("activate()");
        if(preferences.isUseBisqXmrWallet()) {
            navigation.addListener(navigationListener);

            root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);

            if (navigation.getCurrentPath().size() == 3 && navigation.getCurrentPath().get(1) == AccountView.class && navigation.getCurrentPath().get(2) == AltCoinWalletsView.class) {
                if (root.getSelectionModel().getSelectedItem() == moneroWalletTab && selectedTab != moneroWalletTab) {
            		loadView(XmrWalletView.class);
                }
            }
        }
    }

    @Override
    protected void deactivate() {
        if(preferences.isUseBisqXmrWallet()) {
            navigation.removeListener(navigationListener);
            root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
        }
    }

    private void loadView(Class<? extends View> viewClass) {
    	log.debug("loadView({})", viewClass);

    	
        if(preferences.isUseBisqXmrWallet()) {
            if (selectedTab != null && selectedTab.getContent() != null) {
                if (selectedTab.getContent() instanceof ScrollPane) {
                    ((ScrollPane) selectedTab.getContent()).setContent(null);
                } else {
                    selectedTab.setContent(null);
                }
            }

            View view = viewLoader.load(viewClass);
            if (view instanceof XmrWalletView) {
                selectedTab = moneroWalletTab;
            }

            selectedTab.setContent(view.getRoot());
            root.getSelectionModel().select(selectedTab);
        }
    }
}
