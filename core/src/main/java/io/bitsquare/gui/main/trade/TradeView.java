/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.trade;

import io.bitsquare.common.viewfx.view.ActivatableView;
import io.bitsquare.common.viewfx.view.View;
import io.bitsquare.common.viewfx.view.ViewLoader;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.trade.createoffer.CreateOfferView;
import io.bitsquare.gui.main.trade.offerbook.OfferBookView;
import io.bitsquare.gui.main.trade.takeoffer.TakeOfferView;
import io.bitsquare.offer.Direction;
import io.bitsquare.offer.Offer;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import java.util.List;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public abstract class TradeView extends ActivatableView<TabPane, Void> {

    private OfferBookView offerBookView;
    private CreateOfferView createOfferView;
    private TakeOfferView takeOfferView;
    private AnchorPane createOfferPane;
    private AnchorPane takeOfferPane;
    private Navigation.Listener listener;
    private Coin amount;
    private Fiat price;
    private Offer offer;

    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private final Direction direction;

    protected TradeView(ViewLoader viewLoader, Navigation navigation) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
        this.direction = (this instanceof BuyView) ? Direction.BUY : Direction.SELL;
    }

    @Override
    protected void initialize() {
        listener = viewPath -> {
            if (viewPath.size() == 3 && viewPath.indexOf(this.getClass()) == 1)
                loadView(viewPath.tip());
        };
    }

    @Override
    protected void activate() {
        // We need to remove open validation error popups
        // Platform.runLater needed as focus-out event is called after selectedIndexProperty changed
        // TODO Find a way to do that in the InputTextField directly, but a tab change does not trigger any event...
        TabPane tabPane = root;
        tabPane.getSelectionModel().selectedIndexProperty()
                .addListener((observableValue, oldValue, newValue) -> Platform.runLater(InputTextField::hideErrorMessageDisplay));

        // We want to get informed when a tab get closed
        tabPane.getTabs().addListener((ListChangeListener<Tab>) change -> {
            change.next();
            List<? extends Tab> removedTabs = change.getRemoved();
            if (removedTabs.size() == 1) {
                if (removedTabs.get(0).getContent().equals(createOfferPane))
                    onCreateOfferViewRemoved();
                else if (removedTabs.get(0).getContent().equals(takeOfferPane))
                    onTakeOfferViewRemoved();
            }
        });

        navigation.addListener(listener);
        navigation.navigateTo(MainView.class, this.getClass(), OfferBookView.class);
    }

    @Override
    protected void deactivate() {
        navigation.removeListener(listener);
    }

    public void createOffer(Coin amount, Fiat price) {
        this.amount = amount;
        this.price = price;
        navigation.navigateTo(MainView.class, this.getClass(), CreateOfferView.class);
    }

    public void takeOffer(Coin amount, Fiat price, Offer offer) {
        this.amount = amount;
        this.price = price;
        this.offer = offer;
        navigation.navigateTo(MainView.class, this.getClass(), TakeOfferView.class);
    }

    private void loadView(Class<? extends View> viewClass) {
        TabPane tabPane = root;
        View view;

        if (viewClass == OfferBookView.class && offerBookView == null) {
            view = viewLoader.load(viewClass);
            // Offerbook must not be cached by ViewLoader as we use 2 instances for sell and buy screens.
            final Tab tab = new Tab(direction == Direction.BUY ? "Buy Bitcoin" : "Sell Bitcoin");
            tab.setClosable(false);
            tab.setContent(view.getRoot());
            tabPane.getTabs().add(tab);
            offerBookView = (OfferBookView) view;

            OfferActionHandler offerActionHandler = new OfferActionHandler() {
                @Override
                public void createOffer(Coin amount, Fiat price) {
                    if (TradeView.this instanceof SellView) {
                        Popups.openWarningPopup("Warning",
                                "Please note that a sell offer is not supported yet for trading",
                                "You can create the offer and it appears in the offerbook, " +
                                        "but nobody can take the offer.\n" +
                                        "That will be implemented in an upcoming development milestone.");
                    }
                    TradeView.this.amount = amount;
                    TradeView.this.price = price;
                    TradeView.this.navigation.navigateTo(MainView.class, TradeView.this.getClass(),
                            CreateOfferView.class);
                }

                @Override
                public void takeOffer(Coin amount, Fiat price, Offer offer) {
                    TradeView.this.amount = amount;
                    TradeView.this.price = price;
                    TradeView.this.offer = offer;
                    TradeView.this.navigation.navigateTo(MainView.class, TradeView.this.getClass(),
                            TakeOfferView.class);
                }
            };
            offerBookView.setOfferActionHandler(offerActionHandler);

            offerBookView.setDirection(direction);
        }
        else if (viewClass == CreateOfferView.class && createOfferView == null) {
            view = viewLoader.load(viewClass);
            // CreateOffer and TakeOffer must not be cached by ViewLoader as we cannot use a view multiple times
            // in different graphs
            createOfferView = (CreateOfferView) view;
            createOfferView.initWithData(direction, amount, price);
            createOfferPane = ((CreateOfferView) view).getRoot();
            final Tab tab = new Tab("Create offer");
            createOfferView.setCloseHandler(() -> {
                if (tabPane.getTabs().size() == 2)
                    tabPane.getTabs().remove(1);
            });
            tab.setContent(createOfferPane);
            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tab);
        }
        else if (viewClass == TakeOfferView.class && takeOfferView == null && offer != null) {
            view = viewLoader.load(viewClass);
            // CreateOffer and TakeOffer must not be cached by ViewLoader as we cannot use a view multiple times
            // in different graphs
            takeOfferView = (TakeOfferView) view;
            takeOfferView.initWithData(direction, amount, offer);
            takeOfferPane = ((TakeOfferView) view).getRoot();
            final Tab tab = new Tab("Take offer");
            takeOfferView.setCloseHandler(() -> {
                if (tabPane.getTabs().size() == 2)
                    tabPane.getTabs().remove(1);
            });
            tab.setContent(takeOfferPane);
            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tab);
        }
    }


    private void onCreateOfferViewRemoved() {
        createOfferView = null;
        offerBookView.enableCreateOfferButton();

        // update the navigation state
        navigation.navigateTo(MainView.class, this.getClass(), OfferBookView.class);
    }

    private void onTakeOfferViewRemoved() {
        takeOfferView = null;

        // update the navigation state
        navigation.navigateTo(MainView.class, this.getClass(), OfferBookView.class);
    }

    public interface OfferActionHandler {
        void createOffer(Coin amount, Fiat price);

        void takeOffer(Coin amount, Fiat price, Offer offer);
    }

    public interface CloseHandler {
        void close();
    }
}

