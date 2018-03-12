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
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.MainView;
import bisq.desktop.main.offer.createoffer.CreateOfferView;
import bisq.desktop.main.offer.offerbook.OfferBookView;
import bisq.desktop.main.offer.takeoffer.TakeOfferView;

import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.user.Preferences;

import bisq.common.GlobalSettings;
import bisq.common.UserThread;
import bisq.common.locale.CurrencyUtil;
import bisq.common.locale.Res;
import bisq.common.locale.TradeCurrency;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;

import javafx.beans.value.ChangeListener;

import javafx.collections.ListChangeListener;

import java.util.List;
import java.util.Optional;

public abstract class OfferView extends ActivatableView<TabPane, Void> {

    private OfferBookView offerBookView;
    private CreateOfferView createOfferView;
    private TakeOfferView takeOfferView;
    private AnchorPane createOfferPane;
    private Tab takeOfferTab, createOfferTab, offerBookTab;

    private AnchorPane takeOfferPane;
    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private final Preferences preferences;
    private final OfferPayload.Direction direction;

    private Offer offer;
    private TradeCurrency tradeCurrency;
    private boolean createOfferViewOpen, takeOfferViewOpen;
    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;
    private ListChangeListener<Tab> tabListChangeListener;

    protected OfferView(ViewLoader viewLoader, Navigation navigation, Preferences preferences) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
        this.preferences = preferences;
        this.direction = (this instanceof BuyOfferView) ? OfferPayload.Direction.BUY : OfferPayload.Direction.SELL;
    }

    @Override
    protected void initialize() {
        navigationListener = viewPath -> {
            if (viewPath.size() == 3 && viewPath.indexOf(this.getClass()) == 1)
                loadView(viewPath.tip());
        };
        tabChangeListener = (observableValue, oldValue, newValue) -> {
            UserThread.execute(InputTextField::hideErrorMessageDisplay);
            if (newValue != null) {
                if (newValue.equals(createOfferTab) && createOfferView != null) {
                    createOfferView.onTabSelected(true);
                } else if (newValue.equals(takeOfferTab) && takeOfferView != null) {
                    takeOfferView.onTabSelected(true);
                } else if (newValue.equals(offerBookTab) && offerBookView != null) {
                    offerBookView.onTabSelected(true);
                }
            }
            if (oldValue != null) {
                if (oldValue.equals(createOfferTab) && createOfferView != null) {
                    createOfferView.onTabSelected(false);
                } else if (oldValue.equals(takeOfferTab) && takeOfferView != null) {
                    takeOfferView.onTabSelected(false);
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
    }

    @Override
    protected void activate() {
        Optional<TradeCurrency> tradeCurrencyOptional = (this instanceof SellOfferView) ?
                CurrencyUtil.getTradeCurrency(preferences.getSellScreenCurrencyCode()) :
                CurrencyUtil.getTradeCurrency(preferences.getBuyScreenCurrencyCode());
        if (tradeCurrencyOptional.isPresent())
            tradeCurrency = tradeCurrencyOptional.get();
        else {
            tradeCurrency = GlobalSettings.getDefaultTradeCurrency();
        }

        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        root.getTabs().addListener(tabListChangeListener);
        navigation.addListener(navigationListener);
        //noinspection unchecked
        navigation.navigateTo(MainView.class, this.getClass(), OfferBookView.class);
    }

    @Override
    protected void deactivate() {
        navigation.removeListener(navigationListener);
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
        root.getTabs().removeListener(tabListChangeListener);
    }

    private String getCreateOfferTabName() {
        return Res.get("offerbook.createOffer");
    }

    private String getTakeOfferTabName() {
        return Res.get("offerbook.takeOffer");
    }

    private void loadView(Class<? extends View> viewClass) {
        TabPane tabPane = root;
        View view;
        boolean isBuy = direction == OfferPayload.Direction.BUY;

        if (viewClass == OfferBookView.class && offerBookView == null) {
            view = viewLoader.load(viewClass);
            // Offerbook must not be cached by ViewLoader as we use 2 instances for sell and buy screens.
            offerBookTab = new Tab(isBuy ? Res.get("shared.buyBitcoin") : Res.get("shared.sellBitcoin"));
            offerBookTab.setClosable(false);
            offerBookTab.setContent(view.getRoot());
            tabPane.getTabs().add(offerBookTab);
            offerBookView = (OfferBookView) view;
            offerBookView.onTabSelected(true);

            OfferActionHandler offerActionHandler = new OfferActionHandler() {
                @Override
                public void onCreateOffer(TradeCurrency tradeCurrency) {
                    if (!createOfferViewOpen) {
                        OfferView.this.createOfferViewOpen = true;
                        OfferView.this.tradeCurrency = tradeCurrency;
                        //noinspection unchecked
                        OfferView.this.navigation.navigateTo(MainView.class, OfferView.this.getClass(),
                                CreateOfferView.class);
                    } else {
                        log.error("You have already a \"Create offer\" tab open.");
                    }
                }

                @Override
                public void onTakeOffer(Offer offer) {
                    if (!takeOfferViewOpen) {
                        OfferView.this.takeOfferViewOpen = true;
                        OfferView.this.offer = offer;
                        //noinspection unchecked
                        OfferView.this.navigation.navigateTo(MainView.class, OfferView.this.getClass(),
                                TakeOfferView.class);
                    } else {
                        log.error("You have already a \"Take offer\" tab open.");
                    }
                }
            };
            offerBookView.setOfferActionHandler(offerActionHandler);
            offerBookView.setDirection(direction);
        } else if (viewClass == CreateOfferView.class && createOfferView == null) {
            view = viewLoader.load(viewClass);
            // CreateOffer and TakeOffer must not be cached by ViewLoader as we cannot use a view multiple times
            // in different graphs
            createOfferView = (CreateOfferView) view;
            createOfferView.initWithData(direction, tradeCurrency);
            createOfferPane = createOfferView.getRoot();
            createOfferTab = new Tab(getCreateOfferTabName());
            // close handler from close on create offer action
            createOfferView.setCloseHandler(() -> tabPane.getTabs().remove(createOfferTab));
            createOfferTab.setContent(createOfferPane);
            tabPane.getTabs().add(createOfferTab);
            tabPane.getSelectionModel().select(createOfferTab);
        } else if (viewClass == TakeOfferView.class && takeOfferView == null && offer != null) {
            view = viewLoader.load(viewClass);
            // CreateOffer and TakeOffer must not be cached by ViewLoader as we cannot use a view multiple times
            // in different graphs
            takeOfferView = (TakeOfferView) view;
            takeOfferView.initWithData(offer);
            takeOfferPane = ((TakeOfferView) view).getRoot();
            takeOfferTab = new Tab(getTakeOfferTabName());
            // close handler from close on take offer action
            takeOfferView.setCloseHandler(() -> tabPane.getTabs().remove(takeOfferTab));
            takeOfferTab.setContent(takeOfferPane);
            tabPane.getTabs().add(takeOfferTab);
            tabPane.getSelectionModel().select(takeOfferTab);
        }
    }

    private void onCreateOfferViewRemoved() {
        createOfferViewOpen = false;
        if (createOfferView != null) {
            createOfferView.onClose();
            createOfferView = null;
        }
        offerBookView.enableCreateOfferButton();

        // update the navigation state
        //noinspection unchecked
        navigation.navigateTo(MainView.class, this.getClass(), OfferBookView.class);
    }

    private void onTakeOfferViewRemoved() {
        offer = null;
        takeOfferViewOpen = false;
        if (takeOfferView != null) {
            takeOfferView.onClose();
            takeOfferView = null;
        }

        // update the navigation state
        //noinspection unchecked
        navigation.navigateTo(MainView.class, this.getClass(), OfferBookView.class);
    }

    public interface OfferActionHandler {
        void onCreateOffer(TradeCurrency tradeCurrency);

        void onTakeOffer(Offer offer);
    }

    public interface CloseHandler {
        void close();
    }
}

