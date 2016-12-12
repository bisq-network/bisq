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

package io.bitsquare.gui.main.dao.proposals.active;

import io.bitsquare.dao.proposals.Proposal;
import io.bitsquare.dao.proposals.ProposalManager;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.components.TableGroupHeadline;
import io.bitsquare.gui.main.dao.proposals.ProposalDisplay;
import io.bitsquare.gui.util.BSFormatter;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.util.Callback;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javax.inject.Inject;

@FxmlView
public class ActiveProposalsView extends ActivatableView<SplitPane, Void> {

    TableView<Proposal> tableView;
    private InputTextField nameTextField, titleTextField, categoryTextField, descriptionTextField, linkTextField,
            startDateTextField, endDateTextField, requestedBTCTextField, btcAddressTextField;

    private final ProposalManager proposalManager;
    private final BSFormatter formatter;
    private SortedList<Proposal> sortedList;
    private Subscription selectedProposalSubscription;
    private ProposalDisplay proposalDisplay;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ActiveProposalsView(ProposalManager proposalManager, BSFormatter formatter) {
        this.proposalManager = proposalManager;
        this.formatter = formatter;
    }

    @Override
    public void initialize() {
        root.setDividerPositions(0.3, 0.7);
        root.setStyle("-fx-background-insets: 0, 0 0 0 0");
        AnchorPane topAnchorPane = new AnchorPane();
        root.getItems().add(topAnchorPane);

        GridPane gridPane = new GridPane();
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        AnchorPane.setBottomAnchor(gridPane, 10d);
        AnchorPane.setRightAnchor(gridPane, 10d);
        AnchorPane.setLeftAnchor(gridPane, 10d);
        AnchorPane.setTopAnchor(gridPane, 10d);
        topAnchorPane.getChildren().add(gridPane);

        TableGroupHeadline header = new TableGroupHeadline("Active proposals");
        GridPane.setRowIndex(header, 0);
        GridPane.setMargin(header, new Insets(0, -10, -10, -10));
        gridPane.getChildren().add(header);
        header.setMinHeight(20);
        header.setMaxHeight(20);

        tableView = new TableView<>();
        GridPane.setRowIndex(tableView, 1);
        GridPane.setMargin(tableView, new Insets(5, -15, -10, -10));
        GridPane.setVgrow(tableView, Priority.ALWAYS);
        GridPane.setHgrow(tableView, Priority.ALWAYS);
        gridPane.getChildren().add(tableView);

        // tableView.setMinHeight(100);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new Label("No transactions available"));
        sortedList = new SortedList<>(proposalManager.getObservableProposalsList());
        tableView.setItems(sortedList);
        setColumns();
    }

    @Override
    protected void activate() {
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.getSelectionModel().clearSelection();
        selectedProposalSubscription = EasyBind.subscribe(tableView.getSelectionModel().selectedItemProperty(), this::onSelectProposal);
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();
        selectedProposalSubscription.unsubscribe();
    }

    private void onSelectProposal(Proposal proposal) {
        if (proposal != null) {
            if (proposalDisplay == null) {
                ScrollPane scrollPane = new ScrollPane();
                scrollPane.setFitToWidth(true);
                scrollPane.setFitToHeight(true);
                scrollPane.setMinHeight(100);
                root.getItems().add(scrollPane);

                AnchorPane bottomAnchorPane = new AnchorPane();
                scrollPane.setContent(bottomAnchorPane);

                GridPane gridPane = new GridPane();
                gridPane.setHgap(5);
                gridPane.setVgap(5);
                AnchorPane.setBottomAnchor(gridPane, 20d);
                AnchorPane.setRightAnchor(gridPane, -10d);
                AnchorPane.setLeftAnchor(gridPane, 10d);
                AnchorPane.setTopAnchor(gridPane, -20d);
                bottomAnchorPane.getChildren().add(gridPane);

                proposalDisplay = new ProposalDisplay(gridPane);
            }
            proposalDisplay.removeAllFields();
            proposalDisplay.createAllFields();

            proposalDisplay.fillWithProposalData(proposal);
        }
    }

    private void setColumns() {
        TableColumn<Proposal, Proposal> dateColumn = new TableColumn<Proposal, Proposal>("Date/Time") {
            {
                setMinWidth(190);
                setMaxWidth(190);
            }
        };
        dateColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        dateColumn.setCellFactory(
                new Callback<TableColumn<Proposal, Proposal>, TableCell<Proposal,
                        Proposal>>() {
                    @Override
                    public TableCell<Proposal, Proposal> call(
                            TableColumn<Proposal, Proposal> column) {
                        return new TableCell<Proposal, Proposal>() {
                            @Override
                            public void updateItem(final Proposal item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(formatter.formatDateTime(item.creationDate));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        dateColumn.setComparator((o1, o2) -> o1.creationDate.compareTo(o2.creationDate));
        tableView.getColumns().add(dateColumn);
        tableView.getSortOrder().add(dateColumn);


        TableColumn<Proposal, Proposal> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        nameColumn.setCellFactory(
                new Callback<TableColumn<Proposal, Proposal>, TableCell<Proposal,
                        Proposal>>() {
                    @Override
                    public TableCell<Proposal, Proposal> call(
                            TableColumn<Proposal, Proposal> column) {
                        return new TableCell<Proposal, Proposal>() {
                            @Override
                            public void updateItem(final Proposal item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.name);
                                else
                                    setText("");
                            }
                        };
                    }
                });
        nameColumn.setComparator((o1, o2) -> o1.name.compareTo(o2.name));
        tableView.getColumns().add(nameColumn);
    }
}

