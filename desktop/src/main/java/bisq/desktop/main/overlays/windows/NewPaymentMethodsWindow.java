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
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.locale.Res;
import bisq.core.user.DontShowAgainLookup;

import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;

import java.util.stream.Collectors;

/**
 * Announces the stablecoin and custom payment methods once. It is a {@link Popup}, so the {@link
 * bisq.desktop.main.overlays.popups.PopupManager} shows it after other popups, not on top of them.
 */
public class NewPaymentMethodsWindow extends Popup {
    private static final String SHOWN_KEY = "newPaymentMethodsAnnouncement";

    // Outline icons on a 24 x 24 grid, drawn with the stroke of their style class.
    private static final String STABLECOIN_ICON = "M12 2.5a9.5 9.5 0 1 0 0 19a9.5 9.5 0 1 0 0 -19z " +
            "M15 7.5h-4.25a2.25 2.25 0 0 0 0 4.5h2.5a2.25 2.25 0 0 1 0 4.5H9 M12 5.5v2 M12 16.5v2";
    private static final String CUSTOM_ICON = "M11 4.5H6.5a2 2 0 0 0 -2 2v11a2 2 0 0 0 2 2h11a2 2 0 0 0 2 -2V13 " +
            "M17.6 3.4a1.9 1.9 0 0 1 2.7 2.7L12 14.4l-3.4 0.9l0.9 -3.4z";
    private static final String WARNING_ICON = "M10.4 4.2L2.6 17.6a1.85 1.85 0 0 0 1.6 2.8h15.6a1.85 1.85 0 0 0 " +
            "1.6 -2.8L13.6 4.2a1.85 1.85 0 0 0 -3.2 0z M12 9.5v4 M12 17h0.01";

    // The background of a popup is inset by 44 px for its shadow (popup-bg), so the padding includes it.
    private static final double SHADOW_INSET = 44;
    // Fits into the main window at its minimum size of 1020 x 620.
    private static final double CONTENT_WIDTH = 940;

    public static boolean wasShown() {
        return !DontShowAgainLookup.showAgain(SHOWN_KEY);
    }

    public NewPaymentMethodsWindow() {
        width = CONTENT_WIDTH + 2 * SHADOW_INSET;
    }

    // Marked as shown only when it is displayed, so a popup which waits in the queue at shutdown is shown
    // at the next start.
    @Override
    public void onReadyForDisplay() {
        DontShowAgainLookup.dontShowAgain(SHOWN_KEY, true);
        super.onReadyForDisplay();
    }

    @Override
    protected void createGridPane() {
        super.createGridPane();
        gridPane.setPadding(new Insets(SHADOW_INSET + 32, SHADOW_INSET + 40, SHADOW_INSET + 28, SHADOW_INSET + 40));
        gridPane.setVgap(0);
    }

    @Override
    protected void applyStyles() {
        super.applyStyles();
        gridPane.getStyleClass().add("news-window");
    }

    @Override
    protected void addHeadLine() {
        Label eyebrow = wrappingLabel(letterSpaced(Res.get("popup.newPaymentMethods.eyebrow")), "news-eyebrow");
        Label headline = wrappingLabel(Res.get("popup.newPaymentMethods.headline"), "news-headline");
        Label lead = wrappingLabel(Res.get("popup.newPaymentMethods.lead"), "news-lead");
        VBox.setMargin(headline, new Insets(6, 0, 0, 0));
        VBox.setMargin(lead, new Insets(6, 0, 0, 0));
        addRow(new VBox(eyebrow, headline, lead), Insets.EMPTY);
    }

    @Override
    protected void addMessage() {
        // One grid for both columns, so that the rows of both start at the same height also when a text of
        // one column has more lines.
        GridPane columns = new GridPane();
        columns.setHgap(32);
        // The stablecoin column is wider, as its texts are longer.
        columns.getColumnConstraints().addAll(column(420), column(1), column(375));
        addColumn(columns, 0, STABLECOIN_ICON, "stablecoin", "amount", "coins", "networks");
        addColumn(columns, 2, CUSTOM_ICON, "custom", "setup", "matching", "currencies");

        Region separator = new Region();
        separator.getStyleClass().add("news-separator");
        separator.setMinWidth(1);
        separator.setMaxWidth(1);
        columns.add(separator, 1, 0, 1, 4);

        addRow(columns, new Insets(32, 0, 0, 0));
    }

