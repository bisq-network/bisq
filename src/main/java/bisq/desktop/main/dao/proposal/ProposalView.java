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

package bisq.desktop.main.dao.proposal;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.common.view.View;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.common.view.ViewPath;
import bisq.desktop.components.MenuItem;
import bisq.desktop.main.dao.DaoView;
import bisq.desktop.main.dao.proposal.active.ActiveProposalsView;
import bisq.desktop.main.dao.proposal.closed.ClosedProposalsView;
import bisq.desktop.main.dao.proposal.dashboard.ProposalDashboardView;
import bisq.desktop.main.dao.proposal.make.MakeProposalView;

import bisq.common.locale.Res;

import javax.inject.Inject;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.fxml.FXML;

import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;

@FxmlView
public class ProposalView extends ActivatableViewAndModel {

    private final ViewLoader viewLoader;
    private final Navigation navigation;

    private MenuItem dashboard, create, proposed, past;
    private Navigation.Listener listener;

    @FXML
    private VBox leftVBox;
    @FXML
    private AnchorPane content;

    private Class<? extends View> selectedViewClass;

    @Inject
    private ProposalView(CachingViewLoader viewLoader, Navigation navigation) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        listener = viewPath -> {
            if (viewPath.size() != 4 || viewPath.indexOf(ProposalView.class) != 2)
                return;

            selectedViewClass = viewPath.tip();
            loadView(selectedViewClass);
        };

        ToggleGroup toggleGroup = new ToggleGroup();
        dashboard = new MenuItem(navigation, toggleGroup, Res.get("shared.dashboard"), ProposalDashboardView.class, AwesomeIcon.DASHBOARD);
        create = new MenuItem(navigation, toggleGroup, Res.get("dao.proposal.menuItem.make"), MakeProposalView.class, AwesomeIcon.EDIT);
        proposed = new MenuItem(navigation, toggleGroup, Res.get("dao.proposal.menuItem.active"), ActiveProposalsView.class, AwesomeIcon.STACKEXCHANGE);
        past = new MenuItem(navigation, toggleGroup, Res.get("dao.proposal.menuItem.closed"), ClosedProposalsView.class, AwesomeIcon.LIST);
        leftVBox.getChildren().addAll(dashboard, create, proposed, past);
    }

    @Override
    protected void activate() {
        dashboard.activate();
        create.activate();
        proposed.activate();
        past.activate();

        navigation.addListener(listener);
        ViewPath viewPath = navigation.getCurrentPath();
        if (viewPath.size() == 3 && viewPath.indexOf(ProposalView.class) == 2 ||
                viewPath.size() == 2 && viewPath.indexOf(DaoView.class) == 1) {
            if (selectedViewClass == null)
                selectedViewClass = MakeProposalView.class;

            loadView(selectedViewClass);

        } else if (viewPath.size() == 4 && viewPath.indexOf(ProposalView.class) == 2) {
            selectedViewClass = viewPath.get(3);
            loadView(selectedViewClass);
        }
    }

    @Override
    protected void deactivate() {
        navigation.removeListener(listener);

        dashboard.deactivate();
        create.deactivate();
        proposed.deactivate();
        past.deactivate();
    }

    private void loadView(Class<? extends View> viewClass) {
        View view = viewLoader.load(viewClass);
        content.getChildren().setAll(view.getRoot());

        if (view instanceof ProposalDashboardView) dashboard.setSelected(true);
        else if (view instanceof MakeProposalView) create.setSelected(true);
        else if (view instanceof ActiveProposalsView) proposed.setSelected(true);
        else if (view instanceof ClosedProposalsView) past.setSelected(true);
    }

    public Class<? extends View> getSelectedViewClass() {
        return selectedViewClass;
    }
}


