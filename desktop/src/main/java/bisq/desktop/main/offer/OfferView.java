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

package bisq.desktop.main.offer;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.View;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.main.MainView;
import bisq.desktop.main.offer.bisq_v1.createoffer.CreateOfferView;
import bisq.desktop.main.offer.bisq_v1.takeoffer.TakeOfferView;
import bisq.desktop.main.offer.bsq_swap.create_offer.BsqSwapCreateOfferView;
import bisq.desktop.main.offer.bsq_swap.take_offer.BsqSwapTakeOfferView;
import bisq.desktop.main.offer.offerbook.BsqOfferBookView;
import bisq.desktop.main.offer.offerbook.BtcOfferBookView;
import bisq.desktop.main.offer.offerbook.OfferBookView;
import bisq.desktop.main.offer.offerbook.OtherOfferBookView;
import bisq.desktop.main.offer.offerbook.TopAltcoinOfferBookView;
import bisq.desktop.util.GUIUtil;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.bsq_swap.BsqSwapOfferPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.network.p2p.P2PService;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;

import javafx.beans.value.ChangeListener;

import javafx.collections.ListChangeListener;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

public abstract class OfferView extends ActivatableView<TabPane, Void> {

    private OfferBookView btcOfferBookView, bsqOfferBookView, topAltcoinOfferBookView, otherOfferBookView;
    private CreateOfferView createOfferView;
    private BsqSwapCreateOfferView bsqSwapCreateOfferView;
    private TakeOfferView takeOfferView;
    private BsqSwapTakeOfferView bsqSwapTakeOfferView;
    private AnchorPane createOfferPane, takeOfferPane;
    private Tab takeOfferTab, createOfferTab, btcOfferBookTab, bsqOfferBookTab, topAltcoinOfferBookTab, otherOfferBookTab;

    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private final Preferences preferences;
    private final User user;
    private final P2PService p2PService;
    private final OfferDirection direction;

    private Offer offer;
    private TradeCurrency tradeCurrency;
    private boolean createOfferViewOpen, takeOfferViewOpen;
    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;
    private ListChangeListener<Tab> tabListChangeListener;
    private OfferView.OfferActionHandler offerActionHandler;

