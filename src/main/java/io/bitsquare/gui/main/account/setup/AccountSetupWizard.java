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
import io.bitsquare.gui.main.account.content.irc.IrcAccountView;
import io.bitsquare.gui.main.account.content.password.PasswordView;
import io.bitsquare.gui.main.account.content.registration.RegistrationView;
import io.bitsquare.gui.main.account.content.restrictions.RestrictionsView;
import io.bitsquare.gui.main.account.content.seedwords.SeedWordsView;

import javax.inject.Inject;

import viewfx.view.View;
import viewfx.view.ViewLoader;
import viewfx.view.Wizard;
import viewfx.view.support.ActivatableView;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;

class AccountSetupWizard extends ActivatableView implements Wizard {

    @FXML VBox leftVBox;
    @FXML AnchorPane content;

    private WizardItem seedWords, password, fiatAccount, restrictions, registration;
    private Navigation.Listener listener;

    private final ViewLoader viewLoader;
    private final Navigation navigation;

    @Inject
    private AccountSetupWizard(ViewLoader viewLoader, Navigation navigation) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        listener = navigationItems -> {
            if (navigationItems != null &&
                    navigationItems.length == 4 &&
                    navigationItems[2] == Navigation.Item.ACCOUNT_SETUP) {

                switch (navigationItems[3]) {
                    case SEED_WORDS:
                        seedWords.show();
                        break;
                    case ADD_PASSWORD:
                        seedWords.onCompleted();
                        password.show();
                        break;
                    case RESTRICTIONS:
                        seedWords.onCompleted();
                        password.onCompleted();
                        restrictions.show();
                        break;
                    case FIAT_ACCOUNT:
                        seedWords.onCompleted();
                        password.onCompleted();
                        restrictions.onCompleted();
                        fiatAccount.show();
                        break;
                    case REGISTRATION:
                        seedWords.onCompleted();
                        password.onCompleted();
                        restrictions.onCompleted();
                        fiatAccount.onCompleted();
                        registration.show();
                        break;
                }
            }
        };

        seedWords = new WizardItem(this, "Backup wallet seed", "Write down the seed word for your wallet",
                Navigation.Item.SEED_WORDS);
        password = new WizardItem(this, "Setup password", "Protect your wallet with a password",
                Navigation.Item.ADD_PASSWORD);
        restrictions = new WizardItem(this, "Select arbitrators",
                "Select which arbitrators you want to use for trading",
                Navigation.Item.RESTRICTIONS);
        fiatAccount = new WizardItem(this, " Setup Payments account(s)",
                "You need to setup at least one payment account",
                Navigation.Item.FIAT_ACCOUNT);
        registration = new WizardItem(this, "Register your account",
                "The registration in the Blockchain requires a payment of 0.0002 BTC",
                Navigation.Item.REGISTRATION);

        leftVBox.getChildren().addAll(seedWords, password, restrictions, fiatAccount, registration);

        seedWords.setDisable(true);
        password.setDisable(true);
        restrictions.setDisable(true);
        registration.setDisable(true);
    }

    @Override
    public void activate() {
        navigation.addListener(listener);
        fiatAccount.show();
    }

    @Override
    public void deactivate() {
        navigation.removeListener(listener);
    }

    @Override
    public void nextStep(Step currentStep) {
        if (currentStep instanceof SeedWordsView) {
            seedWords.onCompleted();
            password.show();
        }
        else if (currentStep instanceof PasswordView) {
            password.onCompleted();
            restrictions.show();
        }
        else if (currentStep instanceof RestrictionsView) {
            restrictions.onCompleted();
            fiatAccount.show();
        }
        else if (currentStep instanceof IrcAccountView) {
            fiatAccount.onCompleted();
            registration.show();
        }
        else if (currentStep instanceof RegistrationView) {
            registration.onCompleted();

            if (navigation.getItemsForReturning() != null)
                navigation.navigationTo(navigation.getItemsForReturning());
            else
                navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.BUY);
        }
    }

    protected void loadView(Navigation.Item navigationItem) {
        View view = viewLoader.load(navigationItem.getFxmlUrl());
        content.getChildren().setAll(view.getRoot());
        if (view instanceof Wizard.Step)
            ((Step) view).setParent(this);
    }
}


class WizardItem extends HBox {

    private final ImageView imageView;
    private final Label titleLabel;
    private final Label subTitleLabel;
    private final AccountSetupWizard parent;
    private final Navigation.Item navigationItem;

    WizardItem(AccountSetupWizard parent, String title, String subTitle,
               Navigation.Item navigationItem) {
        this.parent = parent;
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

    void show() {
        parent.loadView(navigationItem);
       /* navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ACCOUNT, Navigation
                        .Item.ACCOUNT_SETUP,
                navigationItem);*/

        setId("wizard-item-background-active");
        imageView.setId("image-arrow-blue");
        titleLabel.setId("wizard-title-active");
        subTitleLabel.setId("wizard-sub-title-active");
    }

    void onCompleted() {
        setId("wizard-item-background-completed");
        imageView.setId("image-tick");
        titleLabel.setId("wizard-title-completed");
        subTitleLabel.setId("wizard-sub-title-completed");
    }
}

