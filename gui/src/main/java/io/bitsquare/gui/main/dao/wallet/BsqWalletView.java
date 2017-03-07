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

package io.bitsquare.gui.main.dao.wallet;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.view.*;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.dao.DaoView;
import io.bitsquare.gui.main.dao.wallet.dashboard.BsqDashboardView;
import io.bitsquare.gui.main.dao.wallet.receive.BsqReceiveView;
import io.bitsquare.gui.main.dao.wallet.send.BsqSendView;
import io.bitsquare.gui.main.dao.wallet.tx.BsqTransactionsView;
import io.bitsquare.gui.util.Colors;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;

import javax.inject.Inject;

@FxmlView
public class BsqWalletView extends ActivatableViewAndModel {

    private final ViewLoader viewLoader;
    private final Navigation navigation;

    private MenuItem dashboard, send, receive, transactions;
    private Navigation.Listener listener;

    @FXML
    private VBox leftVBox;
    @FXML
    private AnchorPane content;

    private Class<? extends View> selectedViewClass;

    @Inject
    private BsqWalletView(CachingViewLoader viewLoader, Navigation navigation) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        listener = viewPath -> {
            if (viewPath.size() != 4 || viewPath.indexOf(BsqWalletView.class) != 2)
                return;

            selectedViewClass = viewPath.tip();
            loadView(selectedViewClass);
        };

        ToggleGroup toggleGroup = new ToggleGroup();
        dashboard = new MenuItem(navigation, toggleGroup, "Dashboard", BsqDashboardView.class, AwesomeIcon.DASHBOARD);
        send = new MenuItem(navigation, toggleGroup, "Send", BsqSendView.class, AwesomeIcon.SIGNOUT);
        receive = new MenuItem(navigation, toggleGroup, "Receive", BsqReceiveView.class, AwesomeIcon.SIGNIN);
        transactions = new MenuItem(navigation, toggleGroup, "Transactions", BsqTransactionsView.class, AwesomeIcon.TABLE);
        leftVBox.getChildren().addAll(dashboard, send, receive, transactions);
    }

    @Override
    protected void activate() {
        dashboard.activate();
        send.activate();
        receive.activate();
        transactions.activate();

        navigation.addListener(listener);
        ViewPath viewPath = navigation.getCurrentPath();
        if (viewPath.size() == 3 && viewPath.indexOf(BsqWalletView.class) == 2 ||
                viewPath.size() == 2 && viewPath.indexOf(DaoView.class) == 1) {
            if (selectedViewClass == null)
                selectedViewClass = BsqDashboardView.class;

            loadView(selectedViewClass);

        } else if (viewPath.size() == 4 && viewPath.indexOf(BsqWalletView.class) == 2) {
            selectedViewClass = viewPath.get(3);
            loadView(selectedViewClass);
        }
    }

    @Override
    protected void deactivate() {
        navigation.removeListener(listener);

        dashboard.deactivate();
        send.deactivate();
        receive.deactivate();
        transactions.deactivate();
    }

    private void loadView(Class<? extends View> viewClass) {
        View view = viewLoader.load(viewClass);
        content.getChildren().setAll(view.getRoot());

        if (view instanceof BsqDashboardView) dashboard.setSelected(true);
        else if (view instanceof BsqSendView) send.setSelected(true);
        else if (view instanceof BsqReceiveView) receive.setSelected(true);
        else if (view instanceof BsqTransactionsView) transactions.setSelected(true);
    }

    public Class<? extends View> getSelectedViewClass() {
        return selectedViewClass;
    }
}


class MenuItem extends ToggleButton {

    private final ChangeListener<Boolean> selectedPropertyChangeListener;
    private final ChangeListener<Boolean> disablePropertyChangeListener;
    private final Navigation navigation;
    private final Class<? extends View> viewClass;

    MenuItem(Navigation navigation, ToggleGroup toggleGroup, String title, Class<? extends View> viewClass, AwesomeIcon awesomeIcon) {
        this.navigation = navigation;
        this.viewClass = viewClass;

        setToggleGroup(toggleGroup);
        setText(title);
        setId("account-settings-item-background-active");
        setPrefHeight(40);
        setPrefWidth(240);
        setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label();
        AwesomeDude.setIcon(icon, awesomeIcon);
        icon.setTextFill(Paint.valueOf("#333"));
        icon.setPadding(new Insets(0, 5, 0, 0));
        icon.setAlignment(Pos.CENTER);
        icon.setMinWidth(25);
        icon.setMaxWidth(25);
        setGraphic(icon);

        selectedPropertyChangeListener = (ov, oldValue, newValue) -> {
            if (newValue) {
                setId("account-settings-item-background-selected");
                icon.setTextFill(Colors.BLUE);
            } else {
                setId("account-settings-item-background-active");
                icon.setTextFill(Paint.valueOf("#333"));
            }
        };

        disablePropertyChangeListener = (ov, oldValue, newValue) -> {
            if (newValue) {
                setId("account-settings-item-background-disabled");
                icon.setTextFill(Paint.valueOf("#ccc"));
            } else {
                setId("account-settings-item-background-active");
                icon.setTextFill(Paint.valueOf("#333"));
            }
        };
    }

    public void activate() {
        setOnAction((event) -> navigation.navigateTo(MainView.class, DaoView.class, BsqWalletView.class, viewClass));
        selectedProperty().addListener(selectedPropertyChangeListener);
        disableProperty().addListener(disablePropertyChangeListener);
    }

    public void deactivate() {
        setOnAction(null);
        selectedProperty().removeListener(selectedPropertyChangeListener);
        disableProperty().removeListener(disablePropertyChangeListener);
    }
}

