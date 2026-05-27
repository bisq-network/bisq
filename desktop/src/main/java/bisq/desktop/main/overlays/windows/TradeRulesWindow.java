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

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipCheckBox;
import bisq.desktop.main.overlays.Overlay;

import bisq.core.locale.Res;

import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

import javafx.css.PseudoClass;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

public class TradeRulesWindow extends Overlay<TradeRulesWindow> {
    private static final double WINDOW_WIDTH = 900;
    private static final double SHADOW_PADDING = 18;
    private static final double GRID_HORIZONTAL_PADDING = 40;
    private static final double HEADER_ICON_BOX_WIDTH = 64;
    private static final double HEADER_TEXT_GAP = 14;
    private static final double RULE_ROW_HORIZONTAL_PADDING = 48;
    private static final double RULE_ROW_ICON_BOX_WIDTH = 44;
    private static final double RULE_ROW_TEXT_GAP = 18;
    private static final double PAGE_WIDTH = WINDOW_WIDTH - GRID_HORIZONTAL_PADDING;
    private static final double HEADER_TEXT_WIDTH = PAGE_WIDTH - HEADER_ICON_BOX_WIDTH - HEADER_TEXT_GAP;
    private static final double RULE_ROW_TEXT_WIDTH = PAGE_WIDTH -
            RULE_ROW_HORIZONTAL_PADDING -
            RULE_ROW_ICON_BOX_WIDTH -
            RULE_ROW_TEXT_GAP;
    private static final PseudoClass ERROR_PSEUDO_CLASS = PseudoClass.getPseudoClass("error");

    private StackPane rootContainer;
    private CheckBox acceptCheckBox;
    private boolean validationRequested;
    private final boolean wasAccepted;

    public TradeRulesWindow() {
        this(false);
    }

    public TradeRulesWindow(boolean wasAccepted) {
        this.wasAccepted = wasAccepted;
        type = Type.Attention;
        width = WINDOW_WIDTH;
    }

    @Override
    public void show() {
        rowIndex = -1;
        validationRequested = false;
        actionButtonText(Res.get("tacWindow.agree"));
        closeButtonText(Res.get("shared.close"));

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

        gridPane = new GridPane(0, 10);
        gridPane.getStyleClass().add("tac-agreement-content");
        gridPane.setPadding(new Insets(10, 20, 14, 20));
        gridPane.setPrefWidth(width);

        ColumnConstraints columnConstraints = new ColumnConstraints();
        columnConstraints.setHgrow(Priority.ALWAYS);
        columnConstraints.setFillWidth(true);
        gridPane.getColumnConstraints().add(columnConstraints);

        windowContainer.getChildren().add(gridPane);
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
        VBox page = new VBox(10);
        page.getStyleClass().addAll("tac-agreement-page", "trade-rules-page");

        VBox rulesContent = createRulesContent();
        page.getChildren().addAll(
                createHeaderSection(),
                rulesContent
        );

        GridPane.setRowIndex(page, ++rowIndex);
        GridPane.setHgrow(page, Priority.ALWAYS);
        GridPane.setVgrow(page, Priority.ALWAYS);
        gridPane.getChildren().add(page);
    }

    @Override
    protected void addButtons() {
        closeButton = new AutoTooltipButton(closeButtonText);
        closeButton.getStyleClass().add("compact-button");
        closeButton.setMinWidth(120);
        closeButton.setFocusTraversable(false);
        closeButton.setOnAction(event -> doClose());

        actionButton = new AutoTooltipButton(actionButtonText);
        actionButton.setMinWidth(120);
        actionButton.setDefaultButton(true);
        actionButton.getStyleClass().add("action-button");
        actionButton.setOnAction(event -> handleContinue());
        actionButton.setVisible(!wasAccepted);
        actionButton.setManaged(!wasAccepted);

        acceptCheckBox = new AutoTooltipCheckBox(Res.get("tradeRules.accept"));
        acceptCheckBox.getStyleClass().addAll("tac-agreement-check-box");
        acceptCheckBox.setAlignment(Pos.CENTER_LEFT);
        acceptCheckBox.setOnAction(event -> {
            if (validationRequested) {
                updateCheckBoxErrorState();
            }
        });
        acceptCheckBox.setSelected(wasAccepted);
        acceptCheckBox.setDisable(wasAccepted);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox.setMargin(acceptCheckBox, new Insets(0, 0, 0, 10));
        HBox footer = new HBox(10, acceptCheckBox, spacer, closeButton, actionButton);
        footer.setAlignment(Pos.CENTER);

        GridPane.setMargin(footer, new Insets(10, 0, 0, 0));
        GridPane.setRowIndex(footer, ++rowIndex);
        gridPane.getChildren().add(footer);
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
        gridPane.requestFocus();
    }

