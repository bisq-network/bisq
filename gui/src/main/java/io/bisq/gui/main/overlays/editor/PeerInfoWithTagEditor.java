package io.bisq.gui.main.overlays.editor;

import io.bisq.common.GlobalSettings;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Utilities;
import io.bisq.core.alert.PrivateNotificationManager;
import io.bisq.core.offer.Offer;
import io.bisq.core.user.Preferences;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.main.overlays.Overlay;
import io.bisq.gui.main.overlays.windows.SendPrivateNotificationWindow;
import io.bisq.gui.util.FormBuilder;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Camera;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.transform.Rotate;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
public class PeerInfoWithTagEditor extends Overlay<PeerInfoWithTagEditor> {
    private InputTextField inputTextField;
    private Point2D position;
    private static PeerInfoWithTagEditor INSTANCE;
    private Consumer<String> saveHandler;
    private String hostName;
    private int numTrades;
    private ChangeListener<Boolean> focusListener;
    private final PrivateNotificationManager privateNotificationManager;
    private final Offer offer;
    private final Preferences preferences;
    private EventHandler<KeyEvent> keyEventEventHandler;
    @Nullable
    private String accountAge;

    public PeerInfoWithTagEditor(PrivateNotificationManager privateNotificationManager, Offer offer, Preferences preferences) {
        this.privateNotificationManager = privateNotificationManager;
        this.offer = offer;
        this.preferences = preferences;
        width = 400;
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

    public PeerInfoWithTagEditor hostName(String hostName) {
        this.hostName = hostName;
        return this;
    }

    public PeerInfoWithTagEditor accountAge(@Nullable String accountAge) {
        this.accountAge = accountAge;
        return this;
    }

    public PeerInfoWithTagEditor numTrades(int numTrades) {
        this.numTrades = numTrades;
        if (numTrades == 0)
            width = 500;
        return this;
    }

    @Override
    public void show() {
        headLine(Res.get("peerInfo.title"));
        actionButtonText(Res.get("shared.save"));
        createGridPane();
        addHeadLine();
        addContent();
        addCloseButton();
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
        animateHide(() -> {
            removeEffectFromBackground();

            if (stage != null)
                stage.hide();
            else
                log.warn("Stage is null");

            cleanup();
            onHidden();
        });
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
        FormBuilder.addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.onionAddress"), hostName).second.setMouseTransparent(false);
        FormBuilder.addLabelTextField(gridPane, ++rowIndex,
            Res.get("peerInfo.nrOfTrades"),
            numTrades > 0 ? String.valueOf(numTrades) : Res.get("peerInfo.notTradedYet"));
        if (accountAge != null)
            FormBuilder.addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("peerInfo.age"), accountAge);

        inputTextField = FormBuilder.addLabelInputTextField(gridPane, ++rowIndex, Res.get("peerInfo.setTag")).second;
        Map<String, String> peerTagMap = preferences.getPeerTagMap();
        String tag = peerTagMap.containsKey(hostName) ? peerTagMap.get(hostName) : "";
        inputTextField.setText(tag);

        keyEventEventHandler = event -> {
            if (Utilities.isAltOrCtrlPressed(KeyCode.R, event)) {
                new SendPrivateNotificationWindow(offer.getPubKeyRing(), offer.getMakerNodeAddress())
                    .onAddAlertMessage(privateNotificationManager::sendPrivateNotificationMessageIfKeyIsValid)
                    .show();
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
    protected void addCloseButton() {
        buttonDistance = 10;
        super.addCloseButton();

        actionButton.setOnAction(event -> save());
    }

    private void save() {
        hide();
        if (saveHandler != null && inputTextField != null)
            saveHandler.accept(inputTextField.getText());
    }

    @Override
    protected void applyStyles() {
        gridPane.setId("peer-info-popup-bg");
        if (headLineLabel != null)
            headLineLabel.setId("peer-info-popup-headline");
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
