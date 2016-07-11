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

package io.bitsquare.gui.main.overlays;

import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.gui.components.BusyAnimation;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.util.Transitions;
import io.bitsquare.locale.BSResources;
import io.bitsquare.user.Preferences;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.transform.Rotate;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.bitsquare.gui.util.FormBuilder.addCheckBox;

public abstract class Overlay<T extends Overlay> {
    protected final Logger log = LoggerFactory.getLogger(this.getClass());


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

        public AnimationType animationType;
        public ChangeBackgroundType changeBackgroundType;

        Type(AnimationType animationType, ChangeBackgroundType changeBackgroundType) {
            this.animationType = animationType;
            this.changeBackgroundType = changeBackgroundType;
        }
    }

    protected final static double DEFAULT_WIDTH = 600;
    protected int rowIndex = -1;
    protected String headLine;
    protected String message;
    protected String closeButtonText;
    protected String actionButtonText;
    protected double width = DEFAULT_WIDTH;
    protected Pane owner;
    protected GridPane gridPane;
    protected Button closeButton;
    protected Optional<Runnable> closeHandlerOptional = Optional.empty();
    protected Optional<Runnable> actionHandlerOptional = Optional.empty();
    protected Stage stage;
    private boolean showReportErrorButtons;
    protected Label messageLabel;
    protected String truncatedMessage;
    private BusyAnimation busyAnimation;
    private boolean showBusyAnimation;
    protected Button actionButton;
    protected Label headLineLabel;
    protected String dontShowAgainId;
    protected String dontShowAgainText;
    private Preferences preferences;
    protected ChangeListener<Number> positionListener;
    protected Timer centerTime;
    protected double buttonDistance = 20;
    protected Type type = Type.Undefined;
    protected boolean hideCloseButton;
    protected boolean useAnimation = true;
    private String headlineStyle;
    

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Overlay() {
    }

    public void show() {
        if (dontShowAgainId == null || preferences == null || preferences.showAgain(dontShowAgainId)) {
            createGridPane();
            addHeadLine();
            addSeparator();

            if (showBusyAnimation)
                addBusyAnimation();

            addMessage();
            if (showReportErrorButtons)
                addReportErrorButtons();

            addCloseButton();
            addDontShowAgainCheckBox();
            applyStyles();
            onShow();
        }
    }

    protected void onShow() {
    }

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
        return (T) this;
    }

    public T onAction(Runnable actionHandler) {
        this.actionHandlerOptional = Optional.of(actionHandler);
        return (T) this;
    }

    public T headLine(String headLine) {
        this.headLine = headLine;
        return (T) this;
    }

    public T notification(String message) {
        type = Type.Notification;
        if (headLine == null)
            this.headLine = "Notification";
        this.message = message;
        setTruncatedMessage();
        return (T) this;
    }

    public T instruction(String message) {
        type = Type.Instruction;
        if (headLine == null)
            this.headLine = "Please note:";
        this.message = message;
        setTruncatedMessage();
        return (T) this;
    }

    public T attention(String message) {
        type = Type.Attention;
        if (headLine == null)
            this.headLine = "Attention";
        this.message = message;
        setTruncatedMessage();
        return (T) this;
    }

    public T backgroundInfo(String message) {
        type = Type.BackgroundInfo;
        if (headLine == null)
            this.headLine = "Background information";
        this.message = message;
        setTruncatedMessage();
        return (T) this;
    }

    public T feedback(String message) {
        type = Type.Feedback;
        if (headLine == null)
            this.headLine = "Completed";
        this.message = message;
        setTruncatedMessage();
        return (T) this;
    }

    public T confirmation(String message) {
        type = Type.Confirmation;
        if (headLine == null)
            this.headLine = "Confirmation";
        this.message = message;
        setTruncatedMessage();
        return (T) this;
    }

    public T information(String message) {
        type = Type.Information;
        if (headLine == null)
            this.headLine = "Information";
        this.message = message;
        setTruncatedMessage();
        return (T) this;
    }

    public T warning(String message) {
        type = Type.Warning;

        if (headLine == null)
            this.headLine = "Warning";
        this.message = message;
        setTruncatedMessage();
        return (T) this;
    }

    public T error(String message) {
        type = Type.Error;
        showReportErrorButtons();
        if (headLine == null)
            this.headLine = "Error";
        this.message = message;
        setTruncatedMessage();
        return (T) this;
    }

    public T showReportErrorButtons() {
        this.showReportErrorButtons = true;
        return (T) this;
    }

    public T message(String message) {
        this.message = message;
        setTruncatedMessage();
        return (T) this;
    }

    public T closeButtonText(String closeButtonText) {
        this.closeButtonText = closeButtonText;
        return (T) this;
    }

    public T actionButtonText(String actionButtonText) {
        this.actionButtonText = actionButtonText;
        return (T) this;
    }

    public T width(double width) {
        this.width = width;
        return (T) this;
    }

    public T showBusyAnimation() {
        this.showBusyAnimation = true;
        return (T) this;
    }

    public T dontShowAgainId(String key, Preferences preferences) {
        this.dontShowAgainId = key;
        this.preferences = preferences;
        return (T) this;
    }

    public T dontShowAgainText(String dontShowAgainText) {
        this.dontShowAgainText = dontShowAgainText;
        return (T) this;
    }

    public T hideCloseButton() {
        this.hideCloseButton = true;
        return (T) this;
    }

    public T useAnimation(boolean useAnimation) {
        this.useAnimation = useAnimation;
        return (T) this;
    }

    public T setHeadlineStyle(String headlineStyle) {
        this.headlineStyle = headlineStyle;
        return (T) this;
    }
    

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void createGridPane() {
        gridPane = new GridPane();
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        gridPane.setPadding(new Insets(30, 30, 30, 30));
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
                    new KeyValue(gridPane.translateYProperty(), -10, interpolator)
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
        if (type.animationType == AnimationType.SlideDownFromCenterTop)
            gridPane.setId("popup-bg-top");
        else
            gridPane.setId("popup-bg");

        if (headLineLabel != null)
            headLineLabel.setId("popup-headline");
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

            headLineLabel = new Label(BSResources.get(headLine));
            headLineLabel.setMouseTransparent(true);

            if (headlineStyle != null)
                headLineLabel.setStyle(headlineStyle);
            
            GridPane.setHalignment(headLineLabel, HPos.LEFT);
            GridPane.setRowIndex(headLineLabel, rowIndex);
            GridPane.setColumnSpan(headLineLabel, 2);
            gridPane.getChildren().addAll(headLineLabel);
        }
    }

    protected void addSeparator() {
        if (headLine != null) {
            Separator separator = new Separator();
            separator.setMouseTransparent(true);
            separator.setOrientation(Orientation.HORIZONTAL);
            separator.setStyle("-fx-background: #ccc;");
            GridPane.setHalignment(separator, HPos.CENTER);
            GridPane.setRowIndex(separator, ++rowIndex);
            GridPane.setColumnSpan(separator, 2);

            gridPane.getChildren().add(separator);
        }
    }

    protected void addMessage() {
        if (message != null) {
            messageLabel = new Label(truncatedMessage);
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
        messageLabel.setText(truncatedMessage
                + "\n\nTo help us to improve the software please report the bug at our issue tracker at Github or send it by email to the developers.\n" +
                "The error message will be copied to clipboard when you click the below buttons.\n" +
                "It will make debugging easier if you can attach the bitsquare.log file which you can find in the application directory.");

        Button githubButton = new Button("Report to Github issue tracker");
        GridPane.setMargin(githubButton, new Insets(20, 0, 0, 0));
        GridPane.setHalignment(githubButton, HPos.RIGHT);
        GridPane.setRowIndex(githubButton, ++rowIndex);
        GridPane.setColumnIndex(githubButton, 1);
        gridPane.getChildren().add(githubButton);

        githubButton.setOnAction(event -> {
            Utilities.copyToClipboard(message);
            Utilities.openWebPage("https://github.com/bitsquare/bitsquare/issues");
        });

        Button mailButton = new Button("Report by email");
        GridPane.setHalignment(mailButton, HPos.RIGHT);
        GridPane.setRowIndex(mailButton, ++rowIndex);
        GridPane.setColumnIndex(mailButton, 1);
        gridPane.getChildren().add(mailButton);
        mailButton.setOnAction(event -> {
            Utilities.copyToClipboard(message);
            Utilities.openMail("manfred@bitsquare.io",
                    "Error report",
                    "Error message:\n" + message);
        });
    }

    protected void addBusyAnimation() {
        busyAnimation = new BusyAnimation();
        GridPane.setHalignment(busyAnimation, HPos.CENTER);
        GridPane.setRowIndex(busyAnimation, ++rowIndex);
        GridPane.setColumnSpan(busyAnimation, 2);
        gridPane.getChildren().add(busyAnimation);
    }

    protected void addDontShowAgainCheckBox() {
        if (dontShowAgainId != null && preferences != null) {
            if (dontShowAgainText == null)
                dontShowAgainText = "Don't show again";
            CheckBox dontShowAgainCheckBox = addCheckBox(gridPane, rowIndex, dontShowAgainText, buttonDistance - 1);
            GridPane.setColumnIndex(dontShowAgainCheckBox, 0);
            GridPane.setHalignment(dontShowAgainCheckBox, HPos.LEFT);
            dontShowAgainCheckBox.setOnAction(e -> preferences.dontShowAgain(dontShowAgainId, dontShowAgainCheckBox.isSelected()));
        }
    }

    protected void addCloseButton() {
        closeButton = new Button(closeButtonText == null ? "Close" : closeButtonText);
        closeButton.setOnAction(event -> doClose());

        if (actionHandlerOptional.isPresent() || actionButtonText != null) {
            actionButton = new Button(actionButtonText == null ? "Ok" : actionButtonText);
            actionButton.setDefaultButton(true);
            //TODO app wide focus
            //actionButton.requestFocus();
            actionButton.setOnAction(event -> {
                hide();
                actionHandlerOptional.ifPresent(Runnable::run);
            });

            Pane spacer = new Pane();
            HBox hBox = new HBox();
            hBox.setSpacing(10);
            hBox.getChildren().addAll(spacer, closeButton, actionButton);
            HBox.setHgrow(spacer, Priority.ALWAYS);

            GridPane.setHalignment(hBox, HPos.RIGHT);
            GridPane.setRowIndex(hBox, ++rowIndex);
            GridPane.setColumnSpan(hBox, 2);
            GridPane.setMargin(hBox, new Insets(buttonDistance, 0, 0, 0));
            gridPane.getChildren().add(hBox);
        } else if (!hideCloseButton) {
            closeButton.setDefaultButton(true);
            GridPane.setHalignment(closeButton, HPos.RIGHT);
            if (!showReportErrorButtons)
                GridPane.setMargin(closeButton, new Insets(buttonDistance, 0, 0, 0));
            GridPane.setRowIndex(closeButton, ++rowIndex);
            GridPane.setColumnIndex(closeButton, 1);
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
        else
            truncatedMessage = message;
    }

    protected double getDuration(double duration) {
        return useAnimation && Preferences.useAnimations() ? duration : 1;
    }

    @Override
    public String toString() {
        return "Popup{" +
                "headLine='" + headLine + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
