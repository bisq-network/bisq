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

package io.bitsquare.gui.main.account.arbitrator.browser;

import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.arbitration.ArbitratorService;
import io.bitsquare.common.viewfx.view.ActivatableView;
import io.bitsquare.common.viewfx.view.CachingViewLoader;
import io.bitsquare.common.viewfx.view.FxmlView;
import io.bitsquare.common.viewfx.view.View;
import io.bitsquare.common.viewfx.view.ViewLoader;
import io.bitsquare.gui.main.account.arbitrator.profile.ArbitratorProfileView;
import io.bitsquare.user.AccountSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

@FxmlView
public class BrowserView extends ActivatableView<Pane, Void> implements ArbitratorService.Listener {

    @FXML Button prevButton, nextButton, selectButton, closeButton;
    @FXML Pane arbitratorProfile;

    private Arbitrator currentArbitrator;
    private ArbitratorProfileView arbitratorProfileView;
    private int index = -1;

    private final List<Arbitrator> allArbitrators = new ArrayList<>();

    private final ViewLoader viewLoader;
    private final AccountSettings accountSettings;
    private final ArbitratorService arbitratorService;

    @Inject
    public BrowserView(CachingViewLoader viewLoader, AccountSettings accountSettings,
                       ArbitratorService arbitratorService) {
        this.viewLoader = viewLoader;
        this.accountSettings = accountSettings;
        this.arbitratorService = arbitratorService;
    }

    @Override
    public void initialize() {
        arbitratorService.addListener(this);
       /* arbitratorService.loadAllArbitrators(() -> log.debug("Arbitrators successful loaded " + arbitratorService.getAllArbitrators().size()),
                (errorMessage -> log.error(errorMessage)));*/

        View view = viewLoader.load(ArbitratorProfileView.class);
        root.getChildren().set(0, view.getRoot());
        arbitratorProfileView = (ArbitratorProfileView) view;

        checkButtonState();
    }

    @Override
    public void onArbitratorAdded(Arbitrator arbitrator) {
    }

    @Override
    public void onAllArbitratorsLoaded(Map<String, Arbitrator> arbitratorsMap) {

    }
/*
    @Override
    public void onAllArbitratorsLoaded(List<Arbitrator> arbitrators) {
        allArbitrators.clear();
        allArbitrators.addAll(arbitrators);

        if (!allArbitrators.isEmpty()) {
            index = 0;
            currentArbitrator = allArbitrators.get(index);
            arbitratorProfileView.applyArbitrator(currentArbitrator);
            checkButtonState();
        }
    }*/

    @Override
    public void onArbitratorRemoved(Arbitrator arbitrator) {
    }

    @FXML
    public void onPrevious() {
        if (index > 0) {
            index--;
            currentArbitrator = allArbitrators.get(index);
            arbitratorProfileView.applyArbitrator(currentArbitrator);
        }
        checkButtonState();
    }

    @FXML
    public void onNext() {
        if (index < allArbitrators.size() - 1) {
            index++;
            currentArbitrator = allArbitrators.get(index);
            arbitratorProfileView.applyArbitrator(currentArbitrator);
        }
        checkButtonState();
    }

    @FXML
    public void onSelect() {
        accountSettings.addAcceptedArbitrator(currentArbitrator);
    }

    @FXML
    public void onClose() {
        Stage stage = (Stage) root.getScene().getWindow();
        stage.close();
    }

    private void checkButtonState() {
        prevButton.setDisable(index < 1);
        nextButton.setDisable(index == allArbitrators.size() - 1 || index == -1);
    }
}

