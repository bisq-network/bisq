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
import bisq.desktop.main.offer.offerbook.BsqOfferBookViewModel;
import bisq.desktop.main.offer.offerbook.BtcOfferBookView;
import bisq.desktop.main.offer.offerbook.OfferBookView;
import bisq.desktop.main.offer.offerbook.OtherOfferBookView;
import bisq.desktop.main.offer.offerbook.TopAltcoinOfferBookView;
import bisq.desktop.main.offer.offerbook.TopAltcoinOfferBookViewModel;
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

import javafx.beans.value.ChangeListener;

import java.util.Optional;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public abstract class OfferView extends ActivatableView<TabPane, Void> {

    private OfferBookView<?, ?> btcOfferBookView, bsqOfferBookView, topAltcoinOfferBookView, otherOfferBookView;

    private Tab btcOfferBookTab, bsqOfferBookTab, topAltcoinOfferBookTab, otherOfferBookTab;

    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private final Preferences preferences;
    private final User user;
    private final P2PService p2PService;
    private final OfferDirection direction;

    private Offer offer;
    private TradeCurrency tradeCurrency;
    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;
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
            if (viewPath.size() == 3 && viewPath.indexOf(this.getClass()) == 1) {
                loadView(viewPath.tip(), null, data);
            } else if (viewPath.size() == 4 && viewPath.indexOf(this.getClass()) == 1) {
                loadView(viewPath.get(2), viewPath.tip(), data);
            }
        };
        tabChangeListener = (observableValue, oldValue, newValue) -> {
            if (newValue != null) {
                if (newValue.equals(btcOfferBookTab)) {
                    if (btcOfferBookView != null) {
                        btcOfferBookView.onTabSelected(true);
                    } else {
                        loadView(BtcOfferBookView.class, null, null);
                    }
                } else if (newValue.equals(bsqOfferBookTab)) {
                    if (bsqOfferBookView != null) {
                        bsqOfferBookView.onTabSelected(true);
                    } else {
                        loadView(BsqOfferBookView.class, null, null);
                    }
                } else if (newValue.equals(topAltcoinOfferBookTab)) {
                    if (topAltcoinOfferBookView != null) {
                        topAltcoinOfferBookView.onTabSelected(true);
                    } else {
                        loadView(TopAltcoinOfferBookView.class, null, null);
                    }
                } else if (newValue.equals(otherOfferBookTab)) {
                    if (otherOfferBookView != null) {
                        otherOfferBookView.onTabSelected(true);
                    } else {
                        loadView(OtherOfferBookView.class, null, null);
                    }
                }
            }
            if (oldValue != null) {
                if (oldValue.equals(btcOfferBookTab) && btcOfferBookView != null) {
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

        offerActionHandler = new OfferActionHandler() {
            @Override
            public void onCreateOffer(TradeCurrency tradeCurrency, PaymentMethod paymentMethod) {
                if (canCreateOrTakeOffer(tradeCurrency)) {
                    showCreateOffer(tradeCurrency, paymentMethod);
                }
            }

            @Override
            public void onTakeOffer(Offer offer) {
                Optional<TradeCurrency> optionalTradeCurrency = CurrencyUtil.getTradeCurrency(offer.getCurrencyCode());
                if (optionalTradeCurrency.isPresent() && canCreateOrTakeOffer(optionalTradeCurrency.get())) {
                    showTakeOffer(offer);
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
        navigation.addListener(navigationListener);
        if (btcOfferBookView == null) {
            navigation.navigateTo(MainView.class, this.getClass(), BtcOfferBookView.class);
        }
    }

    @Override
    protected void deactivate() {
        navigation.removeListener(navigationListener);
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
    }

    private void loadView(Class<? extends View> viewClass,
                          Class<? extends View> childViewClass,
                          @Nullable Object data) {
        TabPane tabPane = root;
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        if (OfferBookView.class.isAssignableFrom(viewClass)) {

            if (viewClass == BtcOfferBookView.class && btcOfferBookTab != null && btcOfferBookView != null) {
                if (childViewClass == null) {
                    btcOfferBookTab.setContent(btcOfferBookView.getRoot());
                } else if (childViewClass == TakeOfferView.class) {
                    loadTakeViewClass(viewClass, childViewClass, btcOfferBookTab);
                } else {
                    loadCreateViewClass(btcOfferBookView, viewClass, childViewClass, btcOfferBookTab, (PaymentMethod) data, null);
                }
                tabPane.getSelectionModel().select(btcOfferBookTab);
            } else if (viewClass == BsqOfferBookView.class && bsqOfferBookTab != null && bsqOfferBookView != null) {
                if (childViewClass == null) {
                    bsqOfferBookTab.setContent(bsqOfferBookView.getRoot());
                } else if (childViewClass == TakeOfferView.class) {
                    loadTakeViewClass(viewClass, childViewClass, bsqOfferBookTab);
                } else if (data instanceof BsqSwapOfferPayload) {
                    loadCreateViewClass(bsqOfferBookView, viewClass, childViewClass, bsqOfferBookTab, PaymentMethod.BSQ_SWAP, (BsqSwapOfferPayload) data);
                } else {
                    tradeCurrency = BsqOfferBookViewModel.BSQ;
                    loadCreateViewClass(bsqOfferBookView, viewClass, childViewClass, bsqOfferBookTab, (PaymentMethod) data, null);
                }
                tabPane.getSelectionModel().select(bsqOfferBookTab);
            } else if (viewClass == TopAltcoinOfferBookView.class && topAltcoinOfferBookTab != null && topAltcoinOfferBookView != null) {
                if (childViewClass == null) {
                    topAltcoinOfferBookTab.setContent(topAltcoinOfferBookView.getRoot());
                } else if (childViewClass == TakeOfferView.class) {
                    loadTakeViewClass(viewClass, childViewClass, topAltcoinOfferBookTab);
                } else {
                    tradeCurrency = TopAltcoinOfferBookViewModel.TOP_ALTCOIN;
                    loadCreateViewClass(topAltcoinOfferBookView, viewClass, childViewClass, topAltcoinOfferBookTab, (PaymentMethod) data, null);
                }
                tabPane.getSelectionModel().select(topAltcoinOfferBookTab);
            } else if (viewClass == OtherOfferBookView.class && otherOfferBookTab != null && otherOfferBookView != null) {
                if (childViewClass == null) {
                    otherOfferBookTab.setContent(otherOfferBookView.getRoot());
                } else if (childViewClass == TakeOfferView.class) {
                    loadTakeViewClass(viewClass, childViewClass, otherOfferBookTab);
                } else {
                    //add sanity check in case of app restart
                    if (CurrencyUtil.isFiatCurrency(tradeCurrency.getCode())) {
                        Optional<TradeCurrency> tradeCurrencyOptional = (this.direction == OfferDirection.SELL) ?
                                CurrencyUtil.getTradeCurrency(preferences.getSellScreenCryptoCurrencyCode()) :
                                CurrencyUtil.getTradeCurrency(preferences.getBuyScreenCryptoCurrencyCode());
                        tradeCurrency = tradeCurrencyOptional.isEmpty() ? OfferViewUtil.getAnyOfMainCryptoCurrencies() : tradeCurrencyOptional.get();
                    }
                    loadCreateViewClass(otherOfferBookView, viewClass, childViewClass, otherOfferBookTab, (PaymentMethod) data, null);
                }
                tabPane.getSelectionModel().select(otherOfferBookTab);
            } else {
                if (btcOfferBookTab == null) {
                    btcOfferBookTab = new Tab(Res.getBaseCurrencyName().toUpperCase());
                    btcOfferBookTab.setClosable(false);
                    bsqOfferBookTab = new Tab(BsqOfferBookViewModel.BSQ.getCode());
                    bsqOfferBookTab.setClosable(false);
                    topAltcoinOfferBookTab = new Tab(TopAltcoinOfferBookViewModel.TOP_ALTCOIN.getCode());
                    topAltcoinOfferBookTab.setClosable(false);
                    otherOfferBookTab = new Tab(Res.get("shared.other").toUpperCase());
                    otherOfferBookTab.setClosable(false);

                    tabPane.getTabs().addAll(btcOfferBookTab, bsqOfferBookTab, topAltcoinOfferBookTab, otherOfferBookTab);
                }
                if (viewClass == BtcOfferBookView.class) {
                    btcOfferBookView = (BtcOfferBookView) viewLoader.load(BtcOfferBookView.class);
                    btcOfferBookTab.setContent(btcOfferBookView.getRoot());
                    btcOfferBookView.setOfferActionHandler(offerActionHandler);
                    btcOfferBookView.setDirection(direction);
                    tabPane.getSelectionModel().select(btcOfferBookTab);
                    btcOfferBookView.onTabSelected(true);
                } else if (viewClass == BsqOfferBookView.class) {
                    bsqOfferBookView = (BsqOfferBookView) viewLoader.load(BsqOfferBookView.class);
                    bsqOfferBookView.setOfferActionHandler(offerActionHandler);
                    bsqOfferBookView.setDirection(direction);
                    tabPane.getSelectionModel().select(bsqOfferBookTab);
                    bsqOfferBookTab.setContent(bsqOfferBookView.getRoot());
                    bsqOfferBookView.onTabSelected(true);
                } else if (viewClass == TopAltcoinOfferBookView.class) {
                    topAltcoinOfferBookView = (TopAltcoinOfferBookView) viewLoader.load(TopAltcoinOfferBookView.class);
                    topAltcoinOfferBookView.setOfferActionHandler(offerActionHandler);
                    topAltcoinOfferBookView.setDirection(direction);
                    tabPane.getSelectionModel().select(topAltcoinOfferBookTab);
                    topAltcoinOfferBookTab.setContent(topAltcoinOfferBookView.getRoot());
                    topAltcoinOfferBookView.onTabSelected(true);
                } else if (viewClass == OtherOfferBookView.class) {
                    otherOfferBookView = (OtherOfferBookView) viewLoader.load(OtherOfferBookView.class);
                    otherOfferBookView.setOfferActionHandler(offerActionHandler);
                    otherOfferBookView.setDirection(direction);
                    tabPane.getSelectionModel().select(otherOfferBookTab);
                    otherOfferBookTab.setContent(otherOfferBookView.getRoot());
                    otherOfferBookView.onTabSelected(true);
                }
            }
        }
    }

    private void loadCreateViewClass(OfferBookView<?, ?> offerBookView,
                                     Class<? extends View> viewClass,
                                     Class<? extends View> childViewClass,
                                     Tab marketOfferBookTab,
                                     @Nullable PaymentMethod paymentMethod,
                                     @Nullable BsqSwapOfferPayload payload) {
        if (tradeCurrency == null) {
            return;
        }

        View view;
        // CreateOffer and TakeOffer must not be cached by ViewLoader as we cannot use a view multiple times
        // in different graphs
        if ((paymentMethod != null && (paymentMethod.isBsqSwap() || paymentMethod.getId().equals(GUIUtil.SHOW_ALL_FLAG))) ||
                (paymentMethod == null && viewClass.equals(BsqOfferBookView.class))) {
            view = viewLoader.load(BsqSwapCreateOfferView.class);
            ((BsqSwapCreateOfferView) view).initWithData(direction, offerActionHandler, payload);
        } else {
            view = viewLoader.load(childViewClass);
            ((CreateOfferView) view).initWithData(direction, tradeCurrency, offerActionHandler);
        }

        ((SelectableView) view).onTabSelected(true);

        ((ClosableView) view).setCloseHandler(() -> {
            offerBookView.enableCreateOfferButton();
            ((SelectableView) view).onTabSelected(false);
            navigation.navigateTo(MainView.class, this.getClass(), viewClass);
        });


        // close handler from close on create offer action
        marketOfferBookTab.setContent(view.getRoot());
    }

    private void loadTakeViewClass(Class<? extends View> viewClass,
                                   Class<? extends View> childViewClass,
                                   Tab marketOfferBookTab) {

        if (offer == null) {
            return;
        }

        View view = viewLoader.load(offer.isBsqSwapOffer() ? BsqSwapTakeOfferView.class : childViewClass);
        // CreateOffer and TakeOffer must not be cached by ViewLoader as we cannot use a view multiple times
        // in different graphs
        ((InitializableViewWithTakeOfferData) view).initWithData(offer);
        ((SelectableView) view).onTabSelected(true);

        // close handler from close on take offer action
        ((ClosableView) view).setCloseHandler(() -> {
            ((SelectableView) view).onTabSelected(false);
            navigation.navigateTo(MainView.class, this.getClass(), viewClass);
        });
        marketOfferBookTab.setContent(view.getRoot());
    }

    protected boolean canCreateOrTakeOffer(TradeCurrency tradeCurrency) {
        return GUIUtil.isBootstrappedOrShowPopup(p2PService) &&
                GUIUtil.canCreateOrTakeOfferOrShowPopup(user, navigation, tradeCurrency);
    }

    private void showTakeOffer(Offer offer) {
        this.offer = offer;

        Class<? extends OfferBookView<?, ?>> offerBookViewClass = getOfferBookViewClassFor(offer.getCurrencyCode());
        navigation.navigateTo(MainView.class, this.getClass(), offerBookViewClass, TakeOfferView.class);
    }

    private void showCreateOffer(TradeCurrency tradeCurrency, PaymentMethod paymentMethod) {
        this.tradeCurrency = tradeCurrency;

        Class<? extends OfferBookView<?, ?>> offerBookViewClass = getOfferBookViewClassFor(tradeCurrency.getCode());
        navigation.navigateToWithData(paymentMethod, MainView.class, this.getClass(), offerBookViewClass, CreateOfferView.class);
    }

    @NotNull
    private Class<? extends OfferBookView<?, ?>> getOfferBookViewClassFor(String currencyCode) {
        Class<? extends OfferBookView<?, ?>> offerBookViewClass;
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            offerBookViewClass = BtcOfferBookView.class;
        } else if (currencyCode.equals(BsqOfferBookViewModel.BSQ.getCode())) {
            offerBookViewClass = BsqOfferBookView.class;
        } else if (currencyCode.equals(TopAltcoinOfferBookViewModel.TOP_ALTCOIN.getCode())) {
            offerBookViewClass = TopAltcoinOfferBookView.class;
        } else {
            offerBookViewClass = OtherOfferBookView.class;
        }
        return offerBookViewClass;
    }

    public interface OfferActionHandler {
        void onCreateOffer(TradeCurrency tradeCurrency, PaymentMethod paymentMethod);

        void onTakeOffer(Offer offer);
    }

    public interface CloseHandler {
        void close();
    }
}
