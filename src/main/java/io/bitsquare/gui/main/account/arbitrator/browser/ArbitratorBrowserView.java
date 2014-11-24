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

import io.bitsquare.account.AccountSettings;
import io.bitsquare.arbitrator.Arbitrator;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.main.account.arbitrator.profile.ArbitratorProfileView;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.msg.MessageService;
import io.bitsquare.msg.listeners.ArbitratorListener;
import io.bitsquare.persistence.Persistence;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import viewfx.view.View;
import viewfx.view.support.ViewLoader;
import viewfx.view.support.ActivatableView;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

// TODO Arbitration is very basic yet
class ArbitratorBrowserView extends ActivatableView<Pane, Void> implements ArbitratorListener {

    @FXML Button prevButton, nextButton, selectButton, closeButton;
    @FXML Pane arbitratorProfile;

    private Arbitrator currentArbitrator;
    private ArbitratorProfileView arbitratorProfileView;
    private int index = -1;

    private final List<Arbitrator> allArbitrators = new ArrayList<>();

    private final ViewLoader viewLoader;
    private final AccountSettings accountSettings;
    private final Persistence persistence;
    private final MessageService messageService;

    @Inject
    public ArbitratorBrowserView(ViewLoader viewLoader, AccountSettings accountSettings, Persistence persistence,
                                 MessageService messageService) {
        this.viewLoader = viewLoader;
        this.accountSettings = accountSettings;
        this.persistence = persistence;
        this.messageService = messageService;
    }

    @Override
    public void initialize() {
        messageService.addArbitratorListener(this);
        messageService.getArbitrators(LanguageUtil.getDefaultLanguageLocale());

        loadView(Navigation.Item.ARBITRATOR_PROFILE);
        checkButtonState();
    }

   /* public Initializable loadViewAndGetChildController(Navigation.Item item) {
        final ViewLoader loader = new ViewLoader(getClass().getResource(item.getFxmlUrl()));
        try {
            final Node view = loader.load();
            arbitratorProfileView = loader.getController();
            arbitratorProfileView.setParentController(this);
            root.getChildren().set(0, view);

            return arbitratorProfileView;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }*/


    private void loadView(Navigation.Item navigationItem) {
        View view = viewLoader.load(navigationItem.getFxmlUrl());
        root.getChildren().set(0, view.getRoot());
        arbitratorProfileView = (ArbitratorProfileView) view;
    }

    @Override
    public void onArbitratorAdded(Arbitrator arbitrator) {
    }

    @Override
    public void onArbitratorsReceived(List<Arbitrator> arbitrators) {
        allArbitrators.clear();
        allArbitrators.addAll(arbitrators);

        if (!allArbitrators.isEmpty()) {
            index = 0;
            currentArbitrator = allArbitrators.get(index);
            arbitratorProfileView.applyArbitrator(currentArbitrator);
            checkButtonState();
        }
    }

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
        persistence.write(accountSettings);
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

