package io.bitsquare.gui.main.overlays.editor;

import io.bitsquare.alert.PrivateNotificationManager;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.main.overlays.Overlay;
import io.bitsquare.gui.main.overlays.windows.SendPrivateNotificationWindow;
import io.bitsquare.gui.util.FormBuilder;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.user.Preferences;
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
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.transform.Rotate;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Consumer;

public class PeerInfoWithTagEditor extends Overlay<PeerInfoWithTagEditor> {
    private static final Logger log = LoggerFactory.getLogger(PeerInfoWithTagEditor.class);
    private InputTextField inputTextField;
    private Point2D position;

    private static PeerInfoWithTagEditor INSTANCE;
    private Consumer<String> saveHandler;
    private String hostName;
    private int numTrades;
    private ChangeListener<Boolean> focusListener;
    private PrivateNotificationManager privateNotificationManager;
    private Offer offer;
    private EventHandler<KeyEvent> keyEventEventHandler;


    public PeerInfoWithTagEditor(PrivateNotificationManager privateNotificationManager, Offer offer) {
        this.privateNotificationManager = privateNotificationManager;
        this.offer = offer;
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

    public PeerInfoWithTagEditor numTrades(int numTrades) {
        this.numTrades = numTrades;
        return this;
    }

    @Override
    public void show() {
        headLine("Peer info");
        actionButtonText("Save");
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

    protected void addContent() {
        FormBuilder.addLabelTextField(gridPane, ++rowIndex, "Onion address:", hostName);
        FormBuilder.addLabelTextField(gridPane, ++rowIndex, "Number of completed trades:", String.valueOf(numTrades));
        inputTextField = FormBuilder.addLabelInputTextField(gridPane, ++rowIndex, "Set tag for that peer:").second;
        Map<String, String> peerTagMap = Preferences.INSTANCE.getPeerTagMap();
        String tag = peerTagMap.containsKey(hostName) ? peerTagMap.get(hostName) : "";
        inputTextField.setText(tag);

        keyEventEventHandler = event -> {
            if (new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN).match(event)) {
                new SendPrivateNotificationWindow(offer)
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
        if (Preferences.INSTANCE.getUseAnimations()) {
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
        if (Preferences.INSTANCE.getUseAnimations()) {
            double startY = -160;
            double duration = getDuration(400);
            Interpolator interpolator = Interpolator.SPLINE(0.25, 0.1, 0.25, 1);
            double height = gridPane.getPrefHeight();
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
