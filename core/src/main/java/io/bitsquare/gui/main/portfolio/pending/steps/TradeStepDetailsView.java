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

package io.bitsquare.gui.main.portfolio.pending.steps;

import io.bitsquare.gui.main.help.Help;
import io.bitsquare.gui.main.help.HelpId;
import io.bitsquare.gui.main.portfolio.pending.PendingTradesViewModel;

import javafx.event.ActionEvent;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.ComponentBuilder.getAndAddGridPane;

public abstract class TradeStepDetailsView extends AnchorPane {
    private static final Logger log = LoggerFactory.getLogger(TradeStepDetailsView.class);

    protected final PendingTradesViewModel model;
    protected final GridPane gridPane;
    protected int gridRow = 0;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TradeStepDetailsView(PendingTradesViewModel model) {
        this.model = model;

        AnchorPane.setLeftAnchor(this, 0d);
        AnchorPane.setRightAnchor(this, 0d);
        AnchorPane.setTopAnchor(this, -10d);
        AnchorPane.setBottomAnchor(this, 0d);

        gridPane = getAndAddGridPane(this);

        buildGridEntries();
    }

    // That is called at every state change!
    public void activate() {
    }

    public void deactivate() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void onOpenHelp(ActionEvent actionEvent) {
        log.debug("onOpenHelp");
        Help.openWindow(model.isOfferer() ? HelpId.PENDING_TRADE_OFFERER : HelpId.PENDING_TRADE_TAKER);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Build view
    ///////////////////////////////////////////////////////////////////////////////////////////

    abstract void buildGridEntries();
}
