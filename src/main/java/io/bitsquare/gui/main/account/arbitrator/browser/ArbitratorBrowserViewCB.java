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

import io.bitsquare.arbitrator.Arbitrator;
import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.ViewCB;
import io.bitsquare.gui.main.account.arbitrator.profile.ArbitratorProfileViewCB;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.ArbitratorListener;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.settings.Settings;
import io.bitsquare.util.ViewLoader;

import java.io.IOException;

import java.net.URL;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO Arbitration is very basic yet
public class ArbitratorBrowserViewCB extends CachedViewCB implements ArbitratorListener {
    private static final Logger log = LoggerFactory.getLogger(ArbitratorBrowserViewCB.class);

    private final Settings settings;
    private final Persistence persistence;
    private MessageFacade messageFacade;

    private final List<Arbitrator> allArbitrators = new ArrayList<>();
    private Arbitrator currentArbitrator;
    private ArbitratorProfileViewCB arbitratorProfileViewCB;
    private int index = -1;

    @FXML Button prevButton, nextButton, selectButton, closeButton;
    @FXML Pane arbitratorProfile;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ArbitratorBrowserViewCB(Settings settings, Persistence persistence, MessageFacade messageFacade) {
        this.settings = settings;
        this.persistence = persistence;
        this.messageFacade = messageFacade;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        messageFacade.addArbitratorListener(this);
        messageFacade.getArbitrators(LanguageUtil.getDefaultLanguageLocale());

        loadView(Navigation.Item.ARBITRATOR_PROFILE);
        checkButtonState();

        super.initialize(url, rb);

    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void activate() {
        super.activate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

   /* public Initializable loadViewAndGetChildController(Navigation.Item item) {
        final ViewLoader loader = new ViewLoader(getClass().getResource(item.getFxmlUrl()));
        try {
            final Node view = loader.load();
            arbitratorProfileViewCB = loader.getController();
            arbitratorProfileViewCB.setParentController(this);
            ((Pane) root).getChildren().set(0, view);

            return arbitratorProfileViewCB;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }*/


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected Initializable loadView(Navigation.Item navigationItem) {
        super.loadView(navigationItem);

        final ViewLoader loader = new ViewLoader(getClass().getResource(navigationItem.getFxmlUrl()));
        try {
            Node view = loader.load();
            ((Pane) root).getChildren().set(0, view);
            Initializable childController = arbitratorProfileViewCB = loader.getController();
            ((ViewCB) childController).setParent(this);

        } catch (IOException e) {
            log.error("Loading view failed. FxmlUrl = " + navigationItem.getFxmlUrl());
            e.printStackTrace();
        }
        return childController;
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
            arbitratorProfileViewCB.applyArbitrator(currentArbitrator);
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
            arbitratorProfileViewCB.applyArbitrator(currentArbitrator);
        }
        checkButtonState();
    }

    @FXML
    public void onNext() {
        if (index < allArbitrators.size() - 1) {
            index++;
            currentArbitrator = allArbitrators.get(index);
            arbitratorProfileViewCB.applyArbitrator(currentArbitrator);
        }
        checkButtonState();
    }

    @FXML
    public void onSelect() {
        settings.addAcceptedArbitrator(currentArbitrator);
        persistence.write(settings);
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

