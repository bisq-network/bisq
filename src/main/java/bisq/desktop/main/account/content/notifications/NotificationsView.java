/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.account.content.notifications;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.WebCamWindow;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.Layout;

import bisq.core.locale.Res;
import bisq.core.notifications.MobileMessage;
import bisq.core.notifications.MobileMessageType;
import bisq.core.notifications.MobileNotificationService;
import bisq.core.notifications.MobileNotificationValidator;
import bisq.core.user.Preferences;

import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import javax.inject.Inject;

import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import javafx.beans.value.ChangeListener;

import java.util.Random;

@FxmlView
public class NotificationsView extends ActivatableView<GridPane, Void> {
    private final Preferences preferences;
    private final MobileNotificationValidator mobileNotificationValidator;
    private final MobileNotificationService mobileNotificationService;

    private int gridRow = 0;
    private TextField tokenInputTextField;
    private CheckBox useSoundCheckBox, tradeCheckBox, marketCheckBox, priceCheckBox;
    private ChangeListener<String> tokenInputTextFieldListener;
    private Button webCamButton;
    private Button eraseButton;
    private Button testMsgButton;
    private ChangeListener<Boolean> useSoundCheckBoxListener, tradeCheckBoxListener;
    private WebCamWindow webCamWindow;
    private QrCodeReader qrCodeReader;
    private Label tokenInputLabel;
    private Button noWebCamButton;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private NotificationsView(Preferences preferences,
                              MobileNotificationValidator mobileNotificationValidator,
                              MobileNotificationService mobileNotificationService) {
        super();
        this.preferences = preferences;
        this.mobileNotificationValidator = mobileNotificationValidator;
        this.mobileNotificationService = mobileNotificationService;
    }

