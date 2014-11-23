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
import io.bitsquare.gui.ActivatableView;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.View;
import io.bitsquare.gui.ViewLoader;
import io.bitsquare.gui.main.account.arbitrator.profile.ArbitratorProfileView;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.msg.MessageService;
import io.bitsquare.msg.listeners.ArbitratorListener;
import io.bitsquare.persistence.Persistence;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

// TODO Arbitration is very basic yet
public class ArbitratorBrowserView extends ActivatableView implements ArbitratorListener {

    private final ViewLoader viewLoader;
    private final AccountSettings accountSettings;
    private final Persistence persistence;
    private final MessageService messageService;

    private final List<Arbitrator> allArbitrators = new ArrayList<>();

    private Arbitrator currentArbitrator;
    private ArbitratorProfileView arbitratorProfileView;
    private int index = -1;

    @FXML Button prevButton, nextButton, selectButton, closeButton;
    @FXML Pane arbitratorProfile;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ArbitratorBrowserView(ViewLoader viewLoader, AccountSettings accountSettings, Persistence persistence,
                                 MessageService messageService) {
        this.viewLoader = viewLoader;
        this.accountSettings = accountSettings;
        this.persistence = persistence;
        this.messageService = messageService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize() {
        messageService.addArbitratorListener(this);
        messageService.getArbitrators(LanguageUtil.getDefaultLanguageLocale());

        loadView(Navigation.Item.ARBITRATOR_PROFILE);
        checkButtonState();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

   /* public Initializable loadViewAndGetChildController(Navigation.Item item) {
        final ViewLoader loader = new ViewLoader(getClass().getResource(item.getFxmlUrl()));
        try {
            final Node view = loader.load();
            arbitratorProfileView = loader.getController();
            arbitratorProfileView.setParentController(this);
            ((Pane) root).getChildren().set(0, view);

            return arbitratorProfileView;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }*/


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected View loadView(Navigation.Item navigationItem) {
        ViewLoader.Item loaded = viewLoader.load(navigationItem.getFxmlUrl());
        ((Pane) root).getChildren().set(0, loaded.view);
        return arbitratorProfileView = (ArbitratorProfileView) loaded.controller;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: ArbitratorListener
    ///////////////////////////////////////////////////////////////////////////////////////////

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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void checkButtonState() {
        prevButton.setDisable(index < 1);
        nextButton.setDisable(index == allArbitrators.size() - 1 || index == -1);
    }
}

