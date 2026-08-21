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

import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.main.portfolio.closedtrades.ClosedTradesViewModel;
import bisq.desktop.util.Layout;

import bisq.core.locale.Res;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import javafx.geometry.Insets;

import java.util.Map;

import static bisq.desktop.util.FormBuilder.addConfirmationLabelLabel;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

public class ClosedTradesSummaryWindow extends Overlay<ClosedTradesSummaryWindow> {
    private final ClosedTradesViewModel model;

    @Inject
    public ClosedTradesSummaryWindow(ClosedTradesViewModel model) {
        this.model = model;
        type = Type.Information;
    }

    public void show() {
        rowIndex = 0;
        width = 900;
        createGridPane();
        addContent();
        addButtons();
        display();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void createGridPane() {
        super.createGridPane();
        gridPane.setPadding(new Insets(35, 40, 30, 40));
        gridPane.getStyleClass().add("grid-pane");
    }

    private void addContent() {
        Map<String, String> totalVolumeByCurrency = model.getTotalVolumeByCurrency();
        int rowSpan = totalVolumeByCurrency.size() + 4;
        addTitledGroupBg(gridPane, rowIndex, rowSpan, Res.get("closedTradesSummaryWindow.headline"));
        Coin totalTradeAmount = model.getTotalTradeAmount();
        addConfirmationLabelLabel(gridPane, rowIndex,
                Res.get("closedTradesSummaryWindow.totalAmount.title"),
                model.getTotalAmountWithVolume(totalTradeAmount), Layout.TWICE_FIRST_ROW_DISTANCE);
        totalVolumeByCurrency.entrySet().forEach(entry -> {
            addConfirmationLabelLabel(gridPane, ++rowIndex,
                    Res.get("closedTradesSummaryWindow.totalVolume.title", entry.getKey()), entry.getValue());
        });
        addConfirmationLabelLabel(gridPane, ++rowIndex,
                Res.get("closedTradesSummaryWindow.totalMinerFee.title"),
                model.getTotalTxFee(totalTradeAmount));
        addConfirmationLabelLabel(gridPane, ++rowIndex,
                Res.get("closedTradesSummaryWindow.totalTradeFeeInBtc.title"),
                model.getTotalTradeFeeInBtc(totalTradeAmount));
        addConfirmationLabelLabel(gridPane, ++rowIndex,
                Res.get("closedTradesSummaryWindow.totalTradeFeeInBsq.title") + "       ", // lets give some extra space
                model.getTotalTradeFeeInBsq(totalTradeAmount));
    }
}
