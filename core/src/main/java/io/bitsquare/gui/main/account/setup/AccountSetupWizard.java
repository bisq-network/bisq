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

import io.bitsquare.common.viewfx.view.ActivatableView;
import io.bitsquare.common.viewfx.view.CachingViewLoader;
import io.bitsquare.common.viewfx.view.FxmlView;
import io.bitsquare.common.viewfx.view.View;
import io.bitsquare.common.viewfx.view.ViewLoader;
import io.bitsquare.common.viewfx.view.Wizard;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.account.content.fiat.FiatAccountView;
import io.bitsquare.gui.main.account.content.password.PasswordView;
import io.bitsquare.gui.main.account.content.registration.RegistrationView;
import io.bitsquare.gui.main.account.content.restrictions.RestrictionsView;
import io.bitsquare.gui.main.account.content.seedwords.SeedWordsView;
import io.bitsquare.gui.main.offer.BuyOfferView;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;

@FxmlView
public class AccountSetupWizard extends ActivatableView implements Wizard {

    @FXML VBox leftVBox;
    @FXML AnchorPane content;

    private WizardItem seedWords, password, fiatAccount, restrictions, registration;
    private Navigation.Listener listener;

    private final ViewLoader viewLoader;
    private final Navigation navigation;

    @Inject
    private AccountSetupWizard(CachingViewLoader viewLoader, Navigation navigation) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        listener = viewPath -> {
            if (viewPath.size() != 4 || !viewPath.contains(this.getClass()))
                return;

            Class<? extends View> viewClass = viewPath.tip();

            if (viewClass == SeedWordsView.class) {
                seedWords.show();
            }
            else if (viewClass == PasswordView.class) {
                seedWords.onCompleted();
                password.show();
            }
            else if (viewClass == RestrictionsView.class) {
                seedWords.onCompleted();
                password.onCompleted();
                restrictions.show();
            }
            else if (viewClass == FiatAccountView.class) {
                seedWords.onCompleted();
                password.onCompleted();
                restrictions.onCompleted();
                fiatAccount.show();
            }
            else if (viewClass == RegistrationView.class) {
                seedWords.onCompleted();
                password.onCompleted();
                restrictions.onCompleted();
                fiatAccount.onCompleted();
                registration.show();
            }
        };

        seedWords = new WizardItem(SeedWordsView.class,
                "Backup wallet seed", "Write down the seed word for your wallet");
        password = new WizardItem(PasswordView.class,
                "Setup password", "Protect your wallet with a password");
        restrictions = new WizardItem(RestrictionsView.class,
                "Select arbitrators", "Select which arbitrators you want to use for trading");
        fiatAccount = new WizardItem(FiatAccountView.class,
                " Setup Payments account(s)", "You need to setup at least one payment account");
        registration = new WizardItem(RegistrationView.class,
                "Register your account", "The registration in the Blockchain requires a payment of 0.0002 BTC");

        leftVBox.getChildren().addAll(seedWords, password, restrictions, fiatAccount, registration);
    }

    @Override
    public void activate() {
        navigation.addListener(listener);
        seedWords.show();
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
        else if (currentStep instanceof FiatAccountView) {
            fiatAccount.onCompleted();
            registration.show();
        }
        else if (currentStep instanceof RegistrationView) {
            registration.onCompleted();

            if (navigation.getReturnPath() != null)
                navigation.navigateTo(navigation.getReturnPath());
            else
                navigation.navigateTo(MainView.class, BuyOfferView.class);
        }
    }

    protected void loadView(Class<? extends View> viewClass) {
        View view = viewLoader.load(viewClass);
        content.getChildren().setAll(view.getRoot());
        if (view instanceof Wizard.Step)
            ((Step) view).setWizard(this);
    }


    private class WizardItem extends HBox {

        private final ImageView imageView;
        private final Label titleLabel;
        private final Label subTitleLabel;
        private final Class<? extends View> viewClass;

        WizardItem(Class<? extends View> viewClass, String title, String subTitle) {
            this.viewClass = viewClass;

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
            loadView(viewClass);

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
}

