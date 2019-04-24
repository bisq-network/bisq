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

package bisq.desktop.main.overlays;

import bisq.desktop.app.BisqApp;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.main.MainView;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Transitions;

import bisq.core.app.BisqEnvironment;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.user.DontShowAgainLookup;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.util.Utilities;

import org.apache.commons.lang3.StringUtils;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;

import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.transform.Rotate;

import javafx.geometry.HPos;
import javafx.geometry.Insets;

import javafx.beans.value.ChangeListener;

import javafx.collections.ObservableList;

import javafx.util.Duration;

import java.nio.file.Paths;

import java.io.File;
import java.io.IOException;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.addCheckBox;

@Slf4j
public abstract class Overlay<T extends Overlay> {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    private enum AnimationType {
        FadeInAtCenter,
        SlideDownFromCenterTop,
        SlideFromRightTop,
        ScaleDownToCenter,
        ScaleFromCenter,
        ScaleYFromCenter
    }

    private enum ChangeBackgroundType {
        BlurLight,
        BlurUltraLight,
        Darken
    }

    protected enum Type {
        Undefined(AnimationType.ScaleFromCenter, ChangeBackgroundType.BlurLight),

        Notification(AnimationType.SlideFromRightTop, ChangeBackgroundType.BlurLight),

        BackgroundInfo(AnimationType.SlideDownFromCenterTop, ChangeBackgroundType.BlurUltraLight),
        Feedback(AnimationType.SlideDownFromCenterTop, ChangeBackgroundType.Darken),

        Information(AnimationType.FadeInAtCenter, ChangeBackgroundType.BlurLight),
        Instruction(AnimationType.ScaleFromCenter, ChangeBackgroundType.BlurLight),
        Attention(AnimationType.ScaleFromCenter, ChangeBackgroundType.BlurLight),
        Confirmation(AnimationType.ScaleYFromCenter, ChangeBackgroundType.BlurLight),

        Warning(AnimationType.ScaleDownToCenter, ChangeBackgroundType.BlurLight),
        Error(AnimationType.ScaleDownToCenter, ChangeBackgroundType.BlurLight);

        public final AnimationType animationType;
        public final ChangeBackgroundType changeBackgroundType;

        Type(AnimationType animationType, ChangeBackgroundType changeBackgroundType) {
            this.animationType = animationType;
            this.changeBackgroundType = changeBackgroundType;
        }
    }

    protected final static double DEFAULT_WIDTH = 668;

    protected Stage stage;
    protected GridPane gridPane;
    protected Pane owner;

    protected int rowIndex = -1;
    protected double width = DEFAULT_WIDTH;
    protected double buttonDistance = 20;

    protected boolean showReportErrorButtons;
    private boolean showBusyAnimation;
    protected boolean hideCloseButton;
    protected boolean isDisplayed;
    protected boolean useAnimation = true;

    protected Label headlineIcon, headLineLabel, messageLabel;
    protected String headLine, message, closeButtonText, actionButtonText,
            secondaryActionButtonText, dontShowAgainId, dontShowAgainText,
            truncatedMessage;
    private String headlineStyle;
    protected Button actionButton, secondaryActionButton;
    protected AutoTooltipButton closeButton;

    protected Optional<Runnable> closeHandlerOptional = Optional.<Runnable>empty();
    protected Optional<Runnable> actionHandlerOptional = Optional.<Runnable>empty();
    protected Optional<Runnable> secondaryActionHandlerOptional = Optional.<Runnable>empty();
    protected ChangeListener<Number> positionListener;

    protected Timer centerTime;
    protected Type type = Type.Undefined;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Overlay() {
    }

    public void show() {
        if (dontShowAgainId == null || DontShowAgainLookup.showAgain(dontShowAgainId)) {
            createGridPane();
            addHeadLine();

            if (showBusyAnimation)
                addBusyAnimation();

            addMessage();
            if (showReportErrorButtons)
                addReportErrorButtons();

            addButtons();
            addDontShowAgainCheckBox();
            applyStyles();
            onShow();
        }
    }

    protected void onShow() {
    }

    public void hide() {
        if (gridPane != null) {
            animateHide();
        }
        isDisplayed = false;
    }

