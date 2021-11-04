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

package bisq.desktop.main.overlays.editor;

import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.main.overlays.windows.SendPrivateNotificationWindow;

import bisq.core.alert.PrivateNotificationManager;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.trade.model.TradeModel;
import bisq.core.user.Preferences;

import bisq.common.UserThread;
import bisq.common.crypto.PubKeyRing;
import bisq.common.util.Tuple3;
import bisq.common.util.Utilities;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;

import javafx.stage.Modality;
import javafx.stage.Window;

import javafx.scene.Camera;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Rotate;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;

import javafx.beans.value.ChangeListener;

import javafx.event.EventHandler;

import javafx.collections.ObservableList;

import javafx.util.Duration;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextField;
import static bisq.desktop.util.FormBuilder.addInputTextField;

@Slf4j
public class PeerInfoWithTagEditor extends Overlay<PeerInfoWithTagEditor> {
    private final boolean useDevPrivilegeKeys;
    private InputTextField inputTextField;
    private Point2D position;
    private static PeerInfoWithTagEditor INSTANCE;
    private Consumer<String> saveHandler;
    private String hostName;
    private int numTrades;
    private ChangeListener<Boolean> focusListener;
    private final PrivateNotificationManager privateNotificationManager;
    @Nullable
    private final TradeModel tradeModel;
    private final Offer offer;
    private final Preferences preferences;
    private EventHandler<KeyEvent> keyEventEventHandler;
    @Nullable
    private String accountAge;
    private String accountAgeInfo;
    @Nullable
    private String accountSigningState;
    @Nullable
    private String signAge;
    @Nullable
    private String signAgeInfo;

    public PeerInfoWithTagEditor(PrivateNotificationManager privateNotificationManager,
                                 @Nullable TradeModel tradeModel,
                                 Offer offer,
                                 Preferences preferences,
                                 boolean useDevPrivilegeKeys) {
        this.privateNotificationManager = privateNotificationManager;
        this.tradeModel = tradeModel;
        this.offer = offer;
        this.preferences = preferences;
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
        width = 468;
        type = Type.Undefined;
        if (INSTANCE != null)
            INSTANCE.hide();
        INSTANCE = this;
    }

    public PeerInfoWithTagEditor onSave(Consumer<String> saveHandler) {
        this.saveHandler = saveHandler;
        return this;
    }

    public PeerInfoWithTagEditor position(Point2D position) {
        this.position = position;
        return this;
    }

    public PeerInfoWithTagEditor fullAddress(String hostName) {
        this.hostName = hostName;
        return this;
    }

    public PeerInfoWithTagEditor accountAge(@Nullable String accountAge) {
        this.accountAge = accountAge;
        return this;
    }

    public PeerInfoWithTagEditor accountAgeInfo(String accountAgeInfo) {
        this.accountAgeInfo = accountAgeInfo;
        return this;
    }

    public PeerInfoWithTagEditor signAge(@Nullable String signAge) {
        this.signAge = signAge;
        return this;
    }

    public PeerInfoWithTagEditor signAgeInfo(String signAgeInfo) {
        this.signAgeInfo = signAgeInfo;
        return this;
    }

    public PeerInfoWithTagEditor accountSigningState(@Nullable String accountSigningState) {
        this.accountSigningState = accountSigningState;
        return this;
    }

    public PeerInfoWithTagEditor numTrades(int numTrades) {
        this.numTrades = numTrades;
        if (numTrades == 0)
            width = 568;
        return this;
    }

    @Override
    public void show() {
        headLine(Res.get("peerInfo.title"));
        actionButtonText(Res.get("shared.save"));
        createGridPane();
        addHeadLine();
        addContent();
        addButtons();
        applyStyles();
        onShow();
    }

