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

package bisq.desktop.main.dao.compensation.past;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.SeparatedPhaseBars;
import bisq.desktop.main.dao.compensation.CompensationRequestDisplay;
import bisq.desktop.main.dao.compensation.CompensationRequestView;
import bisq.desktop.util.BsqFormatter;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoPeriodService;
import bisq.core.dao.blockchain.BsqBlockChain;
import bisq.core.dao.blockchain.BsqBlockChainChangeDispatcher;
import bisq.core.dao.blockchain.BsqBlockChainListener;
import bisq.core.dao.request.compensation.CompensationRequestManager;

import bisq.common.locale.Res;

import javax.inject.Inject;

import javafx.scene.control.TableView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;

import javafx.geometry.Insets;

import java.util.List;

@FxmlView
public class PastCompensationRequestView extends CompensationRequestView implements BsqBlockChainListener {

    private List<SeparatedPhaseBars.SeparatedPhaseBarsItem> phaseBarsItems;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private PastCompensationRequestView(CompensationRequestManager compensationRequestManger,
                                        DaoPeriodService daoPeriodService,
                                        BsqWalletService bsqWalletService,
                                        BsqBlockChain bsqBlockChain,
                                        BsqBlockChainChangeDispatcher bsqBlockChainChangeDispatcher,
                                        BsqFormatter bsqFormatter) {
        super(compensationRequestManger, bsqWalletService, bsqBlockChain, bsqBlockChainChangeDispatcher, bsqFormatter);
    }

    @Override
    public void initialize() {
        root.getStyleClass().add("compensation-root");
        AnchorPane topAnchorPane = new AnchorPane();
        root.getChildren().add(topAnchorPane);

        gridPane = new GridPane();
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        AnchorPane.setBottomAnchor(gridPane, 10d);
        AnchorPane.setRightAnchor(gridPane, 10d);
        AnchorPane.setLeftAnchor(gridPane, 10d);
        AnchorPane.setTopAnchor(gridPane, 0d);
        topAnchorPane.getChildren().add(gridPane);

        // Add compensationrequest pane
        tableView = new TableView<>();
        detailsGridPane = new GridPane();
        compensationRequestDisplay = new CompensationRequestDisplay(detailsGridPane, bsqFormatter, bsqWalletService, null);
        compensationRequestPane = compensationRequestDisplay.createCompensationRequestPane(tableView, Res.get("dao.compensation.past.header"));
        compensationRequestPane.setMinWidth(800);
        GridPane.setColumnSpan(compensationRequestPane, 2);
        GridPane.setColumnIndex(compensationRequestPane, 0);
        GridPane.setMargin(compensationRequestPane, new Insets(0, -10, 0, -10));
        GridPane.setRowIndex(compensationRequestPane, gridRow);

        gridPane.getChildren().add(compensationRequestPane);

        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);

        compensationRequestListChangeListener = c -> updateList();
        chainHeightChangeListener = (observable, oldValue, newValue) -> {
            updateList();
        };
    }

    @Override
    protected void updateList() {
        doUpdateList(compensationRequestManger.getPastRequests());
    }
}

