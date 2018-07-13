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

import javax.inject.Inject;

import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
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
    private Button wipeOutButton;
    private Button devButton;
    private ChangeListener<Boolean> useSoundCheckBoxListener, tradeCheckBoxListener;
    private WebCamWindow webCamWindow;
    private QrCodeReader qrCodeReader;


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
        webCamButton = FormBuilder.addLabelButton(root, gridRow, Res.get("account.notifications.webcam.label"),
                Res.get("account.notifications.webcam.title"), Layout.FIRST_ROW_DISTANCE).second;
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
                    tokenInputTextField.setText(qrCode);
                });
            });
        });

        tokenInputTextField = FormBuilder.addLabelInputTextField(root, ++gridRow, Res.get("account.notifications.email.label")).second;
        tokenInputTextField.setPromptText(Res.get("account.notifications.email.prompt"));
        if (preferences.getPhoneKeyAndToken() != null)
            tokenInputTextField.setText(preferences.getPhoneKeyAndToken());
        tokenInputTextFieldListener = (observable, oldValue, newValue) -> {
            applyKeyAndToken(newValue);
        };

        Button resetButton = FormBuilder.addLabelButton(root, ++gridRow, Res.get("account.notifications.reset.label"), Res.get("account.notifications.reset.title")).second;
        resetButton.setOnAction((event) -> {
            mobileNotificationService.reset();
            tokenInputTextField.clear();
            setDisableAtControls(true);
        });

        wipeOutButton = FormBuilder.addLabelButton(root, ++gridRow, Res.get("account.notifications.wipeout.label"), Res.get("account.notifications.wipeout.title")).second;
        wipeOutButton.setOnAction((event) -> {
            try {
                mobileNotificationService.sendWipeOutMessage();
            } catch (Exception e) {
                new Popup<>().error(e.toString()).show();
            }
        });

        FormBuilder.addTitledGroupBg(root, ++gridRow, 5, Res.get("account.notifications.selection.title"),
                Layout.GROUP_DISTANCE);
        tradeCheckBox = FormBuilder.addLabelCheckBox(root, gridRow, Res.get("account.notifications.trade.label"),
                Res.get("account.notifications.checkbox.title"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        tradeCheckBox.setSelected(preferences.isUseTradeNotifications());
        tradeCheckBoxListener = (observable, oldValue, newValue) -> {
            mobileNotificationService.getUseTradeNotificationsProperty().set(newValue);
            preferences.setUseTradeNotifications(newValue);
        };
        marketCheckBox = FormBuilder.addLabelCheckBox(root, ++gridRow, Res.get("account.notifications.market.label"),
                Res.get("account.notifications.checkbox.title")).second;

        priceCheckBox = FormBuilder.addLabelCheckBox(root, ++gridRow, Res.get("account.notifications.price.label"),
                Res.get("account.notifications.checkbox.title")).second;

        useSoundCheckBox = FormBuilder.addLabelCheckBox(root, ++gridRow, Res.get("account.notifications.useSound.label"),
                Res.get("account.notifications.useSound.title")).second;
        useSoundCheckBox.setSelected(preferences.isUseSoundForMobileNotifications());
        useSoundCheckBoxListener = (observable, oldValue, newValue) -> {
            mobileNotificationService.getUseSoundProperty().set(newValue);
            preferences.setUseSoundForMobileNotifications(newValue);
        };

        //TODO remove later
        devButton = FormBuilder.addButton(root, ++gridRow, "Send test msg");
        devButton.setOnAction(event -> {
            MobileMessage message = new MobileMessage("test title",
                    "test msg " + new Random().nextInt(1000),
                    "test txid",
                    MobileMessageType.TRADE);
            try {
                mobileNotificationService.sendMessage(message, useSoundCheckBox.isSelected());
            } catch (Exception e) {
                new Popup<>().error(e.toString()).show();
            }
        });

        setDisableAtControls(!mobileNotificationService.isSetupConfirmationSent());
    }

    @Override
    protected void activate() {
        tokenInputTextField.textProperty().addListener(tokenInputTextFieldListener);
        tradeCheckBox.selectedProperty().addListener(tradeCheckBoxListener);
        useSoundCheckBox.selectedProperty().addListener(useSoundCheckBoxListener);
    }

    @Override
    protected void deactivate() {
        tokenInputTextField.textProperty().removeListener(tokenInputTextFieldListener);
        tradeCheckBox.selectedProperty().removeListener(tradeCheckBoxListener);
        useSoundCheckBox.selectedProperty().removeListener(useSoundCheckBoxListener);
    }

    private void applyKeyAndToken(String keyAndToken) {
        mobileNotificationService.applyKeyAndToken(keyAndToken);
        if (mobileNotificationValidator.isValid(keyAndToken))
            setDisableAtControls(false);
    }

    private void setDisableAtControls(boolean disable) {
        wipeOutButton.setDisable(disable);
        tradeCheckBox.setDisable(disable);
        useSoundCheckBox.setDisable(disable);
        // not impl yet. so keep it inactive
        marketCheckBox.setDisable(true);
        priceCheckBox.setDisable(true);
        devButton.setDisable(disable);
    }
}