    @Override
    protected void onShow() {
        super.display();

        if (stage != null) {
            focusListener = (observable, oldValue, newValue) -> {
                if (!newValue)
                    hide();
            };
            stage.focusedProperty().addListener(focusListener);

            Scene scene = stage.getScene();
            if (scene != null)
                scene.addEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);
        }
    }

    @Override
    public void hide() {
        animateHide();
    }

    @Override
    protected void onHidden() {
        INSTANCE = null;

        if (stage != null) {
            if (focusListener != null)
                stage.focusedProperty().removeListener(focusListener);

            Scene scene = stage.getScene();
            if (scene != null)
                scene.removeEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);
        }
    }

    private void addContent() {
        gridPane.setPadding(new Insets(64));

        final Tuple3<Label, TextField, VBox> onionTuple = addCompactTopLabelTextField(gridPane, ++rowIndex, Res.get("shared.onionAddress"), hostName);
        GridPane.setColumnSpan(onionTuple.third, 2);
        onionTuple.second.setMouseTransparent(false);

        GridPane.setColumnSpan(addCompactTopLabelTextField(gridPane, ++rowIndex,
                Res.get("peerInfo.nrOfTrades"),
                numTrades > 0 ? String.valueOf(numTrades) : Res.get("peerInfo.notTradedYet")).third, 2);

        if (accountAge != null) {
            GridPane.setColumnSpan(addCompactTopLabelTextField(gridPane, ++rowIndex, accountAgeInfo, accountAge).third, 2);
        }

        if (accountSigningState != null) {
            GridPane.setColumnSpan(addCompactTopLabelTextField(gridPane, ++rowIndex, Res.get("shared.accountSigningState"), accountSigningState).third, 2);
        }

        if (signAge != null) {
            GridPane.setColumnSpan(addCompactTopLabelTextField(gridPane, ++rowIndex, signAgeInfo, signAge).third, 2);
        }


        inputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("peerInfo.setTag"));
        GridPane.setColumnSpan(inputTextField, 2);
        Map<String, String> peerTagMap = preferences.getPeerTagMap();
        String tag = peerTagMap.getOrDefault(hostName, "");
        inputTextField.setText(tag);

        keyEventEventHandler = event -> {
            if (Utilities.isAltOrCtrlPressed(KeyCode.R, event)) {
                // We need to close first our current popup and the open delayed the new one,
                // otherwise the text input handler does not work.
                doClose();
                UserThread.runAfter(() -> {
                    PubKeyRing peersPubKeyRing = null;
                    if (tradeModel != null) {
                        peersPubKeyRing = tradeModel.getTradeProtocolModel().getTradePeer().getPubKeyRing();
                    } else if (offer != null) {
                        peersPubKeyRing = offer.getPubKeyRing();
                    }
                    if (peersPubKeyRing != null) {
                        new SendPrivateNotificationWindow(
                                privateNotificationManager,
                                peersPubKeyRing,
                                offer.getMakerNodeAddress(),
                                useDevPrivilegeKeys
                        ).show();
                    }
                }, 100, TimeUnit.MILLISECONDS);
            }
        };
    }

    @Override
    protected void addHeadLine() {
        super.addHeadLine();
        GridPane.setHalignment(headLineLabel, HPos.CENTER);
    }

    protected void setupKeyHandler(Scene scene) {
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                e.consume();
                doClose();
            }
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                save();
            }
        });
    }

    @Override
    protected void animateHide(Runnable onFinishedHandler) {
        if (GlobalSettings.getUseAnimations()) {
            double duration = getDuration(300);
            Interpolator interpolator = Interpolator.SPLINE(0.25, 0.1, 0.25, 1);

            gridPane.setRotationAxis(Rotate.X_AXIS);
            Camera camera = gridPane.getScene().getCamera();
            gridPane.getScene().setCamera(new PerspectiveCamera());

            Timeline timeline = new Timeline();
            ObservableList<KeyFrame> keyFrames = timeline.getKeyFrames();
            keyFrames.add(new KeyFrame(Duration.millis(0),
                    new KeyValue(gridPane.rotateProperty(), 0, interpolator),
                    new KeyValue(gridPane.opacityProperty(), 1, interpolator)
            ));
            keyFrames.add(new KeyFrame(Duration.millis(duration),
                    new KeyValue(gridPane.rotateProperty(), -90, interpolator),
                    new KeyValue(gridPane.opacityProperty(), 0, interpolator)
            ));
            timeline.setOnFinished(event -> {
                gridPane.setRotate(0);
                gridPane.setRotationAxis(Rotate.Z_AXIS);
                gridPane.getScene().setCamera(camera);
                onFinishedHandler.run();
            });
            timeline.play();
        } else {
            onFinishedHandler.run();
        }
    }

    @Override
    protected void animateDisplay() {
        if (GlobalSettings.getUseAnimations()) {
            double startY = -160;
            double duration = getDuration(400);
            Interpolator interpolator = Interpolator.SPLINE(0.25, 0.1, 0.25, 1);
            Timeline timeline = new Timeline();
            ObservableList<KeyFrame> keyFrames = timeline.getKeyFrames();
            keyFrames.add(new KeyFrame(Duration.millis(0),
                    new KeyValue(gridPane.opacityProperty(), 0, interpolator),
                    new KeyValue(gridPane.translateYProperty(), startY, interpolator)
            ));

            keyFrames.add(new KeyFrame(Duration.millis(duration),
                    new KeyValue(gridPane.opacityProperty(), 1, interpolator),
                    new KeyValue(gridPane.translateYProperty(), 0, interpolator)
            ));

            timeline.play();
        }
    }

    @Override
    protected void createGridPane() {
        super.createGridPane();
        gridPane.setPadding(new Insets(15, 15, 30, 30));
    }

    @Override
    protected void addButtons() {
        buttonDistance = 10;
        super.addButtons();

        actionButton.setOnAction(event -> save());
    }

    private void save() {
        hide();
        if (saveHandler != null && inputTextField != null)
            saveHandler.accept(inputTextField.getText());
    }

    @Override
    protected void applyStyles() {
        gridPane.getStyleClass().add("peer-info-popup-bg");
        if (headLineLabel != null)
            headLineLabel.getStyleClass().add("peer-info-popup-headline");
    }

    @Override
    protected void setModality() {
        stage.initOwner(owner.getScene().getWindow());
        stage.initModality(Modality.NONE);
    }

    @Override
    protected void layout() {
        Window window = owner.getScene().getWindow();
        stage.setX(Math.round(window.getX() + position.getX() - width));
        stage.setY(Math.round(window.getY() + position.getY()));
    }

    @Override
    protected void addEffectToBackground() {
    }

    @Override
    protected void removeEffectFromBackground() {
    }
}
