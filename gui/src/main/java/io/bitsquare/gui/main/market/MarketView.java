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

package io.bitsquare.gui.main.market;

import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.model.Activatable;
import io.bitsquare.gui.common.view.*;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.market.offerbook.OfferBookChartView;
import io.bitsquare.gui.main.market.spread.SpreadView;
import io.bitsquare.gui.main.market.trades.TradesChartsView;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;

import javax.inject.Inject;

@FxmlView
public class MarketView extends ActivatableViewAndModel<TabPane, Activatable> {
    @FXML
    TabPane root;
    @FXML
    ScrollPane chartsScroll, statsScroll;
    @FXML
    Tab chartsTab, tradesTab, statisticsTab;
    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    @Inject
    public MarketView(CachingViewLoader viewLoader, Navigation navigation) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        AnchorPane.setTopAnchor(root, MainView.scale(0));
        AnchorPane.setRightAnchor(root, MainView.scale(0));
        AnchorPane.setBottomAnchor(root, MainView.scale(0));
        AnchorPane.setLeftAnchor(root, MainView.scale(0));
        AnchorPane.setTopAnchor(chartsScroll, MainView.scale(0));
        AnchorPane.setRightAnchor(chartsScroll, MainView.scale(0));
        AnchorPane.setBottomAnchor(chartsScroll, MainView.scale(0));
        AnchorPane.setLeftAnchor(chartsScroll, MainView.scale(0));
        AnchorPane.setTopAnchor(statsScroll, MainView.scale(0));
        AnchorPane.setRightAnchor(statsScroll, MainView.scale(0));
        AnchorPane.setBottomAnchor(statsScroll, MainView.scale(0));
        AnchorPane.setLeftAnchor(statsScroll, MainView.scale(0));

        navigationListener = viewPath -> {
            if (viewPath.size() == 3 && viewPath.indexOf(MarketView.class) == 1)
                loadView(viewPath.tip());
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == chartsTab)
                navigation.navigateTo(MainView.class, MarketView.class, OfferBookChartView.class);
            else if (newValue == tradesTab)
                navigation.navigateTo(MainView.class, MarketView.class, TradesChartsView.class);
            else if (newValue == statisticsTab)
                navigation.navigateTo(MainView.class, MarketView.class, SpreadView.class);
        };
    }

    @Override
    protected void activate() {
        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

        if (root.getSelectionModel().getSelectedItem() == chartsTab)
            navigation.navigateTo(MainView.class, MarketView.class, OfferBookChartView.class);
        else if (root.getSelectionModel().getSelectedItem() == tradesTab)
            navigation.navigateTo(MainView.class, MarketView.class, TradesChartsView.class);
        else
            navigation.navigateTo(MainView.class, MarketView.class, SpreadView.class);
    }

    @Override
    protected void deactivate() {
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
        navigation.removeListener(navigationListener);
    }

    private void loadView(Class<? extends View> viewClass) {
        final Tab tab;
        View view = viewLoader.load(viewClass);

        if (view instanceof OfferBookChartView) tab = chartsTab;
        else if (view instanceof TradesChartsView) tab = tradesTab;
        else if (view instanceof SpreadView) tab = statisticsTab;
        else throw new IllegalArgumentException("Navigation to " + viewClass + " is not supported");

        if (tab.getContent() != null && tab.getContent() instanceof ScrollPane) {
            ((ScrollPane) tab.getContent()).setContent(view.getRoot());
        } else {
            tab.setContent(view.getRoot());
        }
        root.getSelectionModel().select(tab);
    }

}