    protected OfferView(ViewLoader viewLoader,
                        Navigation navigation,
                        Preferences preferences,
                        User user,
                        P2PService p2PService,
                        OfferDirection direction) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
        this.preferences = preferences;
        this.user = user;
        this.p2PService = p2PService;
        this.direction = direction;
    }

    @Override
    protected void initialize() {
        navigationListener = (viewPath, data) -> {
            if (viewPath.size() == 3 && viewPath.indexOf(this.getClass()) == 1)
                loadView(viewPath.tip(), data);
        };
        tabChangeListener = (observableValue, oldValue, newValue) -> {
            if (newValue != null) {
                if (newValue.equals(createOfferTab) && createOfferView != null) {
                    createOfferView.onTabSelected(true);
                } else if (newValue.equals(createOfferTab) && bsqSwapCreateOfferView != null) {
                    bsqSwapCreateOfferView.onTabSelected(true);
                } else if (newValue.equals(takeOfferTab) && takeOfferView != null) {
                    takeOfferView.onTabSelected(true);
                } else if (newValue.equals(takeOfferTab) && bsqSwapTakeOfferView != null) {
                    bsqSwapTakeOfferView.onTabSelected(true);
                } else if (newValue.equals(btcOfferBookTab)) {
                    if (btcOfferBookView != null) {
                        btcOfferBookView.onTabSelected(true);
                    } else {
                        loadView(BtcOfferBookView.class, null);
                    }
                } else if (newValue.equals(bsqOfferBookTab)) {
                    if (bsqOfferBookView != null) {
                        bsqOfferBookView.onTabSelected(true);
                    } else {
                        loadView(BsqOfferBookView.class, null);
                    }
                } else if (newValue.equals(topAltcoinOfferBookTab)) {
                    if (topAltcoinOfferBookView != null) {
                        topAltcoinOfferBookView.onTabSelected(true);
                    } else {
                        loadView(TopAltcoinOfferBookView.class, null);
                    }
                } else if (newValue.equals(otherOfferBookTab)) {
                    if (otherOfferBookView != null) {
                        otherOfferBookView.onTabSelected(true);
                    } else {
                        loadView(OtherOfferBookView.class, null);
                    }
                }
            }
            if (oldValue != null) {
                if (oldValue.equals(createOfferTab) && createOfferView != null) {
                    createOfferView.onTabSelected(false);
                } else if (oldValue.equals(createOfferTab) && bsqSwapCreateOfferView != null) {
                    bsqSwapCreateOfferView.onTabSelected(false);
                } else if (oldValue.equals(takeOfferTab) && takeOfferView != null) {
                    takeOfferView.onTabSelected(false);
                } else if (oldValue.equals(takeOfferTab) && bsqSwapTakeOfferView != null) {
                    bsqSwapTakeOfferView.onTabSelected(false);
                } else if (oldValue.equals(btcOfferBookTab) && btcOfferBookView != null) {
                    btcOfferBookView.onTabSelected(false);
                } else if (oldValue.equals(bsqOfferBookTab) && bsqOfferBookView != null) {
                    bsqOfferBookView.onTabSelected(false);
                } else if (oldValue.equals(topAltcoinOfferBookTab) && topAltcoinOfferBookView != null) {
                    topAltcoinOfferBookView.onTabSelected(false);
                } else if (oldValue.equals(otherOfferBookTab) && otherOfferBookView != null) {
                    otherOfferBookView.onTabSelected(false);
                }
            }
        };
        tabListChangeListener = change -> {
            change.next();
            List<? extends Tab> removedTabs = change.getRemoved();
            if (removedTabs.size() == 1) {
                if (removedTabs.get(0).getContent().equals(createOfferPane))
                    onCreateOfferViewRemoved();
                else if (removedTabs.get(0).getContent().equals(takeOfferPane))
                    onTakeOfferViewRemoved();
            }
        };

        offerActionHandler = new OfferActionHandler() {
            @Override
            public void onCreateOffer(TradeCurrency tradeCurrency, PaymentMethod paymentMethod) {
                if (createOfferViewOpen) {
                    root.getTabs().remove(createOfferTab);
                }
                if (canCreateOrTakeOffer(tradeCurrency)) {
                    openCreateOffer(tradeCurrency, paymentMethod);
                }
            }

            @Override
            public void onTakeOffer(Offer offer) {
                if (takeOfferViewOpen) {
                    root.getTabs().remove(takeOfferTab);
                }
                Optional<TradeCurrency> optionalTradeCurrency = CurrencyUtil.getTradeCurrency(offer.getCurrencyCode());
                if (optionalTradeCurrency.isPresent() && canCreateOrTakeOffer(optionalTradeCurrency.get())) {
                    openTakeOffer(offer);
                }
            }
        };
    }

    @Override
    protected void activate() {
        Optional<TradeCurrency> tradeCurrencyOptional = (this.direction == OfferDirection.SELL) ?
                CurrencyUtil.getTradeCurrency(preferences.getSellScreenCurrencyCode()) :
                CurrencyUtil.getTradeCurrency(preferences.getBuyScreenCurrencyCode());
        tradeCurrency = tradeCurrencyOptional.orElseGet(GlobalSettings::getDefaultTradeCurrency);

        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        root.getTabs().addListener(tabListChangeListener);
        navigation.addListener(navigationListener);
        if (btcOfferBookView == null) {
            navigation.navigateTo(MainView.class, this.getClass(), BtcOfferBookView.class);
        }
    }

    @Override
    protected void deactivate() {
        navigation.removeListener(navigationListener);
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
        root.getTabs().removeListener(tabListChangeListener);
    }

    private String getCreateOfferTabName(Class<? extends View> viewClass) {
        return viewClass == BsqSwapCreateOfferView.class ?
                Res.get("offerbook.bsqSwap.createOffer").toUpperCase() :
                Res.get("offerbook.createOffer").toUpperCase();
    }

    private String getTakeOfferTabName() {
        return Res.get("offerbook.takeOffer").toUpperCase();
    }

    private void loadView(Class<? extends View> viewClass, @Nullable Object data) {
        TabPane tabPane = root;
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        View view;

        if (OfferBookView.class.isAssignableFrom(viewClass)) {

            if (viewClass == BtcOfferBookView.class && btcOfferBookTab != null && btcOfferBookView != null) {
                tabPane.getSelectionModel().select(btcOfferBookTab);
            } else if (viewClass == BsqOfferBookView.class && bsqOfferBookTab != null && bsqOfferBookView != null) {
                tabPane.getSelectionModel().select(bsqOfferBookTab);
            } else if (viewClass == TopAltcoinOfferBookView.class && topAltcoinOfferBookTab != null && topAltcoinOfferBookView != null) {
                tabPane.getSelectionModel().select(topAltcoinOfferBookTab);
            } else if (viewClass == OtherOfferBookView.class && otherOfferBookTab != null && otherOfferBookView != null) {
                tabPane.getSelectionModel().select(topAltcoinOfferBookTab);
            } else {
                if (btcOfferBookTab == null) {
                    btcOfferBookTab = new Tab("BTC");
                    btcOfferBookTab.setClosable(false);
                    bsqOfferBookTab = new Tab("BSQ");
                    bsqOfferBookTab.setClosable(false);
                    topAltcoinOfferBookTab = new Tab("XMR");
                    topAltcoinOfferBookTab.setClosable(false);
                    otherOfferBookTab = new Tab("OTHER");
                    otherOfferBookTab.setClosable(false);

                    tabPane.getTabs().addAll(btcOfferBookTab, bsqOfferBookTab, topAltcoinOfferBookTab, otherOfferBookTab);
                }
                if (viewClass == BtcOfferBookView.class) {
                    btcOfferBookView = (OfferBookView) viewLoader.load(BtcOfferBookView.class);
                    btcOfferBookTab.setContent(btcOfferBookView.getRoot());
                    btcOfferBookView.setOfferActionHandler(offerActionHandler);
                    btcOfferBookView.setDirection(direction);
                    btcOfferBookView.onTabSelected(true);
                } else if (viewClass == BsqOfferBookView.class) {
                    bsqOfferBookView = (OfferBookView) viewLoader.load(BsqOfferBookView.class);
                    bsqOfferBookView.setOfferActionHandler(offerActionHandler);
                    bsqOfferBookView.setDirection(direction);
                    bsqOfferBookTab.setContent(bsqOfferBookView.getRoot());
                    bsqOfferBookView.onTabSelected(true);
                } else if (viewClass == TopAltcoinOfferBookView.class) {
                    topAltcoinOfferBookView = (OfferBookView) viewLoader.load(TopAltcoinOfferBookView.class);
                    topAltcoinOfferBookView.setOfferActionHandler(offerActionHandler);
                    topAltcoinOfferBookView.setDirection(direction);
                    topAltcoinOfferBookTab.setContent(topAltcoinOfferBookView.getRoot());
                    topAltcoinOfferBookView.onTabSelected(true);
                } else if (viewClass == OtherOfferBookView.class) {
                    otherOfferBookView = (OfferBookView) viewLoader.load(OtherOfferBookView.class);
                    otherOfferBookView.setOfferActionHandler(offerActionHandler);
                    otherOfferBookView.setDirection(direction);
                    otherOfferBookTab.setContent(otherOfferBookView.getRoot());
                    otherOfferBookView.onTabSelected(true);
                }
            }
        } else if (viewClass == CreateOfferView.class && createOfferView == null) {
            view = viewLoader.load(viewClass);
            // CreateOffer and TakeOffer must not be cached by ViewLoader as we cannot use a view multiple times
            // in different graphs
            createOfferView = (CreateOfferView) view;
            createOfferView.initWithData(direction, tradeCurrency, offerActionHandler);
            createOfferPane = createOfferView.getRoot();
            createOfferTab = new Tab(getCreateOfferTabName(viewClass));
            createOfferTab.setClosable(true);
            // close handler from close on create offer action
            createOfferView.setCloseHandler(() -> tabPane.getTabs().remove(createOfferTab));
            createOfferTab.setContent(createOfferPane);
            tabPane.getTabs().add(createOfferTab);
            tabPane.getSelectionModel().select(createOfferTab);
            createOfferViewOpen = true;
        } else if (viewClass == BsqSwapCreateOfferView.class && bsqSwapCreateOfferView == null) {
            view = viewLoader.load(viewClass);
            bsqSwapCreateOfferView = (BsqSwapCreateOfferView) view;
            bsqSwapCreateOfferView.initWithData(direction, offerActionHandler, (BsqSwapOfferPayload) data);
            createOfferPane = bsqSwapCreateOfferView.getRoot();
            createOfferTab = new Tab(getCreateOfferTabName(viewClass));
            createOfferTab.setClosable(true);
            // close handler from close on create offer action
            bsqSwapCreateOfferView.setCloseHandler(() -> tabPane.getTabs().remove(createOfferTab));
            createOfferTab.setContent(createOfferPane);
            tabPane.getTabs().add(createOfferTab);
            tabPane.getSelectionModel().select(createOfferTab);
            createOfferViewOpen = true;
        } else if (viewClass == TakeOfferView.class && takeOfferView == null && offer != null) {
            view = viewLoader.load(viewClass);
            // CreateOffer and TakeOffer must not be cached by ViewLoader as we cannot use a view multiple times
            // in different graphs
            takeOfferView = (TakeOfferView) view;
            takeOfferView.initWithData(offer);
            takeOfferPane = takeOfferView.getRoot();
            takeOfferTab = new Tab(getTakeOfferTabName());
            takeOfferTab.setClosable(true);
            // close handler from close on take offer action
            takeOfferView.setCloseHandler(() -> tabPane.getTabs().remove(takeOfferTab));
            takeOfferTab.setContent(takeOfferPane);
            tabPane.getTabs().add(takeOfferTab);
            tabPane.getSelectionModel().select(takeOfferTab);
        } else if (viewClass == BsqSwapTakeOfferView.class && takeOfferView == null && offer != null) {
            view = viewLoader.load(viewClass);
            // CreateOffer and TakeOffer must not be cached by ViewLoader as we cannot use a view multiple times
            // in different graphs
            bsqSwapTakeOfferView = (BsqSwapTakeOfferView) view;
            bsqSwapTakeOfferView.initWithData(offer);
            takeOfferPane = bsqSwapTakeOfferView.getRoot();
            takeOfferTab = new Tab(getTakeOfferTabName());
            takeOfferTab.setClosable(true);
            // close handler from close on take offer action
            bsqSwapTakeOfferView.setCloseHandler(() -> tabPane.getTabs().remove(takeOfferTab));
            takeOfferTab.setContent(takeOfferPane);
            tabPane.getTabs().add(takeOfferTab);
            tabPane.getSelectionModel().select(takeOfferTab);
        }
    }

    protected boolean canCreateOrTakeOffer(TradeCurrency tradeCurrency) {
        return GUIUtil.isBootstrappedOrShowPopup(p2PService) &&
                GUIUtil.canCreateOrTakeOfferOrShowPopup(user, navigation, tradeCurrency);
    }

    private void openTakeOffer(Offer offer) {
        takeOfferViewOpen = true;
        this.offer = offer;
        if (offer.isBsqSwapOffer()) {
            navigation.navigateTo(MainView.class, this.getClass(), BsqSwapTakeOfferView.class);
        } else {
            navigation.navigateTo(MainView.class, this.getClass(), TakeOfferView.class);
        }
    }

    private void openCreateOffer(TradeCurrency tradeCurrency, PaymentMethod paymentMethod) {
        createOfferViewOpen = true;
        this.tradeCurrency = tradeCurrency;
        if (tradeCurrency.getCode().equals("BSQ") && paymentMethod.isBsqSwap()) {
            navigation.navigateTo(MainView.class, this.getClass(), BsqSwapCreateOfferView.class);
        } else {
            navigation.navigateTo(MainView.class, this.getClass(), CreateOfferView.class);
        }
    }

    private void onCreateOfferViewRemoved() {
        createOfferViewOpen = false;
        if (createOfferView != null) {
            createOfferView.onClose();
            createOfferView = null;
        }
        if (bsqSwapCreateOfferView != null) {
            bsqSwapCreateOfferView = null;
        }
        btcOfferBookView.enableCreateOfferButton();
        //TODO: go to last selected tab
        navigation.navigateTo(MainView.class, this.getClass(), BtcOfferBookView.class);
    }

    private void onTakeOfferViewRemoved() {
        offer = null;
        takeOfferViewOpen = false;
        if (takeOfferView != null) {
            takeOfferView.onClose();
            takeOfferView = null;
        }
        if (bsqSwapTakeOfferView != null) {
            bsqSwapTakeOfferView = null;
        }
        //TODO: go to last selected tab
        navigation.navigateTo(MainView.class, this.getClass(), BtcOfferBookView.class);
    }

    public interface OfferActionHandler {
        void onCreateOffer(TradeCurrency tradeCurrency, PaymentMethod paymentMethod);

        void onTakeOffer(Offer offer);
    }

    public interface CloseHandler {
        void close();
    }
}
