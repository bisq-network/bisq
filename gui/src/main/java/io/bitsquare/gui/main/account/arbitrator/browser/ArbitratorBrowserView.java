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
import io.bitsquare.gui.main.account.arbitrator.profile.ArbitratorProfileView;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.arbitrator.ArbitratorMessageService;
import io.bitsquare.arbitrator.listeners.ArbitratorListener;
import io.bitsquare.persistence.Persistence;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import io.bitsquare.viewfx.view.FxmlView;
import io.bitsquare.viewfx.view.View;
import io.bitsquare.viewfx.view.ViewLoader;
import io.bitsquare.viewfx.view.ActivatableView;
import io.bitsquare.viewfx.view.CachingViewLoader;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

@FxmlView
public class ArbitratorBrowserView extends ActivatableView<Pane, Void> implements ArbitratorListener {

    @FXML Button prevButton, nextButton, selectButton, closeButton;
    @FXML Pane arbitratorProfile;

    private Arbitrator currentArbitrator;
    private ArbitratorProfileView arbitratorProfileView;
    private int index = -1;

    private final List<Arbitrator> allArbitrators = new ArrayList<>();

    private final ViewLoader viewLoader;
    private final AccountSettings accountSettings;
    private final Persistence persistence;
    private final ArbitratorMessageService messageService;

    @Inject
    public ArbitratorBrowserView(CachingViewLoader viewLoader, AccountSettings accountSettings, Persistence persistence,
                                 ArbitratorMessageService messageService) {
        this.viewLoader = viewLoader;
        this.accountSettings = accountSettings;
        this.persistence = persistence;
        this.messageService = messageService;
    }

    @Override
    public void initialize() {
        messageService.addArbitratorListener(this);
        messageService.getArbitrators(LanguageUtil.getDefaultLanguageLocale());

        View view = viewLoader.load(ArbitratorProfileView.class);
        root.getChildren().set(0, view.getRoot());
        arbitratorProfileView = (ArbitratorProfileView) view;

        checkButtonState();
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

