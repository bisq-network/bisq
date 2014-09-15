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

import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.NavigationManager;
import io.bitsquare.gui.PresentationModel;
import io.bitsquare.gui.ViewCB;
import io.bitsquare.gui.main.account.MultiStepNavigation;
import io.bitsquare.gui.main.account.content.ContextAware;
import io.bitsquare.gui.main.account.content.fiat.FiatAccountViewCB;
import io.bitsquare.gui.main.account.content.password.PasswordViewCB;
import io.bitsquare.gui.main.account.content.registration.RegistrationViewCB;
import io.bitsquare.gui.main.account.content.restrictions.RestrictionsViewCB;
import io.bitsquare.gui.main.account.content.seedwords.SeedWordsViewCB;
import io.bitsquare.gui.util.ImageUtil;
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

public class AccountSetupViewCB extends CachedViewCB<AccountSetupPM> implements MultiStepNavigation {

    private static final Logger log = LoggerFactory.getLogger(AccountSetupViewCB.class);

    private WizardItem seedWords, password, fiatAccount, restrictions, registration;
    private NavigationManager navigationManager;
    private NavigationManager.Listener listener;

    @FXML VBox leftVBox;
    @FXML AnchorPane content;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private AccountSetupViewCB(AccountSetupPM presentationModel, NavigationManager navigationManager) {
        super(presentationModel);
        this.navigationManager = navigationManager;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        listener = navigationItems -> {
            if (navigationItems != null &&
                    navigationItems.length == 4 &&
                    navigationItems[2] == NavigationItem.ACCOUNT_SETUP) {
                loadView(navigationItems[3]);
            }
        };

        seedWords = new WizardItem(navigationManager, "Backup wallet seed", "Write down the seed word for your wallet",
                NavigationItem.SEED_WORDS);
        password = new WizardItem(navigationManager, "Setup password", "Protect your wallet with a password",
                NavigationItem.ADD_PASSWORD);
        restrictions = new WizardItem(navigationManager, "Setup your preferences",
                "Define your preferences with whom you want to trade",
                NavigationItem.RESTRICTIONS);
        fiatAccount = new WizardItem(navigationManager, " Setup Payments account(s)",
                "You need to add a payments account to your trading account",
                NavigationItem.FIAT_ACCOUNT);
        registration = new WizardItem(navigationManager, "Register your account",
                "Pay in the registration fee of 0.0002 BTC and store your account in the BTC block chain",
                NavigationItem.REGISTRATION);

        leftVBox.getChildren().addAll(seedWords, password, restrictions, fiatAccount, registration);

        super.initialize(url, rb);
    }


    @Override
    public void activate() {
        super.activate();

        navigationManager.addListener(listener);

        // triggers navigationTo
        childController = seedWords.show();
    }

    @Override
    public void deactivate() {
        super.deactivate();

        navigationManager.removeListener(listener);
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

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

            navigationManager.navigationTo(navigationManager.getNavigationItemsForReturning());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected Initializable loadView(NavigationItem navigationItem) {
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
            e.getStackTrace();
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
    private final NavigationItem navigationItem;
    private final NavigationManager navigationManager;

    WizardItem(NavigationManager navigationManager, String title, String subTitle,
               NavigationItem navigationItem) {
        this.navigationManager = navigationManager;
        this.navigationItem = navigationItem;

        setId("wizard-item-background-deactivated");
        setSpacing(5);
        setPrefWidth(200);

        imageView = ImageUtil.getIconImageView(ImageUtil.ARROW_GREY);
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
        navigationManager.navigationTo(NavigationItem.MAIN, NavigationItem.ACCOUNT, NavigationItem.ACCOUNT_SETUP,
                navigationItem);

        setId("wizard-item-background-active");
        imageView.setImage(ImageUtil.getIconImage(ImageUtil.ARROW_BLUE));
        titleLabel.setId("wizard-title-active");
        subTitleLabel.setId("wizard-sub-title-active");
        return childController;
    }

    void onCompleted() {
        setId("wizard-item-background-completed");
        imageView.setImage(ImageUtil.getIconImage(ImageUtil.TICK));
        titleLabel.setId("wizard-title-completed");
        subTitleLabel.setId("wizard-sub-title-completed");
    }
}

