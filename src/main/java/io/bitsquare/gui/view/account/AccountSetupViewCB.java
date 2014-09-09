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

package io.bitsquare.gui.view.account;

import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.pm.PresentationModel;
import io.bitsquare.gui.pm.account.AccountSetupPM;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.gui.view.CachedCodeBehind;
import io.bitsquare.gui.view.CodeBehind;
import io.bitsquare.gui.view.account.content.ContextAware;
import io.bitsquare.gui.view.account.content.FiatAccountViewCB;
import io.bitsquare.gui.view.account.content.PasswordViewCB;
import io.bitsquare.gui.view.account.content.RegistrationViewCB;
import io.bitsquare.gui.view.account.content.RestrictionsViewCB;
import io.bitsquare.gui.view.account.content.SeedWordsViewCB;
import io.bitsquare.util.BSFXMLLoader;

import java.io.IOException;

import java.net.URL;

import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountSetupViewCB extends CachedCodeBehind<AccountSetupPM> {

    private static final Logger log = LoggerFactory.getLogger(AccountSetupViewCB.class);

    private WizardItem seedWords, password, fiatAccount, restrictions, registration;
    private Callable<Void> requestCloseCallable;

    @FXML private VBox leftVBox;
    @FXML private AnchorPane content;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private AccountSetupViewCB(AccountSetupPM presentationModel) {
        super(presentationModel);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        seedWords = new WizardItem(this, content, "Backup wallet seed", "Write down the seed word for your wallet",
                NavigationItem.SEED_WORDS);
        password = new WizardItem(this, content, "Setup password", "Protect your wallet with a password",
                NavigationItem.ADD_PASSWORD);
        restrictions = new WizardItem(this, content, "Setup your preferences",
                "Define your preferences with whom you want to trade",
                NavigationItem.RESTRICTIONS);
        fiatAccount = new WizardItem(this, content, " Setup Payments account(s)",
                "You need to add a payments account to your trading account",
                NavigationItem.FIAT_ACCOUNT);
        registration = new WizardItem(this, content, "Register your account",
                "Pay in the registration fee of 0.0002 BTC and store your account in the BTC block chain",
                NavigationItem.REGISTRATION);

        leftVBox.getChildren().addAll(seedWords, password, restrictions, fiatAccount, registration);

        childController = seedWords.show();
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
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onCompleted(CodeBehind<? extends PresentationModel> childView) {
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

            if (requestCloseCallable != null) {
                try {
                    requestCloseCallable.call();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setRemoveCallBack(Callable<Void> requestCloseCallable) {

        this.requestCloseCallable = requestCloseCallable;
    }
}

class WizardItem extends HBox {
    private static final Logger log = LoggerFactory.getLogger(WizardItem.class);

    private CodeBehind<? extends PresentationModel> childController;

    private final ImageView imageView;
    private final Label titleLabel;
    private final Label subTitleLabel;
    private final AccountSetupViewCB parentCB;
    private final Parent content;
    private final NavigationItem navigationItem;

    WizardItem(AccountSetupViewCB parentCB, Parent content, String title, String subTitle,
               NavigationItem navigationItem) {
        this.parentCB = parentCB;
        this.content = content;
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

    CodeBehind<? extends PresentationModel> show() {
        loadView(navigationItem);
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

    private void loadView(NavigationItem navigationItem) {
        final BSFXMLLoader loader = new BSFXMLLoader(getClass().getResource(navigationItem.getFxmlUrl()));
        try {
            final Pane view = loader.load();
            ((AnchorPane) content).getChildren().setAll(view);
            childController = loader.getController();
            childController.setParentController(parentCB);
            ((ContextAware) childController).useSettingsContext(false);
        } catch (IOException e) {
            log.error("Loading view failed. FxmlUrl = " + navigationItem.getFxmlUrl());
            e.getStackTrace();
        }

    }
}

