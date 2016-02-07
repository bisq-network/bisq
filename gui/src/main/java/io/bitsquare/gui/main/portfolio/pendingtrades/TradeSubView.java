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

package io.bitsquare.gui.main.portfolio.pendingtrades;

import io.bitsquare.common.util.Tuple3;
import io.bitsquare.gui.components.TitledGroupBg;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.TradeStepView;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.TradeWizardItem;
import io.bitsquare.gui.util.Layout;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.FormBuilder.addMultilineLabel;
import static io.bitsquare.gui.util.FormBuilder.addTitledGroupBg;

public abstract class TradeSubView extends HBox {
    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    protected final PendingTradesViewModel model;
    protected VBox leftVBox;
    protected AnchorPane contentPane;
    protected TradeStepView tradeStepView;
    private Button openDisputeButton;
    private Tuple3<GridPane, TitledGroupBg, Label> notificationTuple;


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
        if (tradeStepView != null)
            tradeStepView.doDeactivate();

        if (openDisputeButton != null)
            leftVBox.getChildren().remove(openDisputeButton);
        if (notificationTuple != null)
            leftVBox.getChildren().remove(notificationTuple.first);
    }

    private void buildViews() {
        addLeftBox();
        addContentPane();
        addWizards();

        openDisputeButton = new Button("Open Dispute");
        openDisputeButton.setPrefHeight(40);
        openDisputeButton.setPrefWidth(360);
        openDisputeButton.setPadding(new Insets(0, 20, 0, 10));
        openDisputeButton.setAlignment(Pos.CENTER);
        openDisputeButton.setDefaultButton(true);
        openDisputeButton.setId("open-dispute-button");
        openDisputeButton.setVisible(false);
        openDisputeButton.setManaged(false);
        leftVBox.getChildren().add(openDisputeButton);
        VBox.setMargin(openDisputeButton, new Insets(10, 0, 0, 0));

        // notification fields
        GridPane gridPane = new GridPane();
        gridPane.setPrefWidth(340);
        VBox.setMargin(gridPane, new Insets(10, 10, 10, 10));
        gridPane.setHgap(Layout.GRID_GAP);
        gridPane.setVgap(Layout.GRID_GAP);
        gridPane.setVisible(false);
        gridPane.setManaged(false);
        leftVBox.getChildren().add(gridPane);

        TitledGroupBg titledGroupBg = addTitledGroupBg(gridPane, 0, 4, "Important notice", 20);
        Label label = addMultilineLabel(gridPane, 0, Layout.FIRST_ROW_DISTANCE + 20);
        notificationTuple = new Tuple3<>(gridPane, titledGroupBg, label);
    }

    protected void showItem(TradeWizardItem item) {
        item.setActive();
        createAndAddTradeStepView(item.getViewClass());
    }

    abstract protected void addWizards();

    private void createAndAddTradeStepView(Class<? extends TradeStepView> viewClass) {
        try {
            tradeStepView = viewClass.getDeclaredConstructor(PendingTradesViewModel.class).newInstance(model);
            contentPane.getChildren().setAll(tradeStepView);

            tradeStepView.setNotificationFields(notificationTuple);
            tradeStepView.setOpenDisputeButton(openDisputeButton);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addLeftBox() {
        leftVBox = new VBox();
        leftVBox.setSpacing(Layout.SPACING_VBOX);
        leftVBox.setMinWidth(290);
        getChildren().add(leftVBox);
    }

    private void addContentPane() {
        contentPane = new AnchorPane();
        HBox.setHgrow(contentPane, Priority.SOMETIMES);
        getChildren().add(contentPane);
    }
}