    @Override
    protected void addButtons() {
        Region line = new Region();
        line.getStyleClass().add("news-separator");
        line.setMinHeight(1);
        line.setMaxHeight(1);
        addRow(line, new Insets(28, 0, 0, 0));

        Label findBothUnder = new Label(Res.get("popup.newPaymentMethods.findBothUnder"));
        findBothUnder.getStyleClass().add("news-hint");
        Label path = new Label(String.join(" \u203A ",
                Res.get("mainView.menu.account"),
                Res.get("account.menu.paymentAccount"),
                Res.get("shared.addNewAccount")));
        path.getStyleClass().add("news-hint-path");
        HBox hint = new HBox(4, findBothUnder, path);
        hint.setAlignment(Pos.CENTER_LEFT);

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button close = new AutoTooltipButton(Res.get("shared.close"));
        close.setOnAction(event -> doClose());

        Button setUpAccount = new AutoTooltipButton(Res.get("popup.newPaymentMethods.setUpAccount"));
        setUpAccount.setDefaultButton(true);
        setUpAccount.getStyleClass().add("action-button");
        setUpAccount.setOnAction(event -> {
            hide();
            actionHandlerOptional.ifPresent(Runnable::run);
        });

        HBox footer = new HBox(12, hint, spacer, close, setUpAccount);
        footer.setAlignment(Pos.CENTER_LEFT);
        addRow(footer, new Insets(16, 0, 0, 0));
    }

    private static ColumnConstraints column(double width) {
        ColumnConstraints constraints = new ColumnConstraints(width);
        constraints.setHgrow(Priority.ALWAYS);
        return constraints;
    }

    private void addColumn(GridPane columns, int column, String iconPath, String feature, String... rows) {
        String prefix = "popup.newPaymentMethods." + feature + ".";

        Label title = wrappingLabel(Res.get(prefix + "title"), "news-column-title");
        Label tagline = wrappingLabel(Res.get(prefix + "tagline"), "news-column-tagline");

        VBox table = new VBox();
        table.getStyleClass().add("news-table");
        for (String row : rows) {
            table.getChildren().add(tableRow(Res.get(prefix + row), Res.get(prefix + row + ".value")));
        }
        table.getChildren().add(tableRow(Res.get("popup.newPaymentMethods.tradeLimit"),
                Res.get(prefix + "tradeLimit.value")));

        Label riskText = wrappingLabel(Res.get(prefix + "risk"), "news-risk-text");
        HBox.setHgrow(riskText, Priority.ALWAYS);
        HBox risk = new HBox(8, icon(WARNING_ICON, "news-risk-icon", 14), riskText);
        risk.setAlignment(Pos.TOP_LEFT);

        HBox iconAndTitle = new HBox(10, icon(iconPath, "news-column-icon", 20), title);
        iconAndTitle.setAlignment(Pos.CENTER_LEFT);
        addCell(columns, iconAndTitle, column, 0, 0);
        addCell(columns, tagline, column, 1, 6);
        addCell(columns, table, column, 2, 16);
        addCell(columns, risk, column, 3, 16);
    }

    private static void addCell(GridPane grid, Region node, int column, int row, double topMargin) {
        GridPane.setValignment(node, VPos.TOP);
        GridPane.setMargin(node, new Insets(topMargin, 0, 0, 0));
        grid.add(node, column, row);
    }

    private HBox tableRow(String name, String value) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("news-table-label");
        nameLabel.setMinWidth(100);
        nameLabel.setPrefWidth(100);
        Label valueLabel = wrappingLabel(value, "news-table-value");
        HBox.setHgrow(valueLabel, Priority.ALWAYS);
        HBox row = new HBox(nameLabel, valueLabel);
        row.getStyleClass().add("news-table-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static StackPane icon(String content, String styleClass, double size) {
        SVGPath path = new SVGPath();
        path.setContent(content);
        path.getStyleClass().add(styleClass);
        double scale = size / 24;
        path.setScaleX(scale);
        path.setScaleY(scale);
        StackPane box = new StackPane(new Group(path));
        box.setMinSize(size, size);
        box.setMaxSize(size, size);
        return box;
    }

    private void addRow(Region node, Insets margin) {
        GridPane.setRowIndex(node, ++rowIndex);
        GridPane.setColumnSpan(node, 2);
        GridPane.setHgrow(node, Priority.ALWAYS);
        GridPane.setMargin(node, margin);
        gridPane.getChildren().add(node);
    }

    // JavaFX CSS has no letter spacing, so a hair space goes between the letters.
    private static String letterSpaced(String text) {
        return text.codePoints()
                .mapToObj(Character::toString)
                .collect(Collectors.joining("\u200A"));
    }

    private static Label wrappingLabel(String text, String styleClass) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.getStyleClass().add(styleClass);
        return label;
    }
}
