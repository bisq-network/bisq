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

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;
import static bisq.desktop.util.FormBuilder.addMultilineLabel;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@Slf4j
public abstract class TradeSubView extends HBox {
    protected final PendingTradesViewModel model;
    protected VBox leftVBox;
    private AnchorPane contentPane;
    private TradeStepView tradeStepView;
    protected TradeStepInfo tradeStepInfo;
    private GridPane leftGridPane;
    private TitledGroupBg tradeProcessTitledGroupBg;
    private int leftGridPaneRowIndex = 0;
    Subscription viewStateSubscription;


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

        if (tradeStepInfo != null)
            tradeStepInfo.removeItselfFrom(leftGridPane);
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

        TitledGroupBg titledGroupBg = addTitledGroupBg(leftGridPane, leftGridPaneRowIndex, 1, "", 30);
        titledGroupBg.getStyleClass().add("last");
        Label label = addMultilineLabel(leftGridPane, leftGridPaneRowIndex, "", 30);
        AutoTooltipButton button = (AutoTooltipButton) addButtonAfterGroup(leftGridPane, ++leftGridPaneRowIndex, "");
        tradeStepInfo = new TradeStepInfo(titledGroupBg, label, button);
    }

    void showItem(TradeWizardItem item) {
        item.setActive();
        createAndAddTradeStepView(item.getViewClass());
    }

    protected abstract void addWizards();

    protected void onViewStateChanged(PendingTradesViewModel.State viewState) {
        tradeStepInfo.setTrade(model.getTrade());
    }

    void addWizardsToGridPane(TradeWizardItem tradeWizardItem) {
        if (leftGridPaneRowIndex == 0)
            GridPane.setMargin(tradeWizardItem, new Insets(Layout.FIRST_ROW_DISTANCE + Layout.FLOATING_LABEL_DISTANCE, 0, 0, 0));

        GridPane.setRowIndex(tradeWizardItem, leftGridPaneRowIndex++);
        leftGridPane.getChildren().add(tradeWizardItem);
        GridPane.setRowSpan(tradeProcessTitledGroupBg, leftGridPaneRowIndex);
        GridPane.setFillWidth(tradeWizardItem, true);
    }

    void addLineSeparatorToGridPane() {
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
            tradeStepView.setTradeStepInfo(tradeStepInfo);
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



