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

package bisq.desktop.main.dao.results;

import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.TableGroupHeadline;
import bisq.desktop.main.dao.results.model.ResultsOfCycle;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.geometry.Insets;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseResultsTableView<R> {
    protected final GridPane gridPane;
    protected final BsqWalletService bsqWalletService;
    protected final DaoFacade daoFacade;
    protected final BsqFormatter bsqFormatter;

    protected int gridRow;
    protected int gridRowStartIndex;


    protected final ObservableList<R> itemList = FXCollections.observableArrayList();
    private final SortedList<R> sortedList = new SortedList<>(itemList);
    protected ResultsOfCycle resultsOfCycle;
    protected TableView<R> tableView;

    protected abstract String getTitle();

    protected abstract void fillList();

    protected abstract void createColumns(TableView<R> tableView);

    public BaseResultsTableView(GridPane gridPane, BsqWalletService bsqWalletService, DaoFacade daoFacade, BsqFormatter bsqFormatter) {
        this.gridPane = gridPane;
        this.bsqWalletService = bsqWalletService;
        this.daoFacade = daoFacade;
        this.bsqFormatter = bsqFormatter;
    }

    public int createAllFields(int gridRowStartIndex, ResultsOfCycle resultsOfCycle) {
        this.resultsOfCycle = resultsOfCycle;
        this.gridRowStartIndex = gridRowStartIndex;
        this.gridRow = gridRowStartIndex;

        removeAllFields();
        createTableView();
        fillList();
        GUIUtil.setFitToRowsForTableView(tableView, 33, 28, 80);

        return gridRow;
    }

    private void createTableView() {
        TableGroupHeadline headline = new TableGroupHeadline(getTitle());
        GridPane.setRowIndex(headline, gridRow);
        GridPane.setMargin(headline, new Insets(15, -10, -10, -10));
        GridPane.setColumnSpan(headline, 2);
        gridPane.getChildren().add(headline);

        tableView = new TableView<>();
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        createColumns(tableView);
        GridPane.setRowIndex(tableView, gridRow);
        GridPane.setMargin(tableView, new Insets(35, -10, 5, -10));
        GridPane.setColumnSpan(tableView, 2);
        GridPane.setHgrow(tableView, Priority.SOMETIMES);
        gridPane.getChildren().add(tableView);

        tableView.setItems(sortedList);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
    }

    private void removeAllFields() {
        GUIUtil.removeChildrenFromGridPaneRows(gridPane, gridRowStartIndex, gridRow);
        gridRow = gridRowStartIndex;
    }
}