    protected void animateHide() {
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

    protected void onHidden() {
    }

    protected void cleanup() {
        if (centerTime != null)
            centerTime.stop();

        if (owner == null)
            owner = MainView.getRootContainer();
        Scene rootScene = owner.getScene();
        if (rootScene != null) {
            Window window = rootScene.getWindow();
            if (window != null && positionListener != null) {
                window.xProperty().removeListener(positionListener);
                window.yProperty().removeListener(positionListener);
                window.widthProperty().removeListener(positionListener);
            }
        }
    }

    public T onClose(Runnable closeHandler) {
        this.closeHandlerOptional = Optional.of(closeHandler);
        //noinspection unchecked
        return (T) this;
    }

    public T onAction(Runnable actionHandler) {
        this.actionHandlerOptional = Optional.of(actionHandler);
        //noinspection unchecked
        return (T) this;
    }

    public T onSecondaryAction(Runnable secondaryActionHandlerOptional) {
        this.secondaryActionHandlerOptional = Optional.of(secondaryActionHandlerOptional);
        //noinspection unchecked
        return (T) this;
    }

    public T headLine(String headLine) {
        this.headLine = headLine;
        //noinspection unchecked
        return (T) this;
    }

    public T notification(String message) {
        type = Type.Notification;
        if (headLine == null)
            this.headLine = Res.get("popup.headline.notification");
        this.message = message;
        setTruncatedMessage();
        //noinspection unchecked
        return (T) this;
    }

    public T instruction(String message) {
        type = Type.Instruction;
        if (headLine == null)
            this.headLine = Res.get("popup.headline.instruction");
        this.message = message;
        setTruncatedMessage();
        //noinspection unchecked
        return (T) this;
    }

    public T attention(String message) {
        type = Type.Attention;
        if (headLine == null)
            this.headLine = Res.get("popup.headline.attention");
        this.message = message;
        setTruncatedMessage();
        //noinspection unchecked
        return (T) this;
    }

    public T backgroundInfo(String message) {
        type = Type.BackgroundInfo;
        if (headLine == null)
            this.headLine = Res.get("popup.headline.backgroundInfo");
        this.message = message;
        setTruncatedMessage();
        //noinspection unchecked
        return (T) this;
    }

    public T feedback(String message) {
        type = Type.Feedback;
        if (headLine == null)
            this.headLine = Res.get("popup.headline.feedback");
        this.message = message;
        setTruncatedMessage();
        //noinspection unchecked
        return (T) this;
    }

    public T confirmation(String message) {
        type = Type.Confirmation;
        if (headLine == null)
            this.headLine = Res.get("popup.headline.confirmation");
        this.message = message;
        setTruncatedMessage();
        //noinspection unchecked
        return (T) this;
    }

    public T information(String message) {
        type = Type.Information;
        if (headLine == null)
            this.headLine = Res.get("popup.headline.information");
        this.message = message;
        setTruncatedMessage();
        //noinspection unchecked
        return (T) this;
    }

    public T warning(String message) {
        type = Type.Warning;

        if (headLine == null)
            this.headLine = Res.get("popup.headline.warning");
        this.message = message;
        setTruncatedMessage();
        //noinspection unchecked
        return (T) this;
    }

    public T error(String message) {
        type = Type.Error;
        showReportErrorButtons();
        width = 1100;
        if (headLine == null)
            this.headLine = Res.get("popup.headline.error");
        this.message = message;
        setTruncatedMessage();
        //noinspection unchecked
        return (T) this;
    }

    @SuppressWarnings("UnusedReturnValue")
    public T showReportErrorButtons() {
        this.showReportErrorButtons = true;
        //noinspection unchecked
        return (T) this;
    }

    public T message(String message) {
        this.message = message;
        setTruncatedMessage();
        //noinspection unchecked
        return (T) this;
    }

    public T closeButtonText(String closeButtonText) {
        this.closeButtonText = closeButtonText;
        //noinspection unchecked
        return (T) this;
    }

    public T useReportBugButton() {
        this.closeButtonText = Res.get("shared.reportBug");
        this.closeHandlerOptional = Optional.of(() -> GUIUtil.openWebPage("https://bisq.network/source/bisq/issues"));
        //noinspection unchecked
        return (T) this;
    }

    public T useIUnderstandButton() {
        this.closeButtonText = Res.get("shared.iUnderstand");
        //noinspection unchecked
        return (T) this;
    }

    public T actionButtonTextWithGoTo(String target) {
        this.actionButtonText = Res.get("shared.goTo", Res.get(target));
        //noinspection unchecked
        return (T) this;
    }

    public T secondaryActionButtonTextWithGoTo(String target) {
        this.secondaryActionButtonText = Res.get("shared.goTo", Res.get(target));
        //noinspection unchecked
        return (T) this;
    }

    public T closeButtonTextWithGoTo(String target) {
        this.closeButtonText = Res.get("shared.goTo", Res.get(target));
        //noinspection unchecked
        return (T) this;
    }

    public T actionButtonText(String actionButtonText) {
        this.actionButtonText = actionButtonText;
        //noinspection unchecked
        return (T) this;
    }

    public T secondaryActionButtonText(String secondaryActionButtonText) {
        this.secondaryActionButtonText = secondaryActionButtonText;
        //noinspection unchecked
        return (T) this;
    }

    public T useShutDownButton() {
        this.actionButtonText = Res.get("shared.shutDown");
        this.actionHandlerOptional = Optional.of(BisqApp.getShutDownHandler());
        //noinspection unchecked
        return (T) this;
    }

    public T width(double width) {
        this.width = width;
        //noinspection unchecked
        return (T) this;
    }

    public T showBusyAnimation() {
        this.showBusyAnimation = true;
        //noinspection unchecked
        return (T) this;
    }

    public T dontShowAgainId(String key) {
        this.dontShowAgainId = key;
        //noinspection unchecked
        return (T) this;
    }

    public T dontShowAgainText(String dontShowAgainText) {
        this.dontShowAgainText = dontShowAgainText;
        //noinspection unchecked
        return (T) this;
    }

    public T hideCloseButton() {
        this.hideCloseButton = true;
        //noinspection unchecked
        return (T) this;
    }

    public T useAnimation(boolean useAnimation) {
        this.useAnimation = useAnimation;
        //noinspection unchecked
        return (T) this;
    }

    public T setHeadlineStyle(String headlineStyle) {
        this.headlineStyle = headlineStyle;
        //noinspection unchecked
        return (T) this;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void createGridPane() {
        gridPane = new GridPane();
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        gridPane.setPadding(new Insets(64, 64, 64, 64));
        gridPane.setPrefWidth(width);

        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHalignment(HPos.RIGHT);
        columnConstraints1.setHgrow(Priority.SOMETIMES);
        ColumnConstraints columnConstraints2 = new ColumnConstraints();
        columnConstraints2.setHgrow(Priority.ALWAYS);
        gridPane.getColumnConstraints().addAll(columnConstraints1, columnConstraints2);
    }

    protected void blurAgain() {
        UserThread.runAfter(MainView::blurLight, Transitions.DEFAULT_DURATION, TimeUnit.MILLISECONDS);
    }

    public void display() {
        if (owner == null)
            owner = MainView.getRootContainer();

        if (owner != null) {
            Scene rootScene = owner.getScene();
            if (rootScene != null) {
                Scene scene = new Scene(gridPane);
                scene.getStylesheets().setAll(rootScene.getStylesheets());
                scene.setFill(Color.TRANSPARENT);

                setupKeyHandler(scene);

                stage = new Stage();
                stage.setScene(scene);
                Window window = rootScene.getWindow();
                setModality();
                stage.initStyle(StageStyle.TRANSPARENT);
                stage.setOnCloseRequest(event -> {
                    event.consume();
                    doClose();
                });
                stage.sizeToScene();
                stage.show();

                layout();

                addEffectToBackground();

                // On Linux the owner stage does not move the child stage as it does on Mac
                // So we need to apply centerPopup. Further with fast movements the handler loses
                // the latest position, with a delay it fixes that.
                // Also on Mac sometimes the popups are positioned outside of the main app, so keep it for all OS
                positionListener = (observable, oldValue, newValue) -> {
                    if (stage != null) {
                        layout();
                        if (centerTime != null)
                            centerTime.stop();

                        centerTime = UserThread.runAfter(this::layout, 3);
                    }
                };
                window.xProperty().addListener(positionListener);
                window.yProperty().addListener(positionListener);
                window.widthProperty().addListener(positionListener);

                animateDisplay();
                isDisplayed = true;
            }
        }
    }

    protected void setupKeyHandler(Scene scene) {
        if (!hideCloseButton) {
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE || e.getCode() == KeyCode.ENTER) {
                    e.consume();
                    doClose();
                }
            });
        }
    }

    protected void animateDisplay() {
        gridPane.setOpacity(0);
        Interpolator interpolator = Interpolator.SPLINE(0.25, 0.1, 0.25, 1);
        double duration = getDuration(400);
        Timeline timeline = new Timeline();
        ObservableList<KeyFrame> keyFrames = timeline.getKeyFrames();

        if (type.animationType == AnimationType.SlideDownFromCenterTop) {
            double startY = -gridPane.getHeight();
            keyFrames.add(new KeyFrame(Duration.millis(0),
                    new KeyValue(gridPane.opacityProperty(), 0, interpolator),
                    new KeyValue(gridPane.translateYProperty(), startY, interpolator)
            ));
            keyFrames.add(new KeyFrame(Duration.millis(duration),
                    new KeyValue(gridPane.opacityProperty(), 1, interpolator),
                    new KeyValue(gridPane.translateYProperty(), -50, interpolator)
            ));
        } else if (type.animationType == AnimationType.ScaleFromCenter) {
            double startScale = 0.25;
            keyFrames.add(new KeyFrame(Duration.millis(0),
                    new KeyValue(gridPane.opacityProperty(), 0, interpolator),
                    new KeyValue(gridPane.scaleXProperty(), startScale, interpolator),
                    new KeyValue(gridPane.scaleYProperty(), startScale, interpolator)

            ));
            keyFrames.add(new KeyFrame(Duration.millis(duration),
                    new KeyValue(gridPane.opacityProperty(), 1, interpolator),
                    new KeyValue(gridPane.scaleXProperty(), 1, interpolator),
                    new KeyValue(gridPane.scaleYProperty(), 1, interpolator)
            ));
        } else if (type.animationType == AnimationType.ScaleYFromCenter) {
            double startYScale = 0.25;
            keyFrames.add(new KeyFrame(Duration.millis(0),
                    new KeyValue(gridPane.opacityProperty(), 0, interpolator),
                    new KeyValue(gridPane.scaleYProperty(), startYScale, interpolator)

            ));
            keyFrames.add(new KeyFrame(Duration.millis(duration),
                    new KeyValue(gridPane.opacityProperty(), 1, interpolator),
                    new KeyValue(gridPane.scaleYProperty(), 1, interpolator)
            ));
        } else if (type.animationType == AnimationType.ScaleDownToCenter) {
            double startScale = 1.1;
            keyFrames.add(new KeyFrame(Duration.millis(0),
                    new KeyValue(gridPane.opacityProperty(), 0, interpolator),
                    new KeyValue(gridPane.scaleXProperty(), startScale, interpolator),
                    new KeyValue(gridPane.scaleYProperty(), startScale, interpolator)

            ));
            keyFrames.add(new KeyFrame(Duration.millis(duration),
                    new KeyValue(gridPane.opacityProperty(), 1, interpolator),
                    new KeyValue(gridPane.scaleXProperty(), 1, interpolator),
                    new KeyValue(gridPane.scaleYProperty(), 1, interpolator)
            ));
        } else if (type.animationType == AnimationType.FadeInAtCenter) {
            keyFrames.add(new KeyFrame(Duration.millis(0),
                    new KeyValue(gridPane.opacityProperty(), 0, interpolator)

            ));
            keyFrames.add(new KeyFrame(Duration.millis(duration),
                    new KeyValue(gridPane.opacityProperty(), 1, interpolator)
            ));
        }

        timeline.play();
    }

    protected void animateHide(Runnable onFinishedHandler) {
        Interpolator interpolator = Interpolator.SPLINE(0.25, 0.1, 0.25, 1);
        double duration = getDuration(200);
        Timeline timeline = new Timeline();
        ObservableList<KeyFrame> keyFrames = timeline.getKeyFrames();

        if (type.animationType == AnimationType.SlideDownFromCenterTop) {
            double endY = -gridPane.getHeight();
            keyFrames.add(new KeyFrame(Duration.millis(0),
                    new KeyValue(gridPane.opacityProperty(), 1, interpolator),
                    new KeyValue(gridPane.translateYProperty(), -10, interpolator)
            ));
            keyFrames.add(new KeyFrame(Duration.millis(duration),
                    new KeyValue(gridPane.opacityProperty(), 0, interpolator),
                    new KeyValue(gridPane.translateYProperty(), endY, interpolator)
            ));

            timeline.setOnFinished(e -> onFinishedHandler.run());
            timeline.play();
        } else if (type.animationType == AnimationType.ScaleFromCenter) {
            double endScale = 0.25;
            keyFrames.add(new KeyFrame(Duration.millis(0),
                    new KeyValue(gridPane.opacityProperty(), 1, interpolator),
                    new KeyValue(gridPane.scaleXProperty(), 1, interpolator),
                    new KeyValue(gridPane.scaleYProperty(), 1, interpolator)
            ));
            keyFrames.add(new KeyFrame(Duration.millis(duration),
                    new KeyValue(gridPane.opacityProperty(), 0, interpolator),
                    new KeyValue(gridPane.scaleXProperty(), endScale, interpolator),
                    new KeyValue(gridPane.scaleYProperty(), endScale, interpolator)
            ));
        } else if (type.animationType == AnimationType.ScaleYFromCenter) {
            gridPane.setRotationAxis(Rotate.X_AXIS);
            gridPane.getScene().setCamera(new PerspectiveCamera());
            keyFrames.add(new KeyFrame(Duration.millis(0),
                    new KeyValue(gridPane.rotateProperty(), 0, interpolator),
                    new KeyValue(gridPane.opacityProperty(), 1, interpolator)
            ));
            keyFrames.add(new KeyFrame(Duration.millis(duration),
                    new KeyValue(gridPane.rotateProperty(), -90, interpolator),
                    new KeyValue(gridPane.opacityProperty(), 0, interpolator)
            ));
        } else if (type.animationType == AnimationType.ScaleDownToCenter) {
            double endScale = 0.1;
            keyFrames.add(new KeyFrame(Duration.millis(0),
                    new KeyValue(gridPane.opacityProperty(), 1, interpolator),
                    new KeyValue(gridPane.scaleXProperty(), 1, interpolator),
                    new KeyValue(gridPane.scaleYProperty(), 1, interpolator)
            ));
            keyFrames.add(new KeyFrame(Duration.millis(duration),
                    new KeyValue(gridPane.opacityProperty(), 0, interpolator),
                    new KeyValue(gridPane.scaleXProperty(), endScale, interpolator),
                    new KeyValue(gridPane.scaleYProperty(), endScale, interpolator)
            ));
        } else if (type.animationType == AnimationType.FadeInAtCenter) {
            keyFrames.add(new KeyFrame(Duration.millis(0),
                    new KeyValue(gridPane.opacityProperty(), 1, interpolator)
            ));
            keyFrames.add(new KeyFrame(Duration.millis(duration),
                    new KeyValue(gridPane.opacityProperty(), 0, interpolator)
            ));
        }

        timeline.setOnFinished(e -> onFinishedHandler.run());
        timeline.play();
    }

    protected void layout() {
        if (owner == null)
            owner = MainView.getRootContainer();
        Scene rootScene = owner.getScene();
        if (rootScene != null) {
            Window window = rootScene.getWindow();
            double titleBarHeight = window.getHeight() - rootScene.getHeight();
            if (Utilities.isWindows())
                titleBarHeight -= 9;
            stage.setX(Math.round(window.getX() + (owner.getWidth() - stage.getWidth()) / 2));

            if (type.animationType == AnimationType.SlideDownFromCenterTop)
                stage.setY(Math.round(window.getY() + titleBarHeight));
            else
                stage.setY(Math.round(window.getY() + titleBarHeight + (owner.getHeight() - stage.getHeight()) / 2));
        }
    }

    protected void addEffectToBackground() {
        if (type.changeBackgroundType == ChangeBackgroundType.BlurUltraLight)
            MainView.blurUltraLight();
        else if (type.changeBackgroundType == ChangeBackgroundType.BlurLight)
            MainView.blurLight();
        else
            MainView.darken();
    }


    protected void applyStyles() {
        if (type.animationType == AnimationType.SlideDownFromCenterTop) {
            gridPane.getStyleClass().add("popup-bg-top");
        } else {
            gridPane.getStyleClass().add("popup-bg");
        }


        if (headLineLabel != null) {

            switch (type) {
                case Information:
                case BackgroundInfo:
                case Instruction:
                case Confirmation:
                case Feedback:
                case Notification:
                case Attention:
                    headLineLabel.getStyleClass().add("popup-headline-information");
                    headlineIcon.getStyleClass().add("popup-icon-information");
                    headlineIcon.setManaged(true);
                    headlineIcon.setVisible(true);
                    FormBuilder.getIconForLabel(AwesomeIcon.INFO_SIGN, headlineIcon, "1.5em");
                    break;
                case Warning:
                case Error:
                    headLineLabel.getStyleClass().add("popup-headline-warning");
                    headlineIcon.getStyleClass().add("popup-icon-warning");
                    headlineIcon.setManaged(true);
                    headlineIcon.setVisible(true);
                    FormBuilder.getIconForLabel(AwesomeIcon.EXCLAMATION_SIGN, headlineIcon, "1.5em");
                    break;
                default:
                    headLineLabel.getStyleClass().add("popup-headline");
            }
        }
    }

    protected void setModality() {
        stage.initOwner(owner.getScene().getWindow());
        stage.initModality(Modality.WINDOW_MODAL);
    }

    protected void removeEffectFromBackground() {
        MainView.removeEffect();
    }

    protected void addHeadLine() {
        if (headLine != null) {
            ++rowIndex;

            HBox hBox = new HBox();
            hBox.setSpacing(7);
            headLineLabel = new AutoTooltipLabel(headLine);
            headlineIcon = new Label();
            headlineIcon.setManaged(false);
            headlineIcon.setVisible(false);
            headlineIcon.setPadding(new Insets(3));
            headLineLabel.setMouseTransparent(true);

            if (headlineStyle != null)
                headLineLabel.setStyle(headlineStyle);

            hBox.getChildren().addAll(headlineIcon, headLineLabel);

            GridPane.setHalignment(hBox, HPos.LEFT);
            GridPane.setRowIndex(hBox, rowIndex);
            GridPane.setColumnSpan(hBox, 2);
            gridPane.getChildren().addAll(hBox);
        }
    }

    protected void addMessage() {
        if (message != null) {
            messageLabel = new AutoTooltipLabel(truncatedMessage);
            messageLabel.setMouseTransparent(true);
            messageLabel.setWrapText(true);
            GridPane.setHalignment(messageLabel, HPos.LEFT);
            GridPane.setHgrow(messageLabel, Priority.ALWAYS);
            GridPane.setMargin(messageLabel, new Insets(3, 0, 0, 0));
            GridPane.setRowIndex(messageLabel, ++rowIndex);
            GridPane.setColumnIndex(messageLabel, 0);
            GridPane.setColumnSpan(messageLabel, 2);
            gridPane.getChildren().add(messageLabel);
        }
    }

    private void addReportErrorButtons() {
        messageLabel.setText(Res.get("popup.reportError", truncatedMessage));

        Button logButton = new AutoTooltipButton(Res.get("popup.reportError.log"));
        GridPane.setMargin(logButton, new Insets(20, 0, 0, 0));
        GridPane.setHalignment(logButton, HPos.LEFT);
        GridPane.setRowIndex(logButton, ++rowIndex);
        gridPane.getChildren().add(logButton);
        logButton.setOnAction(event -> {
            try {
                File dataDir = new File(BisqEnvironment.getStaticAppDataDir());
                File logFile = new File(Paths.get(dataDir.getPath(), "bisq.log").toString());
                Utilities.openFile(logFile);
            } catch (IOException e) {
                e.printStackTrace();
                log.error(e.getMessage());
            }
        });

        Button gitHubButton = new AutoTooltipButton(Res.get("popup.reportError.gitHub"));
        GridPane.setHalignment(gitHubButton, HPos.RIGHT);
        GridPane.setRowIndex(gitHubButton, ++rowIndex);
        gridPane.getChildren().add(gitHubButton);
        gitHubButton.setOnAction(event -> {
            if (message != null)
                Utilities.copyToClipboard(message);
            GUIUtil.openWebPage("https://bisq.network/source/bisq/issues");
            hide();
        });
    }

    protected void addBusyAnimation() {
        BusyAnimation busyAnimation = new BusyAnimation();
        GridPane.setHalignment(busyAnimation, HPos.CENTER);
        GridPane.setRowIndex(busyAnimation, ++rowIndex);
        GridPane.setColumnSpan(busyAnimation, 2);
        gridPane.getChildren().add(busyAnimation);
    }

    protected void addDontShowAgainCheckBox() {
        if (dontShowAgainId != null) {
            // We might have set it and overridden the default, so we check if it is not set
            if (dontShowAgainText == null)
                dontShowAgainText = Res.get("popup.doNotShowAgain");

            CheckBox dontShowAgainCheckBox = addCheckBox(gridPane, rowIndex, dontShowAgainText, buttonDistance - 1);
            GridPane.setColumnIndex(dontShowAgainCheckBox, 0);
            GridPane.setHalignment(dontShowAgainCheckBox, HPos.LEFT);
            dontShowAgainCheckBox.setOnAction(e -> DontShowAgainLookup.dontShowAgain(dontShowAgainId, dontShowAgainCheckBox.isSelected()));
        }
    }

    protected void addButtons() {
        if (!hideCloseButton) {
            closeButton = new AutoTooltipButton(closeButtonText == null ? Res.get("shared.close") : closeButtonText);
            closeButton.getStyleClass().add("compact-button");
            closeButton.setOnAction(event -> doClose());
        }
        if (actionHandlerOptional.isPresent() || actionButtonText != null) {
            actionButton = new AutoTooltipButton(actionButtonText == null ? Res.get("shared.ok") : actionButtonText);
            actionButton.setDefaultButton(true);
            actionButton.getStyleClass().add("action-button");
            //TODO app wide focus
            //actionButton.requestFocus();
            actionButton.setOnAction(event -> {
                hide();
                actionHandlerOptional.ifPresent(Runnable::run);
            });

            Pane spacer = new Pane();
            HBox hBox = new HBox();
            hBox.setSpacing(10);

            hBox.getChildren().addAll(spacer, actionButton);

            if (secondaryActionButtonText != null && secondaryActionHandlerOptional.isPresent()) {
                secondaryActionButton = new AutoTooltipButton(secondaryActionButtonText);
                secondaryActionButton.setOnAction(event -> {
                    hide();
                    secondaryActionHandlerOptional.ifPresent(Runnable::run);
                });

                hBox.getChildren().add(secondaryActionButton);
            }

            if (!hideCloseButton)
                hBox.getChildren().add(closeButton);

            HBox.setHgrow(spacer, Priority.ALWAYS);
            spacer.setMaxWidth(Double.MAX_VALUE);

            GridPane.setHalignment(hBox, HPos.RIGHT);
            GridPane.setRowIndex(hBox, ++rowIndex);
            GridPane.setColumnSpan(hBox, 2);
            GridPane.setMargin(hBox, new Insets(buttonDistance, 0, 0, 0));
            gridPane.getChildren().add(hBox);
        } else if (!hideCloseButton) {
            closeButton.setDefaultButton(true);
            GridPane.setHalignment(closeButton, HPos.RIGHT);
            GridPane.setColumnSpan(closeButton, 2);
            if (!showReportErrorButtons)
                GridPane.setMargin(closeButton, new Insets(buttonDistance, 0, 0, 0));
            GridPane.setRowIndex(closeButton, ++rowIndex);
            gridPane.getChildren().add(closeButton);
        }
    }

    protected void doClose() {
        hide();
        closeHandlerOptional.ifPresent(Runnable::run);
    }

    protected void setTruncatedMessage() {
        if (message != null && message.length() > 1800)
            truncatedMessage = StringUtils.abbreviate(message, 1800);
        else truncatedMessage = Objects.requireNonNullElse(message, "");
    }

    protected double getDuration(double duration) {
        return useAnimation && GlobalSettings.getUseAnimations() ? duration : 1;
    }

    @Override
    public String toString() {
        return "Popup{" +
                "headLine='" + headLine + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
