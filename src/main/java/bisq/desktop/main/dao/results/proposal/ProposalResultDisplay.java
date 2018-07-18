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

package bisq.desktop.main.dao.results.proposal;

import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.TableGroupHeadline;
import bisq.desktop.main.dao.proposal.ProposalDetailsWindow;
import bisq.desktop.main.dao.results.model.ResultsOfCycle;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.voting.proposal.ProposalType;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import org.bitcoinj.core.Coin;

import de.jensd.fx.fontawesome.AwesomeDude;

import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.geometry.HPos;
import javafx.geometry.Insets;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProposalResultDisplay {
    private final GridPane gridPane;
    private final BsqWalletService bsqWalletService;
    private final DaoFacade daoFacade;
    private final BsqFormatter bsqFormatter;

    private int gridRow;
    private int gridRowStartIndex;


    private final ObservableList<ProposalResultListItem> itemList = FXCollections.observableArrayList();
    private final SortedList<ProposalResultListItem> sortedList = new SortedList<>(itemList);


    public ProposalResultDisplay(GridPane gridPane, BsqWalletService bsqWalletService, DaoFacade daoFacade, BsqFormatter bsqFormatter) {
        this.gridPane = gridPane;
        this.bsqWalletService = bsqWalletService;
        this.daoFacade = daoFacade;
        this.bsqFormatter = bsqFormatter;
    }

    public void createAllFields(int gridRowStartIndex, ResultsOfCycle resultsOfCycle) {
        removeAllFields();

        this.gridRowStartIndex = gridRowStartIndex;
        this.gridRow = gridRowStartIndex;

        createTableView();

        itemList.setAll(resultsOfCycle.getEvaluatedProposals().stream().map(e -> new ProposalResultListItem(e, bsqFormatter)).collect(Collectors.toList()));

        Map<ProposalType, Long> requiredThresholdByType = new HashMap<>();
        resultsOfCycle.getEvaluatedProposals().forEach(e -> {
            requiredThresholdByType.putIfAbsent(e.getProposal().getType(), e.getRequiredThreshold());
        });
        Map<ProposalType, Long> requiredQuorumByType = new HashMap<>();
        resultsOfCycle.getEvaluatedProposals().forEach(e -> {
            requiredQuorumByType.putIfAbsent(e.getProposal().getType(), e.getRequiredQuorum());
        });

        requiredThresholdByType.forEach((key, value) -> {
            String title = Res.get("dao.results.result.requiredThreshold." + key.name());
            String requiredThreshold = String.valueOf(value / 100) + "%";
            FormBuilder.addLabelTextField(gridPane, ++gridRow, title, requiredThreshold, 0);
        });
        requiredQuorumByType.forEach((key, value) -> {
            String title = Res.get("dao.results.result.requiredQuorum." + key.name());
            String requiredQuorum = bsqFormatter.formatCoinWithCode(Coin.valueOf(value));
            FormBuilder.addLabelTextField(gridPane, ++gridRow, title, requiredQuorum);
        });
    }

    private void createTableView() {
        TableGroupHeadline headline = new TableGroupHeadline(Res.get("dao.results.result.header"));
        GridPane.setRowIndex(headline, gridRow);
        GridPane.setMargin(headline, new Insets(0, -10, -10, -10));
        GridPane.setColumnSpan(headline, 2);
        gridPane.getChildren().add(headline);

        TableView<ProposalResultListItem> tableView = new TableView<>();
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPrefHeight(200);

        createColumns(tableView);
        GridPane.setRowIndex(tableView, gridRow);
        GridPane.setMargin(tableView, new Insets(20, -10, 5, -10));
        GridPane.setColumnSpan(tableView, 2);
        GridPane.setHgrow(tableView, Priority.ALWAYS);
        gridPane.getChildren().add(tableView);

        tableView.setItems(sortedList);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
    }

    private void createColumns(TableView<ProposalResultListItem> tableView) {
        TableColumn<ProposalResultListItem, ProposalResultListItem> proposalColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.result.table.header.proposalOwnerName"));
        proposalColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        proposalColumn.setCellFactory(
                new Callback<TableColumn<ProposalResultListItem, ProposalResultListItem>, TableCell<ProposalResultListItem,
                        ProposalResultListItem>>() {
                    @Override
                    public TableCell<ProposalResultListItem, ProposalResultListItem> call(
                            TableColumn<ProposalResultListItem, ProposalResultListItem> column) {
                        return new TableCell<ProposalResultListItem, ProposalResultListItem>() {
                            @Override
                            public void updateItem(final ProposalResultListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getProposalOwnerName());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        proposalColumn.setComparator(Comparator.comparing(ProposalResultListItem::getProposalOwnerName));
        tableView.getColumns().add(proposalColumn);

        TableColumn<ProposalResultListItem, ProposalResultListItem> proposalIdColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.result.table.header.proposalId"));
        proposalIdColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));

        proposalIdColumn.setCellFactory(
                new Callback<TableColumn<ProposalResultListItem, ProposalResultListItem>, TableCell<ProposalResultListItem, ProposalResultListItem>>() {

                    @Override
                    public TableCell<ProposalResultListItem, ProposalResultListItem> call(TableColumn<ProposalResultListItem,
                            ProposalResultListItem> column) {
                        return new TableCell<ProposalResultListItem, ProposalResultListItem>() {
                            private Hyperlink field;

                            @Override
                            public void updateItem(final ProposalResultListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                // cycleDetailsWindow.show(item.getEvaluatedProposal())
                                if (item != null && !empty) {
                                    field = new Hyperlink(item.getProposalId());

                                    //TODO setId or getStyleClass.add does not apply color...
                                    //field.getStyleClass().add(item.getColorStyleClass());
                                    //field.setId(item.getColorStyleClass());
                                    field.setStyle(item.getColorStyle());
                                    field.setOnAction(event -> new ProposalDetailsWindow(bsqFormatter,
                                            bsqWalletService,
                                            item.getEvaluatedProposal().getProposal(),
                                            daoFacade)
                                            .show());
                                    field.setTooltip(new Tooltip(Res.get("tooltip.openPopupForDetails")));
                                    setGraphic(field);
                                } else {
                                    setGraphic(null);
                                    if (field != null)
                                        field.setOnAction(null);
                                }
                            }
                        };
                    }
                });
        proposalIdColumn.setComparator(Comparator.comparing(ProposalResultListItem::getProposalOwnerName));
        tableView.getColumns().add(proposalIdColumn);

        TableColumn<ProposalResultListItem, ProposalResultListItem> acceptedColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.result.table.header.accepted"));
        acceptedColumn.setMinWidth(80);
        acceptedColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        acceptedColumn.setCellFactory(
                new Callback<TableColumn<ProposalResultListItem, ProposalResultListItem>, TableCell<ProposalResultListItem,
                        ProposalResultListItem>>() {
                    private Hyperlink field;

                    @Override
                    public TableCell<ProposalResultListItem, ProposalResultListItem> call(
                            TableColumn<ProposalResultListItem, ProposalResultListItem> column) {
                        return new TableCell<ProposalResultListItem, ProposalResultListItem>() {
                            @Override
                            public void updateItem(final ProposalResultListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    field = new Hyperlink(item.getAccepted());
                                    field.setStyle(item.getColorStyle());
                                    field.setOnAction(event -> new ProposalResultDetailsWindow(bsqFormatter, item.getEvaluatedProposal()).show());
                                    field.setTooltip(new Tooltip(Res.get("tooltip.openPopupForDetails")));
                                    setGraphic(field);
                                } else {
                                    setGraphic(null);

                                    //TODO does get called on active items somehow...
                                    //if (field != null)
                                    //    field.setOnAction(null);
                                }
                            }
                        };
                    }
                });
        acceptedColumn.setComparator(Comparator.comparing(ProposalResultListItem::getAccepted));
        tableView.getColumns().add(acceptedColumn);

        TableColumn<ProposalResultListItem, ProposalResultListItem> rejectedColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.result.table.header.rejected"));
        rejectedColumn.setMinWidth(80);
        rejectedColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        rejectedColumn.setCellFactory(
                new Callback<TableColumn<ProposalResultListItem, ProposalResultListItem>, TableCell<ProposalResultListItem,
                        ProposalResultListItem>>() {
                    private Hyperlink field;

                    @Override
                    public TableCell<ProposalResultListItem, ProposalResultListItem> call(
                            TableColumn<ProposalResultListItem, ProposalResultListItem> column) {
                        return new TableCell<ProposalResultListItem, ProposalResultListItem>() {
                            @Override
                            public void updateItem(final ProposalResultListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    field = new Hyperlink(item.getRejected());
                                    field.setStyle(item.getColorStyle());
                                    field.setOnAction(event -> new ProposalResultDetailsWindow(bsqFormatter, item.getEvaluatedProposal()).show());
                                    field.setTooltip(new Tooltip(Res.get("tooltip.openPopupForDetails")));
                                    setGraphic(field);
                                } else {
                                    setGraphic(null);
                                    //TODO does get called on active items somehow...
                                    //if (field != null)
                                    //    field.setOnAction(null);
                                }
                            }
                        };
                    }
                });
        rejectedColumn.setComparator(Comparator.comparing(ProposalResultListItem::getRejected));
        tableView.getColumns().add(rejectedColumn);

        TableColumn<ProposalResultListItem, ProposalResultListItem> thresholdColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.result.table.header.threshold"));
        thresholdColumn.setMinWidth(100);
        thresholdColumn.setMaxWidth(100);
        thresholdColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        thresholdColumn.setCellFactory(
                new Callback<TableColumn<ProposalResultListItem, ProposalResultListItem>, TableCell<ProposalResultListItem,
                        ProposalResultListItem>>() {
                    @Override
                    public TableCell<ProposalResultListItem, ProposalResultListItem> call(
                            TableColumn<ProposalResultListItem, ProposalResultListItem> column) {
                        return new TableCell<ProposalResultListItem, ProposalResultListItem>() {
                            @Override
                            public void updateItem(final ProposalResultListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getThreshold());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        thresholdColumn.setComparator(Comparator.comparing(ProposalResultListItem::getThreshold));
        tableView.getColumns().add(thresholdColumn);

        TableColumn<ProposalResultListItem, ProposalResultListItem> quorumColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.result.table.header.quorum"));
        quorumColumn.setMinWidth(130);
        quorumColumn.setMaxWidth(130);
        quorumColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        quorumColumn.setCellFactory(
                new Callback<TableColumn<ProposalResultListItem, ProposalResultListItem>, TableCell<ProposalResultListItem,
                        ProposalResultListItem>>() {
                    @Override
                    public TableCell<ProposalResultListItem, ProposalResultListItem> call(
                            TableColumn<ProposalResultListItem, ProposalResultListItem> column) {
                        return new TableCell<ProposalResultListItem, ProposalResultListItem>() {
                            @Override
                            public void updateItem(final ProposalResultListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getQuorum());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        quorumColumn.setComparator(Comparator.comparing(ProposalResultListItem::getThreshold));
        tableView.getColumns().add(quorumColumn);

        TableColumn<ProposalResultListItem, ProposalResultListItem> issuanceColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.result.table.header.issuance"));
        issuanceColumn.setMinWidth(130);
        issuanceColumn.setMaxWidth(130);
        issuanceColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        issuanceColumn.setCellFactory(
                new Callback<TableColumn<ProposalResultListItem, ProposalResultListItem>, TableCell<ProposalResultListItem,
                        ProposalResultListItem>>() {
                    @Override
                    public TableCell<ProposalResultListItem, ProposalResultListItem> call(
                            TableColumn<ProposalResultListItem, ProposalResultListItem> column) {
                        return new TableCell<ProposalResultListItem, ProposalResultListItem>() {
                            @Override
                            public void updateItem(final ProposalResultListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getIssuance());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        issuanceColumn.setComparator(Comparator.comparing(ProposalResultListItem::getThreshold));
        tableView.getColumns().add(issuanceColumn);


        TableColumn<ProposalResultListItem, ProposalResultListItem> resultColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.result.table.header.result"));
        resultColumn.setMinWidth(60);
        resultColumn.setMaxWidth(60);
        resultColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        resultColumn.setCellFactory(new Callback<TableColumn<ProposalResultListItem, ProposalResultListItem>,
                TableCell<ProposalResultListItem, ProposalResultListItem>>() {

            @Override
            public TableCell<ProposalResultListItem, ProposalResultListItem> call(TableColumn<ProposalResultListItem,
                    ProposalResultListItem> column) {
                return new TableCell<ProposalResultListItem, ProposalResultListItem>() {
                    Label icon;

                    @Override
                    public void updateItem(final ProposalResultListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            icon = new Label();
                            AwesomeDude.setIcon(icon, item.getIcon());
                            icon.getStyleClass().add(item.getColorStyleClass());
                            setGraphic(icon);
                        } else {
                            setGraphic(null);
                            if (icon != null)
                                icon = null;
                        }
                    }
                };
            }
        });


        resultColumn.setComparator(Comparator.comparing(ProposalResultListItem::getThreshold));
        tableView.getColumns().add(resultColumn);
    }

    public void removeAllFields() {
        GUIUtil.removeChildrenFromGridPaneRows(gridPane, gridRowStartIndex, gridRow);
        gridRow = gridRowStartIndex;
    }

    @SuppressWarnings("Duplicates")
    public ScrollPane getView() {
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setMinHeight(280); // just enough to display overview at voting without scroller

        AnchorPane anchorPane = new AnchorPane();
        scrollPane.setContent(anchorPane);

        gridPane.setHgap(5);
        gridPane.setVgap(5);
        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHalignment(HPos.RIGHT);
        columnConstraints1.setHgrow(Priority.SOMETIMES);
        columnConstraints1.setMinWidth(140);
        ColumnConstraints columnConstraints2 = new ColumnConstraints();
        columnConstraints2.setHgrow(Priority.ALWAYS);
        columnConstraints2.setMinWidth(300);

        gridPane.getColumnConstraints().addAll(columnConstraints1, columnConstraints2);
        AnchorPane.setBottomAnchor(gridPane, 20d);
        AnchorPane.setRightAnchor(gridPane, 10d);
        AnchorPane.setLeftAnchor(gridPane, 10d);
        AnchorPane.setTopAnchor(gridPane, 20d);
        anchorPane.getChildren().add(gridPane);

        return scrollPane;
    }
}
