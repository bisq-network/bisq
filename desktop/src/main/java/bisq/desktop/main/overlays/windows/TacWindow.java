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

package bisq.desktop.main.overlays.windows;

import bisq.desktop.app.BisqApp;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipCheckBox;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.util.ImageUtil;

import bisq.core.locale.Res;

import com.google.inject.Inject;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

public class TacWindow extends Overlay<TacWindow> {
    private static final double WINDOW_WIDTH = 900;
    private static final double POPUP_HEIGHT = 555;
    private static final double SHADOW_PADDING = 18;
    private static final double TITLE_BAR_HEIGHT = 40;
    private static final double GRID_VERTICAL_PADDING = 24;
    private static final double GRID_ROW_GAPS = 20;
    private static final double FOOTER_SEPARATOR_HEIGHT = 1;
    private static final double FOOTER_HEIGHT = 48;
    private static final double GRID_HORIZONTAL_PADDING = 40;
    private static final double HEADER_ICON_BOX_WIDTH = 64;
    private static final double HEADER_TEXT_GAP = 14;
    private static final double RISK_DETAIL_HORIZONTAL_PADDING = 48;
    private static final double RISK_DETAIL_ICON_BOX_WIDTH = 44;
    private static final double RISK_DETAIL_TEXT_GAP = 18;
    private static final double PAGE_WIDTH = WINDOW_WIDTH - GRID_HORIZONTAL_PADDING;
    private static final double HEADER_TEXT_WIDTH = PAGE_WIDTH - HEADER_ICON_BOX_WIDTH - HEADER_TEXT_GAP;
    private static final double RISK_DETAIL_TEXT_WIDTH = PAGE_WIDTH -
            RISK_DETAIL_HORIZONTAL_PADDING -
            RISK_DETAIL_ICON_BOX_WIDTH -
            RISK_DETAIL_TEXT_GAP;
    private static final PseudoClass ERROR_PSEUDO_CLASS = PseudoClass.getPseudoClass("error");
    private static final double PAGE_HEIGHT = POPUP_HEIGHT -
            TITLE_BAR_HEIGHT -
            GRID_VERTICAL_PADDING -
            GRID_ROW_GAPS -
            FOOTER_SEPARATOR_HEIGHT -
            FOOTER_HEIGHT;
    private static final double ICON_SIZE = 25;
    private static final String TAC_DECENTRALIZED_ICON_ID = "image-tac-decentr";
    private static final String TAC_REFUND_ICON_ID = "image-tac-refund";
    private static final String TAC_SHIELD_ICON_ID = "image-tac-shield";
    private static final String TAC_LEGAL_ICON_ID = "image-tac-legal";
    private static final String WARNING_GREEN_ICON_ID = "image-warning-green";
    private static final String WARNING_YELLOW_ICON_ID = "image-warning-yellow";
    private static final String INFO_GREEN_ICON_ID = "image-info-green";

    private StackPane rootContainer;
    private VBox riskPage;
    private VBox legalPage;
    private CheckBox lossOfFundsCheckBox;
    private CheckBox compensationCheckBox;
    private CheckBox legalTermsCheckBox;
    private AutoTooltipButton backButton;
    private boolean riskValidationRequested;
    private boolean legalValidationRequested;
    private boolean isLegalPageVisible;

    @Inject
    public TacWindow() {
        type = Type.Attention;
        width = WINDOW_WIDTH;
    }

    @Override
    public void show() {
        rowIndex = -1;
        riskValidationRequested = false;
        legalValidationRequested = false;
        isLegalPageVisible = false;
        actionButtonText(getNextButtonText());
        closeButtonText(getRejectButtonText());
        onClose(BisqApp.getShutDownHandler());

        super.show();
    }

