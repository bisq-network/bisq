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
import bisq.desktop.main.offer.offerbook.OfferBookView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.GUIUtil;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.LanguageUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.bsq_swap.BsqSwapOfferPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
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
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public abstract class OfferView extends ActivatableView<TabPane, Void> {

    private OfferBookView offerBookView;
    private CreateOfferView createOfferView;
    private BsqSwapCreateOfferView bsqSwapCreateOfferView;
    private TakeOfferView takeOfferView;
    private BsqSwapTakeOfferView bsqSwapTakeOfferView;
    private AnchorPane createOfferPane, takeOfferPane;
    private Tab takeOfferTab, createOfferTab, offerBookTab;

    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private final Preferences preferences;
    private final User user;
    private final P2PService p2PService;
    private final OfferDirection direction;
    private final ArbitratorManager arbitratorManager;

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
                        ArbitratorManager arbitratorManager,
                        User user,
                        P2PService p2PService,
                        OfferDirection direction) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
        this.preferences = preferences;
        this.user = user;
        this.p2PService = p2PService;
        this.direction = direction;
        this.arbitratorManager = arbitratorManager;
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
                } else if (newValue.equals(offerBookTab) && offerBookView != null) {
                    offerBookView.onTabSelected(true);
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
                } else if (oldValue.equals(offerBookTab) && offerBookView != null) {
                    offerBookView.onTabSelected(false);
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
                if (canCreateOrTakeOffer(CurrencyUtil.getTradeCurrency(offer.getCurrencyCode()).get())) {
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
        if (offerBookView == null) {
            navigation.navigateTo(MainView.class, this.getClass(), OfferBookView.class);
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
        boolean isBuy = direction == OfferDirection.BUY;

        if (viewClass == OfferBookView.class) {
            if (offerBookTab != null && offerBookView != null) {
                tabPane.getSelectionModel().select(offerBookTab);
            } else {
                view = viewLoader.load(viewClass);
                // Offerbook must not be cached by ViewLoader as we use 2 instances for sell and buy screens.
                offerBookTab = new Tab(isBuy ? Res.get("shared.buyBitcoin").toUpperCase() : Res.get("shared.sellBitcoin").toUpperCase());
                offerBookTab.setClosable(false);
                offerBookTab.setContent(view.getRoot());
                tabPane.getTabs().add(offerBookTab);
                offerBookView = (OfferBookView) view;
                offerBookView.onTabSelected(true);
                offerBookView.setOfferActionHandler(offerActionHandler);
                offerBookView.setDirection(direction);
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

    private void showNoArbitratorForUserLocaleWarning() {
        String key = "NoArbitratorForUserLocaleWarning";
        new Popup().information(Res.get("offerbook.info.noArbitrationInUserLanguage",
                getArbitrationLanguages(), LanguageUtil.getDisplayName(preferences.getUserLanguage())))
                .closeButtonText(Res.get("shared.ok"))
                .dontShowAgainId(key)
                .show();
    }

    private String getArbitrationLanguages() {
        return arbitratorManager.getObservableMap().values().stream()
                .flatMap(arbitrator -> arbitrator.getLanguageCodes().stream())
                .distinct()
                .map(LanguageUtil::getDisplayName)
                .collect(Collectors.joining(", "));
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
        offerBookView.enableCreateOfferButton();

        navigation.navigateTo(MainView.class, this.getClass(), OfferBookView.class);
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

        navigation.navigateTo(MainView.class, this.getClass(), OfferBookView.class);
    }

    public interface OfferActionHandler {
        void onCreateOffer(TradeCurrency tradeCurrency, PaymentMethod paymentMethod);

        void onTakeOffer(Offer offer);
    }

    public interface CloseHandler {
        void close();
    }
}
