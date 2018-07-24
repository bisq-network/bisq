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

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.geometry.Insets;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseResultsTableView1<R> {
    @Getter
    protected GridPane gridPane = new GridPane();
    protected final BsqWalletService bsqWalletService;
    protected final DaoFacade daoFacade;
    protected final BsqFormatter bsqFormatter;

    protected final ObservableList<R> itemList = FXCollections.observableArrayList();
    private final SortedList<R> sortedList = new SortedList<>(itemList);
    protected ResultsOfCycle resultsOfCycle;
    protected TableView<R> tableView;
    private TableGroupHeadline headline;

    protected abstract String getTitle();

    protected abstract void fillList();

    protected abstract void createColumns(TableView<R> tableView);

    public BaseResultsTableView1(BsqWalletService bsqWalletService, DaoFacade daoFacade,
                                 BsqFormatter bsqFormatter, int columnIndex) {
        this.bsqWalletService = bsqWalletService;
        this.daoFacade = daoFacade;
        this.bsqFormatter = bsqFormatter;

        headline = new TableGroupHeadline(getTitle());
        GridPane.setMargin(headline, new Insets(15, -10, -10, -10));
        GridPane.setColumnIndex(headline, columnIndex);
        gridPane.getChildren().add(headline);

        tableView = new TableView<>();
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        createColumns(tableView);
        GridPane.setMargin(tableView, new Insets(35, -10, 5, -10));
        GridPane.setColumnIndex(tableView, columnIndex);
        gridPane.getChildren().add(tableView);

        GridPane.setHgrow(headline, Priority.ALWAYS);
        GridPane.setHgrow(tableView, Priority.ALWAYS);

        tableView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<R>() {
            @Override
            public void changed(ObservableValue<? extends R> observable, R oldValue, R newValue) {
                onSelected(newValue);
            }
        });
    }

    protected abstract void onSelected(R item);

    public void createAllFields(ResultsOfCycle resultsOfCycle) {
        this.resultsOfCycle = resultsOfCycle;

        tableView.setItems(sortedList);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        fillList();
    }
}
