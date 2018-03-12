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

package bisq.desktop.main.dao.voting;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.common.view.View;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.common.view.ViewPath;
import bisq.desktop.components.AutoTooltipToggleButton;
import bisq.desktop.main.MainView;
import bisq.desktop.main.dao.DaoView;
import bisq.desktop.main.dao.voting.dashboard.VotingDashboardView;
import bisq.desktop.main.dao.voting.history.VotingHistoryView;
import bisq.desktop.main.dao.voting.vote.VoteView;
import bisq.desktop.util.Colors;

import bisq.common.locale.Res;

import javax.inject.Inject;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.fxml.FXML;

import javafx.scene.control.Label;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

import javafx.beans.value.ChangeListener;

@FxmlView
public class VotingView extends ActivatableViewAndModel {

    private final ViewLoader viewLoader;
    private final Navigation navigation;

    private MenuItem dashboard, vote, history;
    private Navigation.Listener listener;

    @FXML
    private VBox leftVBox;
    @FXML
    private AnchorPane content;

    private Class<? extends View> selectedViewClass;

    @Inject
    private VotingView(CachingViewLoader viewLoader, Navigation navigation) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        listener = viewPath -> {
            if (viewPath.size() != 4 || viewPath.indexOf(VotingView.class) != 2)
                return;

            selectedViewClass = viewPath.tip();
            loadView(selectedViewClass);
        };

        ToggleGroup toggleGroup = new ToggleGroup();
        dashboard = new MenuItem(navigation, toggleGroup, Res.get("shared.dashboard"), VotingDashboardView.class, AwesomeIcon.DASHBOARD);
        vote = new MenuItem(navigation, toggleGroup, Res.get("share.vote"), VoteView.class, AwesomeIcon.EDIT);
        history = new MenuItem(navigation, toggleGroup, Res.get("share.history"), VotingHistoryView.class, AwesomeIcon.TABLE);
        leftVBox.getChildren().addAll(dashboard, vote, history);
    }

    @Override
    protected void activate() {
        dashboard.activate();
        vote.activate();
        history.activate();

        navigation.addListener(listener);
        ViewPath viewPath = navigation.getCurrentPath();
        if (viewPath.size() == 3 && viewPath.indexOf(VotingView.class) == 2 ||
                viewPath.size() == 2 && viewPath.indexOf(DaoView.class) == 1) {
            if (selectedViewClass == null)
                selectedViewClass = VotingDashboardView.class;

            loadView(selectedViewClass);

        } else if (viewPath.size() == 4 && viewPath.indexOf(VotingView.class) == 2) {
            selectedViewClass = viewPath.get(3);
            loadView(selectedViewClass);
        }
    }

    @Override
    protected void deactivate() {
        navigation.removeListener(listener);

        dashboard.deactivate();
        vote.deactivate();
        history.deactivate();
    }

    private void loadView(Class<? extends View> viewClass) {
        View view = viewLoader.load(viewClass);
        content.getChildren().setAll(view.getRoot());

        if (view instanceof VotingDashboardView) dashboard.setSelected(true);
        else if (view instanceof VoteView) vote.setSelected(true);
        else if (view instanceof VotingHistoryView) history.setSelected(true);
    }

    public Class<? extends View> getSelectedViewClass() {
        return selectedViewClass;
    }
}


class MenuItem extends AutoTooltipToggleButton {

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
        //noinspection unchecked
        setOnAction((event) -> navigation.navigateTo(MainView.class, DaoView.class, VotingView.class, viewClass));
        selectedProperty().addListener(selectedPropertyChangeListener);
        disableProperty().addListener(disablePropertyChangeListener);
    }

    public void deactivate() {
        setOnAction(null);
        selectedProperty().removeListener(selectedPropertyChangeListener);
        disableProperty().removeListener(disablePropertyChangeListener);
    }
}

