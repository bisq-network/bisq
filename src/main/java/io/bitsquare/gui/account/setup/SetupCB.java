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

package io.bitsquare.gui.account.setup;

import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.CachedCodeBehind;
import io.bitsquare.gui.CodeBehind;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.PresentationModel;
import io.bitsquare.gui.account.fiataccount.FiatAccountCB;
import io.bitsquare.gui.account.password.PasswordCB;
import io.bitsquare.gui.account.registration.RegistrationCB;
import io.bitsquare.gui.account.restrictions.RestrictionsCB;
import io.bitsquare.gui.account.seedwords.SeedWordsCB;
import io.bitsquare.gui.util.ImageUtil;

import java.io.IOException;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.geometry.Insets;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetupCB extends CachedCodeBehind<SetupPM> {

    private static final Logger log = LoggerFactory.getLogger(SetupCB.class);
    public VBox leftVBox;
    public AnchorPane content;
    private WizardItem seedWords, password, fiatAccount, restrictions, registration;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public SetupCB(SetupPM presentationModel) {
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
                NavigationItem.PASSWORD);
        restrictions = new WizardItem(this, content, "Setup your preferences",
                "Define your preferences with whom you want to trade",
                NavigationItem.RESTRICTIONS);
        fiatAccount = new WizardItem(this, content, " Setup Payments account(s)",
                "You need to add a payments account to your trading account",
                NavigationItem.FIAT_ACCOUNT);
        registration = new WizardItem(this, root, "Register your account",
                "Pay in the registration fee of 0.0002 BTC and store your account in the BTC block chain",
                NavigationItem.REGISTRATION);

        leftVBox.getChildren().addAll(seedWords, password, restrictions, fiatAccount, registration);

        childController = seedWords.show(NavigationItem.SEED_WORDS);
    }

    @Override
    public void activate() {
        super.activate();
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }

    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////


    public void onCompleted(CodeBehind<? extends PresentationModel> childView) {
        if (childView instanceof SeedWordsCB) {
            seedWords.onCompleted();
            childController = password.show(NavigationItem.PASSWORD);
        }
        else if (childView instanceof PasswordCB) {
            password.onCompleted();
            childController = restrictions.show(NavigationItem.RESTRICTIONS);
        }
        else if (childView instanceof RestrictionsCB) {
            restrictions.onCompleted();
            childController = fiatAccount.show(NavigationItem.FIAT_ACCOUNT);
        }
        else if (childView instanceof FiatAccountCB) {
            fiatAccount.onCompleted();
            childController = registration.show(NavigationItem.REGISTRATION);
        }
        else if (childView instanceof RegistrationCB) {
            registration.onCompleted();
            childController = null;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////
}

class WizardItem extends HBox {

    private static final Logger log = LoggerFactory.getLogger(WizardItem.class);

    private final ImageView imageView;
    private final Label titleLabel;
    private final Label subTitleLabel;
    private SetupCB parentCB;
    private Parent content;


    private CodeBehind<? extends PresentationModel> childController;

    WizardItem(SetupCB parentCB, Parent content, String title, String subTitle, NavigationItem navigationItem) {
        this.parentCB = parentCB;
        this.content = content;
        setId("wizard-item-background-deactivated");
        layout();
        setSpacing(5);
        setPrefWidth(200);

        imageView = ImageUtil.getIconImageView(ImageUtil.ARROW_GREY);
        imageView.setFitHeight(15);
        imageView.setFitWidth(20);
        imageView.setPickOnBounds(true);
        imageView.setMouseTransparent(true);
        HBox.setMargin(imageView, new Insets(8, 0, 0, 8));

        final VBox vBox = new VBox();
        vBox.setSpacing(1);
        HBox.setMargin(vBox, new Insets(5, 0, 8, 0));
        vBox.setMouseTransparent(true);

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

        vBox.getChildren().addAll(titleLabel, subTitleLabel);
        getChildren().addAll(imageView, vBox);
    }

    public CodeBehind<? extends PresentationModel> show(NavigationItem navigationItem) {
        loadView(navigationItem);
        setId("wizard-item-background-active");
        imageView.setImage(ImageUtil.getIconImage(ImageUtil.ARROW_BLUE));
        titleLabel.setId("wizard-title-active");
        subTitleLabel.setId("wizard-sub-title-active");
        return childController;
    }

    public void onCompleted() {
        setId("wizard-item-background-completed");
        imageView.setImage(ImageUtil.getIconImage(ImageUtil.TICK));
        titleLabel.setId("wizard-title-completed");
        subTitleLabel.setId("wizard-sub-title-completed");
    }

    public CodeBehind<? extends PresentationModel> getChildController() {
        return childController;
    }

    public ImageView getImageView() {
        return imageView;
    }

    public Label getTitleLabel() {
        return titleLabel;
    }

    public Label getSubTitleLabel() {
        return subTitleLabel;
    }

    private void loadView(NavigationItem navigationItem) {
        final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource(navigationItem.getFxmlUrl()));
        try {
            final Pane view = loader.load();
            ((AnchorPane) content).getChildren().setAll(view);
            childController = loader.getController();
            childController.setParentController(parentCB);
        } catch (IOException e) {
            log.error("Loading view failed. FxmlUrl = " + navigationItem.getFxmlUrl());
            // log.error(e.getCause().toString());
            log.error(e.getMessage());
            log.error(e.getStackTrace().toString());
        }

    }
}

