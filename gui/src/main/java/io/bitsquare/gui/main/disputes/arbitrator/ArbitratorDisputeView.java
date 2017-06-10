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

package io.bitsquare.gui.main.disputes.arbitrator;

import io.bitsquare.alert.PrivateNotificationManager;
import io.bitsquare.arbitration.DisputeManager;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.disputes.trader.TraderDisputeView;
import io.bitsquare.gui.main.overlays.windows.ContractWindow;
import io.bitsquare.gui.main.overlays.windows.DisputeSummaryWindow;
import io.bitsquare.gui.main.overlays.windows.TradeDetailsWindow;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.trade.TradeManager;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.stage.Stage;

import javax.inject.Inject;

// will be probably only used for arbitration communication, will be renamed and the icon changed
@FxmlView
public class ArbitratorDisputeView extends TraderDisputeView {

    @FXML
    Insets rootPadding;

    @Inject
    public ArbitratorDisputeView(DisputeManager disputeManager, KeyRing keyRing, TradeManager tradeManager, Stage stage,
                                 BSFormatter formatter, DisputeSummaryWindow disputeSummaryWindow, PrivateNotificationManager privateNotificationManager,
                                 ContractWindow contractWindow, TradeDetailsWindow tradeDetailsWindow, P2PService p2PService) {
        super(disputeManager, keyRing, tradeManager, stage, formatter,
                disputeSummaryWindow, privateNotificationManager, contractWindow, tradeDetailsWindow, p2PService);
    }

    @Override
    public void initialize() {
        super.initialize();
        rootPadding = new Insets(MainView.scale(10), MainView.scale(10), MainView.scale(0), MainView.scale(10));

        filterBox.setVisible(true);
        filterBox.setManaged(true);
    }

    @Override
    protected void applyFilteredListPredicate(String filterString) {
        // If in arbitrator view we must only display disputes where we are selected as arbitrator (must not receive others anyway)
        filteredList.setPredicate(dispute ->
                dispute.getArbitratorPubKeyRing().equals(keyRing.getPubKeyRing()) &&
                        (filterString.isEmpty() ||
                                (dispute.getId().contains(filterString) ||
                                        (!dispute.isClosed() && filterString.toLowerCase().equals("open")) ||
                                        formatter.formatDate(dispute.getOpeningDate()).contains(filterString)) ||
                                getBuyerOnionAddressColumnLabel(dispute).contains(filterString) ||
                                getSellerOnionAddressColumnLabel(dispute).contains(filterString)

                        ));
    }

}