    @Override
    protected void createGridPane() {
        rootContainer = new StackPane();
        rootContainer.getStyleClass().add("tac-agreement-root");
        rootContainer.setPadding(new Insets(SHADOW_PADDING));
        rootContainer.setPrefWidth(width + 2 * SHADOW_PADDING);

        VBox windowContainer = new VBox();
        windowContainer.getStyleClass().add("tac-agreement-window");
        windowContainer.setPrefWidth(width);
        windowContainer.setMaxWidth(width);
        setFixedHeight(windowContainer, POPUP_HEIGHT);

        gridPane = new GridPane();
        gridPane.getStyleClass().add("tac-agreement-content");
        gridPane.setHgap(0);
        gridPane.setVgap(10);
        gridPane.setPadding(new Insets(10, 20, 14, 20));
        gridPane.setPrefWidth(width);

        ColumnConstraints columnConstraints = new ColumnConstraints();
        columnConstraints.setHgrow(Priority.ALWAYS);
        columnConstraints.setFillWidth(true);
        gridPane.getColumnConstraints().add(columnConstraints);

        windowContainer.getChildren().addAll(createTitleBar(), gridPane);
        rootContainer.getChildren().add(windowContainer);
    }

    @Override
    protected Region getRootContainer() {
        return rootContainer;
    }

    @Override
    protected void addHeadLine() {
    }

    @Override
    protected void addMessage() {
        riskPage = new VBox(8);
        riskPage.getStyleClass().addAll("tac-agreement-page", "tac-agreement-risk-page");
        setFixedHeight(riskPage, PAGE_HEIGHT);

        HBox importantCallout = createImportantCallout();
        VBox.setMargin(importantCallout, new Insets(9, 0, 0, 0));
        riskPage.getChildren().addAll(createRiskOverview(), importantCallout, createConfirmationsPanel());

        legalPage = new VBox(12);
        legalPage.getStyleClass().addAll("tac-agreement-page", "tac-agreement-legal-page");
        setFixedHeight(legalPage, PAGE_HEIGHT);
        legalPage.getChildren().addAll(createLegalPanel(), createLegalConfirmationRow());

        StackPane pageContainer = new StackPane(riskPage, legalPage);
        pageContainer.getStyleClass().add("tac-agreement-page-container");
        pageContainer.setAlignment(Pos.TOP_LEFT);
        setFixedHeight(pageContainer, PAGE_HEIGHT);
        GridPane.setRowIndex(pageContainer, ++rowIndex);
        GridPane.setHgrow(pageContainer, Priority.ALWAYS);
        GridPane.setVgrow(pageContainer, Priority.ALWAYS);

        gridPane.getChildren().add(pageContainer);
    }

