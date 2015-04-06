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

import io.bitsquare.gui.main.portfolio.pendingtrades.steps.TradeStepDetailsView;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.TradeWizardItem;
import io.bitsquare.gui.util.Layout;

import javafx.beans.value.ChangeListener;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TradeSubView extends HBox {
    private static final Logger log = LoggerFactory.getLogger(TradeSubView.class);

    protected final PendingTradesViewModel model;
    protected final ChangeListener<PendingTradesViewModel.ViewState> offererStateChangeListener;
    protected VBox leftVBox;
    protected AnchorPane contentPane;
    protected TradeStepDetailsView tradeStepDetailsView;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TradeSubView(PendingTradesViewModel model) {
        this.model = model;

        setSpacing(Layout.PADDING_WINDOW);
        buildViews();

        offererStateChangeListener = (ov, oldValue, newValue) -> applyState(newValue);
    }

    public void activate() {
        model.getViewState().addListener(offererStateChangeListener);
        applyState(model.getViewState().get());
    }

    public void deactivate() {
        model.getViewState().removeListener(offererStateChangeListener);

        if (tradeStepDetailsView != null)
            tradeStepDetailsView.deactivate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Misc
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract void applyState(PendingTradesViewModel.ViewState state);

    private void buildViews() {
        addLeftBox();
        addContentPane();
        addWizards();
    }

    protected void showItem(TradeWizardItem item) {
        item.active();
        createAndAddTradeStepView(item.getViewClass());
    }

    abstract protected void addWizards();

    private void createAndAddTradeStepView(Class<? extends TradeStepDetailsView> viewClass) {
        try {
            tradeStepDetailsView = viewClass.getDeclaredConstructor(PendingTradesViewModel.class).newInstance(model);
        } catch (Exception e) {
            e.printStackTrace();
        }

        contentPane.getChildren().setAll(tradeStepDetailsView);
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



