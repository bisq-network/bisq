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

package io.bitsquare.gui.main.dao;

import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.model.Activatable;
import io.bitsquare.gui.common.view.*;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.dao.proposals.ProposalsView;
import io.bitsquare.gui.main.dao.tokenwallet.TokenWalletView;
import io.bitsquare.gui.main.dao.voting.VotingView;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import javax.inject.Inject;

@FxmlView
public class DaoView extends ActivatableViewAndModel<TabPane, Activatable> {
    @FXML
    Tab tokenWalletTab, proposalsTab, votingTab;
    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    @Inject
    public DaoView(CachingViewLoader viewLoader, Navigation navigation) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        navigationListener = viewPath -> {
            if (viewPath.size() == 3 && viewPath.indexOf(DaoView.class) == 1)
                loadView(viewPath.tip());
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == tokenWalletTab)
                navigation.navigateTo(MainView.class, DaoView.class, TokenWalletView.class);
            else if (newValue == proposalsTab)
                navigation.navigateTo(MainView.class, DaoView.class, ProposalsView.class);
            else if (newValue == votingTab)
                navigation.navigateTo(MainView.class, DaoView.class, VotingView.class);
        };
    }

    @Override
    protected void activate() {
        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

        Tab selectedItem = root.getSelectionModel().getSelectedItem();
        if (selectedItem == tokenWalletTab)
            navigation.navigateTo(MainView.class, DaoView.class, TokenWalletView.class);
        else if (selectedItem == proposalsTab)
            navigation.navigateTo(MainView.class, DaoView.class, ProposalsView.class);
        else if (selectedItem == votingTab)
            navigation.navigateTo(MainView.class, DaoView.class, VotingView.class);
    }

    @Override
    protected void deactivate() {
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
        navigation.removeListener(navigationListener);
    }

    private void loadView(Class<? extends View> viewClass) {
        final Tab tab;
        View view = viewLoader.load(viewClass);

        if (view instanceof TokenWalletView) tab = tokenWalletTab;
        else if (view instanceof ProposalsView) tab = proposalsTab;
        else if (view instanceof VotingView) tab = votingTab;
        else throw new IllegalArgumentException("Navigation to " + viewClass + " is not supported");

        tab.setContent(view.getRoot());
        root.getSelectionModel().select(tab);
    }
}