    @Override
    protected void addButtons() {
        Region separator = new Region();
        separator.getStyleClass().add("tac-agreement-footer-separator");
        GridPane.setHgrow(separator, Priority.ALWAYS);
        GridPane.setRowIndex(separator, ++rowIndex);
        gridPane.getChildren().add(separator);

        HBox footer = new HBox(14);
        footer.getStyleClass().add("tac-agreement-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        backButton = new AutoTooltipButton("< " + Res.get("tacWindow.legal.back"));
        backButton.getStyleClass().addAll("tac-agreement-secondary-button", "tac-agreement-back-button");
        backButton.setMinWidth(120);
        backButton.setFocusTraversable(false);
        backButton.setOnAction(event -> setLegalPageVisible(false));

        closeButton = new AutoTooltipButton(closeButtonText);
        closeButton.getStyleClass().addAll("tac-agreement-secondary-button", "tac-agreement-reject-button");
        closeButton.setMinWidth(190);
        closeButton.setFocusTraversable(false);
        closeButton.setOnAction(event -> doClose());

        actionButton = new AutoTooltipButton(actionButtonText);
        actionButton.getStyleClass().addAll("action-button", "tac-agreement-action-button");
        actionButton.setMinWidth(190);
        actionButton.setDefaultButton(true);
        actionButton.setOnAction(event -> handleAction());

        footer.getChildren().addAll(backButton, spacer, closeButton, actionButton);

        GridPane.setHgrow(footer, Priority.ALWAYS);
        GridPane.setRowIndex(footer, ++rowIndex);
        gridPane.getChildren().add(footer);

        setLegalPageVisible(false);
    }

    @Override
    protected void applyStyles() {
    }

    @Override
    protected void setupKeyHandler(Scene scene) {
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
                doClose();
            } else if (event.getCode() == KeyCode.ENTER && actionButton != null && !actionButton.isDisabled()) {
                event.consume();
                actionButton.fire();
            }
        });
    }

    @Override
    protected void onShow() {
        display();
    }

    private BorderPane createTitleBar() {
        BorderPane titleBar = new BorderPane();
        titleBar.getStyleClass().add("tac-agreement-title-bar");

        Label title = new Label(Res.get("tacWindow.headline"));
        title.getStyleClass().add("tac-agreement-title");
        title.setMaxWidth(Double.MAX_VALUE);
        title.setAlignment(Pos.CENTER);
        BorderPane.setAlignment(title, Pos.CENTER);

        titleBar.setCenter(title);
        return titleBar;
    }

    private VBox createRiskOverview() {
        VBox section = new VBox(8);
        section.getStyleClass().add("tac-agreement-risk-overview");

        section.getChildren().addAll(
                createHeaderSection(TAC_SHIELD_ICON_ID,
                        Res.get("tacWindow.risk.headline"),
                        Res.get("tacWindow.risk.subtitle")),
                createRiskDetailRow(TAC_DECENTRALIZED_ICON_ID,
                        Res.get("tacWindow.risk.p2p.title"),
                        Res.get("tacWindow.risk.p2p.body")),
                createRiskSeparator(),
                createRiskDetailRow(WARNING_GREEN_ICON_ID,
                        Res.get("tacWindow.risk.financial.title"),
                        Res.get("tacWindow.risk.financial.body")),
                createRiskSeparator(),
                createRiskDetailRow(TAC_REFUND_ICON_ID,
                        Res.get("tacWindow.risk.noGuarantees.title"),
                        Res.get("tacWindow.risk.noGuarantees.body"))
        );
        return section;
    }

    private HBox createRiskDetailRow(String iconId, String title, String body) {
        HBox row = new HBox(RISK_DETAIL_TEXT_GAP);
        row.getStyleClass().add("tac-agreement-risk-detail-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setFillHeight(false);
        row.setMinHeight(Region.USE_PREF_SIZE);
        row.setMaxWidth(Double.MAX_VALUE);

        StackPane iconBox = new StackPane(createImageIcon(iconId, ICON_SIZE, ICON_SIZE,
                "tac-agreement-risk-detail-image"));
        iconBox.getStyleClass().add("tac-agreement-risk-detail-icon-box");

        VBox textBox = new VBox(3);
        textBox.setPrefWidth(RISK_DETAIL_TEXT_WIDTH);
        textBox.setMinWidth(0);
        textBox.setMinHeight(Region.USE_PREF_SIZE);
        textBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("tac-agreement-risk-detail-title");
        titleLabel.setWrapText(true);
        titleLabel.setPrefWidth(RISK_DETAIL_TEXT_WIDTH);
        titleLabel.setMinWidth(0);
        titleLabel.setMinHeight(Region.USE_PREF_SIZE);
        titleLabel.setMaxWidth(Double.MAX_VALUE);

        Label bodyLabel = new Label(body);
        bodyLabel.getStyleClass().add("tac-agreement-risk-detail-body");
        bodyLabel.setWrapText(true);
        bodyLabel.setPrefWidth(RISK_DETAIL_TEXT_WIDTH);
        bodyLabel.setMinWidth(0);
        bodyLabel.setMinHeight(Region.USE_PREF_SIZE);
        bodyLabel.setMaxWidth(Double.MAX_VALUE);

        textBox.getChildren().addAll(titleLabel, bodyLabel);
        row.getChildren().addAll(iconBox, textBox);
        return row;
    }

    private Region createRiskSeparator() {
        Region separator = new Region();
        separator.getStyleClass().add("tac-agreement-risk-separator");
        return separator;
    }

    private HBox createImportantCallout() {
        HBox callout = new HBox(12);
        callout.getStyleClass().add("tac-agreement-important-callout");
        callout.setAlignment(Pos.CENTER_LEFT);
        callout.setMaxWidth(Double.MAX_VALUE);

        HBox iconBox = new HBox();
        iconBox.getStyleClass().add("tac-agreement-important-icon-box");
        iconBox.setAlignment(Pos.CENTER);

        ImageView icon = createImageIcon(WARNING_YELLOW_ICON_ID, ICON_SIZE, ICON_SIZE, "tac-agreement-important-image");
        iconBox.getChildren().add(icon);

        Region divider = new Region();
        divider.getStyleClass().add("tac-agreement-important-divider");

        VBox bullets = new VBox(2);
        bullets.getStyleClass().add("tac-agreement-important-bullets");
        bullets.getChildren().add(createImportantBullet(Res.get("tacWindow.risk.warning")));
        HBox.setHgrow(bullets, Priority.ALWAYS);

        callout.getChildren().addAll(iconBox, divider, bullets);
        return callout;
    }

    private Label createImportantBullet(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("tac-agreement-important-bullet");
        label.setWrapText(true);
        return label;
    }

    private VBox createConfirmationsPanel() {
        VBox panel = new VBox(6);
        panel.getStyleClass().add("tac-agreement-confirm-panel");

        Label headline = new Label(Res.get("tacWindow.risk.confirm.headline"));
        headline.getStyleClass().add("tac-agreement-confirm-headline");

        lossOfFundsCheckBox = createConfirmCheckBox(Res.get("tacWindow.risk.accept1"));
        compensationCheckBox = createConfirmCheckBox(Res.get("tacWindow.risk.accept2"));

        panel.getChildren().addAll(headline,
                createConfirmRow(lossOfFundsCheckBox),
                createConfirmRow(compensationCheckBox));
        return panel;
    }

    private VBox createLegalPanel() {
        VBox panel = new VBox(12);
        panel.getStyleClass().add("tac-agreement-legal-panel");

        ScrollPane legalScrollPane = new ScrollPane(createLegalContent());
        legalScrollPane.getStyleClass().add("tac-agreement-legal-scroll-pane");
        legalScrollPane.setFitToWidth(true);
        legalScrollPane.setPrefHeight(260);
        legalScrollPane.setMinHeight(230);
        VBox.setVgrow(legalScrollPane, Priority.ALWAYS);

        panel.getChildren().addAll(
                createHeaderSection(TAC_LEGAL_ICON_ID,
                        Res.get("tacWindow.legal.headline"),
                        Res.get("tacWindow.legal.subtitle")),
                legalScrollPane,
                createLegalAcknowledgment()
        );
        return panel;
    }

    private VBox createHeaderSection(String iconId, String titleText, String subtitleText) {
        VBox section = new VBox(12);
        section.getStyleClass().add("tac-agreement-header-section");
        section.setPrefWidth(PAGE_WIDTH);
        section.setMaxWidth(Double.MAX_VALUE);

        HBox header = new HBox(HEADER_TEXT_GAP);
        header.getStyleClass().add("tac-agreement-page-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setFillHeight(false);
        header.setPrefWidth(PAGE_WIDTH);
        header.setMaxWidth(Double.MAX_VALUE);

        StackPane iconBox = new StackPane(createImageIcon(iconId, 50, 50, "tac-agreement-header-image"));
        iconBox.getStyleClass().add("tac-agreement-header-icon-box");
        iconBox.setMinWidth(HEADER_ICON_BOX_WIDTH);
        iconBox.setPrefWidth(HEADER_ICON_BOX_WIDTH);
        iconBox.setMaxWidth(HEADER_ICON_BOX_WIDTH);

        VBox titleBox = new VBox(3);
        titleBox.getStyleClass().add("tac-agreement-header-title-box");
        titleBox.setAlignment(Pos.CENTER_LEFT);
        titleBox.setPrefWidth(HEADER_TEXT_WIDTH);
        titleBox.setMinWidth(0);
        titleBox.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label(titleText);
        title.getStyleClass().add("tac-agreement-header-title");
        title.setWrapText(true);
        title.setPrefWidth(HEADER_TEXT_WIDTH);
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);

        Label subtitle = new Label(subtitleText);
        subtitle.getStyleClass().add("tac-agreement-header-subtitle");
        subtitle.setWrapText(true);
        subtitle.setPrefWidth(HEADER_TEXT_WIDTH);
        subtitle.setMinWidth(0);
        subtitle.setMaxWidth(Double.MAX_VALUE);

        titleBox.getChildren().addAll(title, subtitle);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        header.getChildren().addAll(iconBox, titleBox);

        Region divider = new Region();
        divider.getStyleClass().add("tac-agreement-header-divider");

        section.getChildren().addAll(header, divider);
        return section;
    }

    private VBox createLegalContent() {
        VBox content = new VBox(10);
        content.getStyleClass().add("tac-agreement-legal-scroll-content");
        content.getChildren().addAll(
                createLegalSection("1.", Res.get("tacWindow.legal.section1.title"), Res.get("tacWindow.legal.section1.body")),
                createLegalSection("2.", Res.get("tacWindow.legal.section2.title"), Res.get("tacWindow.legal.section2.body")),
                createLegalSection("3.", Res.get("tacWindow.legal.section3.title"), Res.get("tacWindow.legal.section3.body")),
                createLegalSection("4.", Res.get("tacWindow.legal.section4.title"), Res.get("tacWindow.legal.section4.body")),
                createLegalSection("5.", Res.get("tacWindow.legal.section5.title"), Res.get("tacWindow.legal.section5.body")),
                createLegalSection("6.", Res.get("tacWindow.legal.section6.title"), Res.get("tacWindow.legal.section6.body")),
                createLegalSection("7.", Res.get("tacWindow.legal.section7.title"), Res.get("tacWindow.legal.section7.body"))
        );
        return content;
    }

    private HBox createLegalSection(String number, String title, String body) {
        HBox section = new HBox(6);
        section.getStyleClass().add("tac-agreement-legal-section");
        section.setAlignment(Pos.TOP_LEFT);
        section.setMaxWidth(Double.MAX_VALUE);

        Label numberLabel = new Label(number);
        numberLabel.getStyleClass().add("tac-agreement-legal-section-number");

        VBox textBox = new VBox(6);
        textBox.setMinWidth(0);
        textBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("tac-agreement-legal-section-title");
        titleLabel.setWrapText(true);
        titleLabel.setMinWidth(0);
        titleLabel.setMaxWidth(Double.MAX_VALUE);

        Label bodyLabel = new Label(body);
        bodyLabel.getStyleClass().add("tac-agreement-legal-section-body");
        bodyLabel.setWrapText(true);
        bodyLabel.setMinWidth(0);
        bodyLabel.setMaxWidth(Double.MAX_VALUE);

        textBox.getChildren().addAll(titleLabel, bodyLabel);
        section.getChildren().addAll(numberLabel, textBox);
        return section;
    }

    private HBox createLegalAcknowledgment() {
        HBox acknowledgment = new HBox(12);
        acknowledgment.getStyleClass().add("tac-agreement-legal-acknowledgment");
        acknowledgment.setAlignment(Pos.CENTER_LEFT);

        ImageView icon = createImageIcon(INFO_GREEN_ICON_ID, 20, 20, "tac-agreement-legal-acknowledgment-image");
        Label text = new Label(Res.get("tacWindow.legal.acknowledgment"));
        text.getStyleClass().add("tac-agreement-legal-acknowledgment-text");
        text.setWrapText(true);
        HBox.setHgrow(text, Priority.ALWAYS);

        acknowledgment.getChildren().addAll(icon, text);
        return acknowledgment;
    }

    private VBox createLegalConfirmationRow() {
        legalTermsCheckBox = createConfirmCheckBox(Res.get("tacWindow.legal.accept3"));
        legalTermsCheckBox.getStyleClass().add("tac-agreement-legal-check-box");

        VBox row = createConfirmRow(legalTermsCheckBox);
        row.getStyleClass().add("tac-agreement-legal-check-row");
        return row;
    }

    private CheckBox createConfirmCheckBox(String text) {
        CheckBox checkBox = new AutoTooltipCheckBox(text);
        checkBox.getStyleClass().add("tac-agreement-check-box");
        checkBox.setMinWidth(0);
        checkBox.setMaxWidth(Double.MAX_VALUE);
        checkBox.setWrapText(true);
        checkBox.setOnAction(event -> {
            if (riskValidationRequested || legalValidationRequested) {
                updateCheckBoxErrorStates();
            }
        });
        return checkBox;
    }

    private VBox createConfirmRow(CheckBox checkBox) {
        VBox container = new VBox();
        container.getStyleClass().add("tac-agreement-check-row-container");
        container.setMaxWidth(Double.MAX_VALUE);

        HBox row = new HBox(10);
        row.getStyleClass().add("tac-agreement-check-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);

        row.getChildren().add(checkBox);
        HBox.setHgrow(checkBox, Priority.ALWAYS);
        container.getChildren().add(row);
        return container;
    }

    private ImageView createImageIcon(String iconId, double fitWidth, double fitHeight, String styleClass) {
        ImageView imageView = ImageUtil.getImageViewById(iconId);
        imageView.getStyleClass().add(styleClass);
        imageView.setFitWidth(fitWidth);
        imageView.setFitHeight(fitHeight);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setMouseTransparent(true);
        return imageView;
    }

    private void handleAction() {
        if (isLegalPageVisible) {
            handleAgree();
        } else {
            handleNext();
        }
    }

    private void handleNext() {
        if (riskConfirmationsChecked()) {
            riskValidationRequested = false;
            updateCheckBoxErrorStates();
            setLegalPageVisible(true);
        } else {
            riskValidationRequested = true;
            updateCheckBoxErrorStates();
        }
    }

    private void handleAgree() {
        if (allConfirmationsChecked()) {
            hide();
            actionHandlerOptional.ifPresent(Runnable::run);
        } else {
            if (!riskConfirmationsChecked()) {
                riskValidationRequested = true;
                setLegalPageVisible(false);
            } else {
                legalValidationRequested = true;
            }
            updateCheckBoxErrorStates();
        }
    }

    private boolean riskConfirmationsChecked() {
        return lossOfFundsCheckBox.isSelected() &&
                compensationCheckBox.isSelected();
    }

    private boolean allConfirmationsChecked() {
        return riskConfirmationsChecked() &&
                legalTermsCheckBox.isSelected();
    }

    private void setLegalPageVisible(boolean visible) {
        isLegalPageVisible = visible;
        setPageVisibility(riskPage, !visible);
        setPageVisibility(legalPage, visible);

        if (closeButton != null) {
            closeButton.disarm();
            closeButton.setText(getRejectButtonText());
            closeButton.setOnAction(event -> doClose());
        }
        if (actionButton != null) {
            actionButton.setText(visible ? getAcceptButtonText() : getNextButtonText());
        }
        if (backButton != null) {
            backButton.setManaged(visible);
            backButton.setVisible(visible);
        }
        if (!visible && rootContainer != null) {
            Platform.runLater(rootContainer::requestFocus);
        }
    }

    private void setPageVisibility(Region page, boolean visible) {
        if (page != null) {
            page.setManaged(visible);
            page.setVisible(visible);
        }
    }

    private String getAcceptButtonText() {
        return Res.get("tacWindow.agree");
    }

    private String getRejectButtonText() {
        return Res.get("tacWindow.disagree");
    }

    private String getNextButtonText() {
        return Res.get("tacWindow.risk.next");
    }

    private void setFixedHeight(Region region, double height) {
        region.setMinHeight(height);
        region.setPrefHeight(height);
        region.setMaxHeight(height);
    }

    private void updateCheckBoxErrorStates() {
        updateCheckBoxErrorState(lossOfFundsCheckBox);
        updateCheckBoxErrorState(compensationCheckBox);
        updateCheckBoxErrorState(legalTermsCheckBox);
    }

    private void updateCheckBoxErrorState(CheckBox checkBox) {
        if (checkBox == null) {
            return;
        }

        boolean isRiskCheckBox = checkBox == lossOfFundsCheckBox || checkBox == compensationCheckBox;
        boolean validationRequested = isRiskCheckBox ? riskValidationRequested : legalValidationRequested;
        boolean show = validationRequested && !checkBox.isSelected();
        checkBox.pseudoClassStateChanged(ERROR_PSEUDO_CLASS, show);
    }
}