    @Override
    public void initialize() {
        FormBuilder.addTitledGroupBg(root, gridRow, 4, Res.get("account.notifications.setup.title"));
        Tuple3<Label, Button, Button> tuple = FormBuilder.addLabel2Buttons(root, gridRow, Res.get("account.notifications.webcam.label"),
                Res.get("account.notifications.webcam.button"), Res.get("account.notifications.noWebcam.button"), Layout.FIRST_ROW_DISTANCE);
        webCamButton = tuple.second;
        webCamButton.setDefaultButton(true);
        webCamButton.setOnAction((event) -> {
            webCamButton.setDisable(true);

            new WebCamLauncher(webCam -> {
                webCamWindow = new WebCamWindow(webCam.getViewSize().width, webCam.getViewSize().height)
                        .onClose(() -> {
                            webCamButton.setDisable(false);
                            qrCodeReader.close();
                        });
                webCamWindow.show();

                qrCodeReader = new QrCodeReader(webCam, webCamWindow.getImageView(), qrCode -> {
                    webCamWindow.hide();
                    webCamButton.setDisable(false);
                    reset();
                    tokenInputTextField.setText(qrCode);
                });
            });
        });

        noWebCamButton = tuple.third;
        noWebCamButton.setOnAction(e -> {
            setPairingTokenFieldsVisible();

            noWebCamButton.setManaged(false);
            noWebCamButton.setVisible(false);
        });

        Tuple2<Label, InputTextField> tuple2 = FormBuilder.addLabelInputTextField(root, ++gridRow, Res.get("account.notifications.email.label"));
        tokenInputLabel = tuple2.first;
        tokenInputTextField = tuple2.second;
        tokenInputTextField.setPromptText(Res.get("account.notifications.email.prompt"));
        tokenInputTextFieldListener = (observable, oldValue, newValue) -> {
            applyKeyAndToken(newValue);
        };
        tokenInputLabel.setManaged(false);
        tokenInputLabel.setVisible(false);
        tokenInputTextField.setManaged(false);
        tokenInputTextField.setVisible(false);

        testMsgButton = FormBuilder.addLabelButton(root, ++gridRow, Res.get("account.notifications.testMsg.label"), Res.get("account.notifications.testMsg.title")).second;
        testMsgButton.setDefaultButton(false);
        testMsgButton.setOnAction(event -> {
            MobileMessage message = new MobileMessage("Test notification",
                    "Test message " + new Random().nextInt(1000),
                    "Test txId",
                    MobileMessageType.TRADE);
            try {
                mobileNotificationService.sendMessage(message, useSoundCheckBox.isSelected());
            } catch (Exception e) {
                new Popup<>().error(e.toString()).show();
            }
        });

        eraseButton = FormBuilder.addLabelButton(root, ++gridRow, Res.get("account.notifications.erase.label"), Res.get("account.notifications.erase.title")).second;
        eraseButton.setId("notification-erase-button");
        eraseButton.setOnAction((event) -> {
            try {
                mobileNotificationService.sendWipeOutMessage();
                reset();
            } catch (Exception e) {
                new Popup<>().error(e.toString()).show();
            }
        });


        FormBuilder.addTitledGroupBg(root, ++gridRow, 2, Res.get("account.notifications.selection.title"),
                Layout.GROUP_DISTANCE);

        useSoundCheckBox = FormBuilder.addLabelCheckBox(root, gridRow, Res.get("account.notifications.useSound.label"),
                "",
                Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        useSoundCheckBox.setSelected(preferences.isUseSoundForMobileNotifications());
        useSoundCheckBoxListener = (observable, oldValue, newValue) -> {
            mobileNotificationService.getUseSoundProperty().set(newValue);
            preferences.setUseSoundForMobileNotifications(newValue);
        };

        tradeCheckBox = FormBuilder.addLabelCheckBox(root, ++gridRow, Res.get("account.notifications.trade.label")).second;
        tradeCheckBox.setSelected(preferences.isUseTradeNotifications());
        tradeCheckBoxListener = (observable, oldValue, newValue) -> {
            mobileNotificationService.getUseTradeNotificationsProperty().set(newValue);
            preferences.setUseTradeNotifications(newValue);
        };

       /* marketCheckBox = FormBuilder.addLabelCheckBox(root, ++gridRow, Res.get("account.notifications.market.label")).second;

        priceCheckBox = FormBuilder.addLabelCheckBox(root, ++gridRow, Res.get("account.notifications.price.label")).second;*/

        setDisableAtControls(!mobileNotificationService.isSetupConfirmationSent());

        if (preferences.getPhoneKeyAndToken() != null) {
            tokenInputTextField.setText(preferences.getPhoneKeyAndToken());
            setPairingTokenFieldsVisible();
        } else {
            eraseButton.setDisable(true);
            testMsgButton.setDisable(true);
        }
    }

    @Override
    protected void activate() {
        tokenInputTextField.textProperty().addListener(tokenInputTextFieldListener);
        useSoundCheckBox.selectedProperty().addListener(useSoundCheckBoxListener);
        tradeCheckBox.selectedProperty().addListener(tradeCheckBoxListener);
    }

    @Override
    protected void deactivate() {
        tokenInputTextField.textProperty().removeListener(tokenInputTextFieldListener);
        useSoundCheckBox.selectedProperty().removeListener(useSoundCheckBoxListener);
        tradeCheckBox.selectedProperty().removeListener(tradeCheckBoxListener);
    }

    private void applyKeyAndToken(String keyAndToken) {
        mobileNotificationService.applyKeyAndToken(keyAndToken);
        if (mobileNotificationValidator.isValid(keyAndToken)) {
            setDisableAtControls(false);
            setPairingTokenFieldsVisible();
        }
    }

    private void setDisableAtControls(boolean disable) {
        testMsgButton.setDisable(disable);
        eraseButton.setDisable(disable);
        tradeCheckBox.setDisable(disable);
        useSoundCheckBox.setDisable(disable);
        // not impl yet. so keep it inactive
       /* marketCheckBox.setDisable(true);
        priceCheckBox.setDisable(true);*/

    }

    private void setPairingTokenFieldsVisible() {
        tokenInputLabel.setManaged(true);
        tokenInputLabel.setVisible(true);
        tokenInputTextField.setManaged(true);
        tokenInputTextField.setVisible(true);
    }

    private void reset() {
        mobileNotificationService.reset();
        tokenInputTextField.clear();
        setDisableAtControls(true);
        eraseButton.setDisable(true);
        testMsgButton.setDisable(true);
    }
}