    private VBox createHeaderSection() {
        VBox section = new VBox(12);
        section.getStyleClass().add("tac-agreement-header-section");
        section.setPrefWidth(PAGE_WIDTH);
        section.setMaxWidth(Double.MAX_VALUE);

        HBox header = new HBox(HEADER_TEXT_GAP);
        header.getStyleClass().add("tac-agreement-page-header");
        header.setAlignment(Pos.CENTER);
        header.setFillHeight(false);
        header.setPrefWidth(PAGE_WIDTH);
        header.setMaxWidth(Double.MAX_VALUE);

        VBox titleBox = new VBox(3);
        titleBox.getStyleClass().add("tac-agreement-header-title-box");
        titleBox.setAlignment(Pos.CENTER);
        titleBox.setPrefWidth(HEADER_TEXT_WIDTH);
        titleBox.setMinWidth(0);
        titleBox.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label(Res.get("tradeRules.headline"));
        title.getStyleClass().add("trade-rules-header-title");
        title.setWrapText(true);
        title.setPrefWidth(HEADER_TEXT_WIDTH);
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setTextAlignment(TextAlignment.CENTER);
        title.setAlignment(Pos.CENTER);

        Label subtitle = new Label(Res.get("tradeRules.subtitle"));
        subtitle.getStyleClass().add("tac-agreement-header-subtitle");
        subtitle.setWrapText(true);
        subtitle.setPrefWidth(HEADER_TEXT_WIDTH);
        subtitle.setMinWidth(0);
        subtitle.setMaxWidth(Double.MAX_VALUE);

        titleBox.getChildren().add(title);
        // titleBox.getChildren().addAll(title, subtitle);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        header.getChildren().addAll(/*iconBox, */titleBox);

        Region divider = new Region();
        divider.getStyleClass().add("tac-agreement-header-divider");

        section.getChildren().addAll(header, divider);
        return section;
    }

    private VBox createRulesContent() {
        VBox content = new VBox();
        content.getStyleClass().add("trade-rules-list");
        content.getChildren().addAll(
                createRuleRow(1,
                        Res.get("tradeRules.trading.title"),
                        Res.get("tradeRules.trading.body")),
                createRuleSeparator(),
                createRuleRow(2,
                        Res.get("tradeRules.fees.title"),
                        Res.get("tradeRules.fees.body")),
                createRuleSeparator(),

                createRuleRow(3,
                        Res.get("tradeRules.mediation.title"),
                        Res.get("tradeRules.mediation.body")),
                createRuleSeparator(),

                createRuleRow(4,
                        Res.get("tradeRules.arbitration.title"),
                        Res.get("tradeRules.arbitration.body")),
                createRuleSeparator(),

                createRuleRow(5,
                        Res.get("tradeRules.refund.title"),
                        Res.get("tradeRules.refund.body")),
                createRuleSeparator(),

                createRuleRow(6,
                        Res.get("tradeRules.reimbursement.title"),
                        Res.get("tradeRules.reimbursement.body")),
                createRuleSeparator()
        );
        return content;
    }

    private HBox createRuleRow(int number, String title, String body) {
        HBox row = new HBox(RULE_ROW_TEXT_GAP);
        row.getStyleClass().add("trade-rules-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setFillHeight(false);
        row.setMinHeight(Region.USE_PREF_SIZE);
        row.setMaxWidth(Double.MAX_VALUE);

        VBox textBox = new VBox(3);
        textBox.setPrefWidth(RULE_ROW_TEXT_WIDTH);
        textBox.setMinWidth(0);
        textBox.setMinHeight(Region.USE_PREF_SIZE);
        textBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Label titleLabel = new Label(number + ". " + title);
        titleLabel.getStyleClass().add("trade-rules-row-title");
        titleLabel.setWrapText(true);
        titleLabel.setPrefWidth(RULE_ROW_TEXT_WIDTH);
        titleLabel.setMinWidth(0);
        titleLabel.setMinHeight(Region.USE_PREF_SIZE);
        titleLabel.setMaxWidth(Double.MAX_VALUE);

        Label bodyLabel = new Label(body);
        bodyLabel.getStyleClass().add("trade-rules-row-body");
        bodyLabel.setWrapText(true);
        bodyLabel.setPrefWidth(RULE_ROW_TEXT_WIDTH);
        bodyLabel.setMinWidth(0);
        bodyLabel.setMinHeight(Region.USE_PREF_SIZE);
        bodyLabel.setMaxWidth(Double.MAX_VALUE);

        textBox.getChildren().addAll(titleLabel, bodyLabel);
        row.getChildren().addAll(/*iconBox, */textBox);
        return row;
    }

    private Region createRuleSeparator() {
        Region separator = new Region();
        separator.getStyleClass().add("trade-rules-separator");
        return separator;
    }

    private void handleContinue() {
        if (acceptCheckBox.isSelected()) {
            validationRequested = false;
            updateCheckBoxErrorState();
            hide();
            actionHandlerOptional.ifPresent(Runnable::run);
        } else {
            validationRequested = true;
            updateCheckBoxErrorState();
        }
    }

    private void updateCheckBoxErrorState() {
        if (acceptCheckBox != null) {
            acceptCheckBox.pseudoClassStateChanged(ERROR_PSEUDO_CLASS,
                    validationRequested && !acceptCheckBox.isSelected());
        }
    }
}
