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

package bisq.desktop.main.portfolio.pendingtrades;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.portfolio.pendingtrades.steps.TradeStepView;
import bisq.desktop.main.portfolio.pendingtrades.steps.TradeWizardItem;
import bisq.desktop.util.Layout;

import bisq.core.locale.Res;

import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;

import org.fxmisc.easybind.Subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;
import static bisq.desktop.util.FormBuilder.addMultilineLabel;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

public abstract class TradeSubView extends HBox {
    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    protected final PendingTradesViewModel model;
    protected VBox leftVBox;
    protected AnchorPane contentPane;
    protected TradeStepView tradeStepView;
    private AutoTooltipButton openDisputeButton;
    private NotificationGroup notificationGroup;
    protected GridPane leftGridPane;
    protected TitledGroupBg tradeProcessTitledGroupBg;
    protected int leftGridPaneRowIndex = 0;
    protected Subscription viewStateSubscription;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TradeSubView(PendingTradesViewModel model) {
        this.model = model;

        setSpacing(Layout.PADDING_WINDOW);
        buildViews();
    }

    protected void activate() {
    }

    protected void deactivate() {
        if (viewStateSubscription != null)
            viewStateSubscription.unsubscribe();

        if (tradeStepView != null)
            tradeStepView.deactivate();

        if (openDisputeButton != null)
            leftGridPane.getChildren().remove(openDisputeButton);

        if (notificationGroup != null)
            notificationGroup.removeItselfFrom(leftGridPane);
    }

    private void buildViews() {
        addLeftBox();
        addContentPane();

        leftGridPane = new GridPane();
        leftGridPane.setPrefWidth(340);
        leftGridPane.setHgap(Layout.GRID_GAP);
        leftGridPane.setVgap(Layout.GRID_GAP);
        VBox.setMargin(leftGridPane, new Insets(0, 10, 10, 10));
        leftVBox.getChildren().add(leftGridPane);

        leftGridPaneRowIndex = 0;
        tradeProcessTitledGroupBg = addTitledGroupBg(leftGridPane, leftGridPaneRowIndex, 1, Res.get("portfolio.pending.tradeProcess"));
        tradeProcessTitledGroupBg.getStyleClass().add("last");

        addWizards();

        TitledGroupBg noticeTitledGroupBg = addTitledGroupBg(leftGridPane, leftGridPaneRowIndex, 1, "",
                0);
        noticeTitledGroupBg.getStyleClass().add("last");
        Label label = addMultilineLabel(leftGridPane, leftGridPaneRowIndex, "",
                Layout.FIRST_ROW_DISTANCE);
        openDisputeButton = (AutoTooltipButton) addButtonAfterGroup(leftGridPane, ++leftGridPaneRowIndex, Res.get("portfolio.pending.openDispute"));
        GridPane.setColumnIndex(openDisputeButton, 0);
        openDisputeButton.setId("open-dispute-button");

        notificationGroup = new NotificationGroup(noticeTitledGroupBg, label, openDisputeButton);
        notificationGroup.setLabelAndHeadlineVisible(false);
        notificationGroup.setButtonVisible(false);
    }

    public static class NotificationGroup {
        public final TitledGroupBg titledGroupBg;
        public final Label label;
        public final AutoTooltipButton button;

        public NotificationGroup(TitledGroupBg titledGroupBg, Label label, AutoTooltipButton button) {
            this.titledGroupBg = titledGroupBg;
            this.label = label;
            this.button = button;
        }

        public void setLabelAndHeadlineVisible(boolean isVisible) {
            titledGroupBg.setVisible(isVisible);
            label.setVisible(isVisible);
            titledGroupBg.setManaged(isVisible);
            label.setManaged(isVisible);
        }

        public void setButtonVisible(boolean isVisible) {
            button.setVisible(isVisible);
            button.setManaged(isVisible);
        }

        public void removeItselfFrom(GridPane leftGridPane) {
            leftGridPane.getChildren().remove(titledGroupBg);
            leftGridPane.getChildren().remove(label);
            leftGridPane.getChildren().remove(button);
        }
    }

    protected void showItem(TradeWizardItem item) {
        item.setActive();
        createAndAddTradeStepView(item.getViewClass());
    }

    abstract protected void addWizards();

    abstract protected void onViewStateChanged(PendingTradesViewModel.State viewState);

    protected void addWizardsToGridPane(TradeWizardItem tradeWizardItem) {
        if (leftGridPaneRowIndex == 0)
            GridPane.setMargin(tradeWizardItem, new Insets(Layout.FIRST_ROW_DISTANCE + Layout.FLOATING_LABEL_DISTANCE, 0, 0, 0));

        GridPane.setRowIndex(tradeWizardItem, leftGridPaneRowIndex++);
        leftGridPane.getChildren().add(tradeWizardItem);
        GridPane.setRowSpan(tradeProcessTitledGroupBg, leftGridPaneRowIndex);
        GridPane.setFillWidth(tradeWizardItem, true);
    }

    protected void addLineSeparatorToGridPane() {
        final Separator separator = new Separator(Orientation.VERTICAL);
        separator.setMinHeight(22);
        GridPane.setMargin(separator, new Insets(0, 0, 0, 13));
        GridPane.setHalignment(separator, HPos.LEFT);
        GridPane.setRowIndex(separator, leftGridPaneRowIndex++);
        leftGridPane.getChildren().add(separator);
    }

    private void createAndAddTradeStepView(Class<? extends TradeStepView> viewClass) {
        if (tradeStepView != null)
            tradeStepView.deactivate();
        try {
            tradeStepView = viewClass.getDeclaredConstructor(PendingTradesViewModel.class).newInstance(model);
            contentPane.getChildren().setAll(tradeStepView);
            tradeStepView.setNotificationGroup(notificationGroup);
            tradeStepView.activate();
        } catch (Exception e) {
            log.error("Creating viewClass {} caused an error {}", viewClass, e.getMessage());
            e.printStackTrace();
        }
    }

    private void addLeftBox() {
        leftVBox = new VBox();
        leftVBox.setSpacing(Layout.SPACING_V_BOX);
        leftVBox.setMinWidth(290);
        getChildren().add(leftVBox);
    }

    private void addContentPane() {
        contentPane = new AnchorPane();
        HBox.setHgrow(contentPane, Priority.SOMETIMES);
        getChildren().add(contentPane);
    }
}



