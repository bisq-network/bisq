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

package io.bitsquare.gui.main.account.setup;

import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.PresentationModel;
import io.bitsquare.gui.ViewCB;
import io.bitsquare.gui.main.account.MultiStepNavigation;
import io.bitsquare.gui.main.account.content.ContextAware;
import io.bitsquare.gui.main.account.content.fiat.FiatAccountViewCB;
import io.bitsquare.gui.main.account.content.password.PasswordViewCB;
import io.bitsquare.gui.main.account.content.registration.RegistrationViewCB;
import io.bitsquare.gui.main.account.content.restrictions.RestrictionsViewCB;
import io.bitsquare.gui.main.account.content.seedwords.SeedWordsViewCB;
import io.bitsquare.util.ViewLoader;

import java.io.IOException;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This UI is not cached as it is normally only needed once.
 */
public class AccountSetupViewCB extends ViewCB implements MultiStepNavigation {

    private static final Logger log = LoggerFactory.getLogger(AccountSetupViewCB.class);

    private WizardItem seedWords, password, fiatAccount, restrictions, registration;
    private final Navigation navigation;
    private Navigation.Listener listener;

    @FXML VBox leftVBox;
    @FXML AnchorPane content;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private AccountSetupViewCB(Navigation navigation) {
        super();
        this.navigation = navigation;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        listener = navigationItems -> {
            if (navigationItems != null &&
                    navigationItems.length == 4 &&
                    navigationItems[2] == Navigation.Item.ACCOUNT_SETUP) {
                switch (navigationItems[3]) {
                    case SEED_WORDS:
                        childController = seedWords.show();
                        break;
                    case ADD_PASSWORD:
                        seedWords.onCompleted();
                        childController = password.show();
                        break;
                    case RESTRICTIONS:
                        seedWords.onCompleted();
                        password.onCompleted();
                        childController = restrictions.show();
                        break;
                    case FIAT_ACCOUNT:
                        seedWords.onCompleted();
                        password.onCompleted();
                        restrictions.onCompleted();
                        childController = fiatAccount.show();
                        break;
                    case REGISTRATION:
                        seedWords.onCompleted();
                        password.onCompleted();
                        restrictions.onCompleted();
                        fiatAccount.onCompleted();
                        childController = registration.show();
                        break;
                }
            }
        };

        seedWords = new WizardItem(this, "Backup wallet seed", "Write down the seed word for your wallet",
                Navigation.Item.SEED_WORDS);
        password = new WizardItem(this, "Setup password", "Protect your wallet with a password",
                Navigation.Item.ADD_PASSWORD);
        restrictions = new WizardItem(this, "Setup your preferences",
                "Define your preferences with whom you want to trade",
                Navigation.Item.RESTRICTIONS);
        fiatAccount = new WizardItem(this, " Setup Payments account(s)",
                "You need to setup at least one payment account",
                Navigation.Item.FIAT_ACCOUNT);
        registration = new WizardItem(this, "Register your account",
                "The registration in the Blockchain requires a payment of 0.0002 BTC",
                Navigation.Item.REGISTRATION);

        leftVBox.getChildren().addAll(seedWords, password, restrictions, fiatAccount, registration);

        super.initialize(url, rb);

        navigation.addListener(listener);
        childController = seedWords.show();
    }

    @Override
    public void terminate() {
        super.terminate();
        navigation.removeListener(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MultiStepNavigation implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void nextStep(ViewCB<? extends PresentationModel> childView) {
        if (childView instanceof SeedWordsViewCB) {
            seedWords.onCompleted();
            childController = password.show();
        }
        else if (childView instanceof PasswordViewCB) {
            password.onCompleted();
            childController = restrictions.show();
        }
        else if (childView instanceof RestrictionsViewCB) {
            restrictions.onCompleted();
            childController = fiatAccount.show();
        }
        else if (childView instanceof FiatAccountViewCB) {
            fiatAccount.onCompleted();
            childController = registration.show();
        }
        else if (childView instanceof RegistrationViewCB) {
            registration.onCompleted();
            childController = null;

            if (navigation.getItemsForReturning() != null)
                navigation.navigationTo(navigation.getItemsForReturning());
            else
                navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.BUY);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected Initializable loadView(Navigation.Item navigationItem) {
        final ViewLoader loader = new ViewLoader(getClass().getResource(navigationItem.getFxmlUrl()));
        try {
            final Pane view = loader.load();
            content.getChildren().setAll(view);
            childController = loader.getController();
            ((ViewCB<? extends PresentationModel>) childController).setParent(this);
            ((ContextAware) childController).useSettingsContext(false);
            return childController;
        } catch (IOException e) {
            log.error("Loading view failed. FxmlUrl = " + navigationItem.getFxmlUrl());
            e.printStackTrace();
        }
        return null;
    }
}

class WizardItem extends HBox {
    private static final Logger log = LoggerFactory.getLogger(WizardItem.class);

    private ViewCB<? extends PresentationModel> childController;

    private final ImageView imageView;
    private final Label titleLabel;
    private final Label subTitleLabel;
    private final AccountSetupViewCB host;
    private final Navigation.Item navigationItem;

    WizardItem(AccountSetupViewCB host, String title, String subTitle,
               Navigation.Item navigationItem) {
        this.host = host;
        this.navigationItem = navigationItem;

        setId("wizard-item-background-deactivated");
        setSpacing(5);
        setPrefWidth(200);

        imageView = new ImageView();
        imageView.setId("image-arrow-grey");
        imageView.setFitHeight(15);
        imageView.setFitWidth(20);
        imageView.setPickOnBounds(true);
        imageView.setMouseTransparent(true);
        HBox.setMargin(imageView, new Insets(8, 0, 0, 8));

        titleLabel = new Label(title);
        titleLabel.setId("wizard-title-deactivated");
        titleLabel.setLayoutX(7);
        titleLabel.setMouseTransparent(true);

        subTitleLabel = new Label(subTitle);
        subTitleLabel.setId("wizard-sub-title-deactivated");
        subTitleLabel.setLayoutX(40);
        subTitleLabel.setLayoutY(33);
        subTitleLabel.setMaxWidth(250);
        subTitleLabel.setWrapText(true);
        subTitleLabel.setMouseTransparent(true);

        final VBox vBox = new VBox();
        vBox.setSpacing(1);
        HBox.setMargin(vBox, new Insets(5, 0, 8, 0));
        vBox.setMouseTransparent(true);
        vBox.getChildren().addAll(titleLabel, subTitleLabel);

        getChildren().addAll(imageView, vBox);
    }

    ViewCB<? extends PresentationModel> show() {
        host.loadView(navigationItem);
       /* navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ACCOUNT, Navigation
                        .Item.ACCOUNT_SETUP,
                navigationItem);*/

        setId("wizard-item-background-active");
        imageView.setId("image-arrow-blue");
        titleLabel.setId("wizard-title-active");
        subTitleLabel.setId("wizard-sub-title-active");

        return childController;
    }

    void onCompleted() {
        setId("wizard-item-background-completed");
        imageView.setId("image-tick");
        titleLabel.setId("wizard-title-completed");
        subTitleLabel.setId("wizard-sub-title-completed");
    }
}

