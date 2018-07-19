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

package bisq.desktop.main.dao.results.proposals;

import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.main.dao.proposal.ProposalDetailsWindow;
import bisq.desktop.main.dao.results.BaseResultsTableView;
import bisq.desktop.main.dao.results.ProposalResultsDetailsWindow;
import bisq.desktop.main.dao.results.model.ResultsOfCycle;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import de.jensd.fx.fontawesome.AwesomeDude;

import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.util.Callback;

import java.util.Comparator;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProposalResultsTableView extends BaseResultsTableView<ProposalResultsListItem> {

    public ProposalResultsTableView(GridPane gridPane, BsqWalletService bsqWalletService, DaoFacade daoFacade, BsqFormatter bsqFormatter) {
        super(gridPane, bsqWalletService, daoFacade, bsqFormatter);
    }

    @Override
    protected String getTitle() {
        return Res.get("dao.results.proposals.header");
    }

    @Override
    protected void fillList() {
        itemList.setAll(resultsOfCycle.getEvaluatedProposals().stream()
                .map(e -> new ProposalResultsListItem(e, bsqFormatter))
                .collect(Collectors.toList()));

        itemList.sort(Comparator.comparing(proposalResultsListItem -> proposalResultsListItem.getEvaluatedProposal().getProposal().getCreationDate()));
    }

    public int createAllFields(int gridRowStartIndex, ResultsOfCycle resultsOfCycle) {
        super.createAllFields(gridRowStartIndex, resultsOfCycle);

        GUIUtil.setFitToRowsForTableView(tableView, 30, 28, 80);

        return gridRow;
    }

    @Override
    protected void createColumns(TableView<ProposalResultsListItem> tableView) {
        TableColumn<ProposalResultsListItem, ProposalResultsListItem> nameColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.proposals.table.header.proposalOwnerName"));
        nameColumn.setMinWidth(100);
        nameColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        nameColumn.setCellFactory(
                new Callback<TableColumn<ProposalResultsListItem, ProposalResultsListItem>, TableCell<ProposalResultsListItem,
                        ProposalResultsListItem>>() {
                    @Override
                    public TableCell<ProposalResultsListItem, ProposalResultsListItem> call(
                            TableColumn<ProposalResultsListItem, ProposalResultsListItem> column) {
                        return new TableCell<ProposalResultsListItem, ProposalResultsListItem>() {
                            @Override
                            public void updateItem(final ProposalResultsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getProposalOwnerName());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        nameColumn.setComparator(Comparator.comparing(ProposalResultsListItem::getProposalOwnerName));
        tableView.getColumns().add(nameColumn);

        TableColumn<ProposalResultsListItem, ProposalResultsListItem> idColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.proposals.table.header.proposalId"));
        idColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        idColumn.setMinWidth(100);
        idColumn.setCellFactory(
                new Callback<TableColumn<ProposalResultsListItem, ProposalResultsListItem>, TableCell<ProposalResultsListItem, ProposalResultsListItem>>() {

                    @Override
                    public TableCell<ProposalResultsListItem, ProposalResultsListItem> call(TableColumn<ProposalResultsListItem,
                            ProposalResultsListItem> column) {
                        return new TableCell<ProposalResultsListItem, ProposalResultsListItem>() {
                            private Hyperlink field;

                            @Override
                            public void updateItem(final ProposalResultsListItem item, boolean empty) {
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
        idColumn.setComparator(Comparator.comparing(ProposalResultsListItem::getProposalOwnerName));
        tableView.getColumns().add(idColumn);

        TableColumn<ProposalResultsListItem, ProposalResultsListItem> acceptedColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.proposals.table.header.accepted"));
        acceptedColumn.setMinWidth(80);
        acceptedColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        acceptedColumn.setCellFactory(
                new Callback<TableColumn<ProposalResultsListItem, ProposalResultsListItem>, TableCell<ProposalResultsListItem,
                        ProposalResultsListItem>>() {
                    private Hyperlink field;

                    @Override
                    public TableCell<ProposalResultsListItem, ProposalResultsListItem> call(
                            TableColumn<ProposalResultsListItem, ProposalResultsListItem> column) {
                        return new TableCell<ProposalResultsListItem, ProposalResultsListItem>() {
                            @Override
                            public void updateItem(final ProposalResultsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    field = new Hyperlink(item.getAccepted());
                                    field.setStyle(item.getColorStyle());
                                    field.setOnAction(event -> new ProposalResultsDetailsWindow(bsqFormatter, item.getEvaluatedProposal()).show());
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
        acceptedColumn.setComparator(Comparator.comparing(ProposalResultsListItem::getAccepted));
        tableView.getColumns().add(acceptedColumn);

        TableColumn<ProposalResultsListItem, ProposalResultsListItem> rejectedColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.proposals.table.header.rejected"));
        rejectedColumn.setMinWidth(80);
        rejectedColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        rejectedColumn.setCellFactory(
                new Callback<TableColumn<ProposalResultsListItem, ProposalResultsListItem>, TableCell<ProposalResultsListItem,
                        ProposalResultsListItem>>() {
                    private Hyperlink field;

                    @Override
                    public TableCell<ProposalResultsListItem, ProposalResultsListItem> call(
                            TableColumn<ProposalResultsListItem, ProposalResultsListItem> column) {
                        return new TableCell<ProposalResultsListItem, ProposalResultsListItem>() {
                            @Override
                            public void updateItem(final ProposalResultsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    field = new Hyperlink(item.getRejected());
                                    field.setStyle(item.getColorStyle());
                                    field.setOnAction(event -> new ProposalResultsDetailsWindow(bsqFormatter, item.getEvaluatedProposal()).show());
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
        rejectedColumn.setComparator(Comparator.comparing(ProposalResultsListItem::getRejected));
        tableView.getColumns().add(rejectedColumn);

        TableColumn<ProposalResultsListItem, ProposalResultsListItem> thresholdColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.proposals.table.header.threshold"));
        thresholdColumn.setMinWidth(100);
        thresholdColumn.setMaxWidth(100);
        thresholdColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        thresholdColumn.setCellFactory(
                new Callback<TableColumn<ProposalResultsListItem, ProposalResultsListItem>, TableCell<ProposalResultsListItem,
                        ProposalResultsListItem>>() {
                    @Override
                    public TableCell<ProposalResultsListItem, ProposalResultsListItem> call(
                            TableColumn<ProposalResultsListItem, ProposalResultsListItem> column) {
                        return new TableCell<ProposalResultsListItem, ProposalResultsListItem>() {
                            @Override
                            public void updateItem(final ProposalResultsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getThreshold());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        thresholdColumn.setComparator(Comparator.comparing(ProposalResultsListItem::getThreshold));
        tableView.getColumns().add(thresholdColumn);

        TableColumn<ProposalResultsListItem, ProposalResultsListItem> quorumColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.proposals.table.header.quorum"));
        quorumColumn.setMinWidth(130);
        quorumColumn.setMaxWidth(130);
        quorumColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        quorumColumn.setCellFactory(
                new Callback<TableColumn<ProposalResultsListItem, ProposalResultsListItem>, TableCell<ProposalResultsListItem,
                        ProposalResultsListItem>>() {
                    @Override
                    public TableCell<ProposalResultsListItem, ProposalResultsListItem> call(
                            TableColumn<ProposalResultsListItem, ProposalResultsListItem> column) {
                        return new TableCell<ProposalResultsListItem, ProposalResultsListItem>() {
                            @Override
                            public void updateItem(final ProposalResultsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getQuorum());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        quorumColumn.setComparator(Comparator.comparing(ProposalResultsListItem::getThreshold));
        tableView.getColumns().add(quorumColumn);

        TableColumn<ProposalResultsListItem, ProposalResultsListItem> issuanceColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.proposals.table.header.issuance"));
        issuanceColumn.setMinWidth(130);
        issuanceColumn.setMaxWidth(130);
        issuanceColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        issuanceColumn.setCellFactory(
                new Callback<TableColumn<ProposalResultsListItem, ProposalResultsListItem>, TableCell<ProposalResultsListItem,
                        ProposalResultsListItem>>() {
                    @Override
                    public TableCell<ProposalResultsListItem, ProposalResultsListItem> call(
                            TableColumn<ProposalResultsListItem, ProposalResultsListItem> column) {
                        return new TableCell<ProposalResultsListItem, ProposalResultsListItem>() {
                            @Override
                            public void updateItem(final ProposalResultsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getIssuance());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        issuanceColumn.setComparator(Comparator.comparing(ProposalResultsListItem::getThreshold));
        tableView.getColumns().add(issuanceColumn);

        TableColumn<ProposalResultsListItem, ProposalResultsListItem> resultColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.proposals.table.header.result"));
        resultColumn.setMinWidth(60);
        resultColumn.setMaxWidth(60);
        resultColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        resultColumn.setCellFactory(new Callback<TableColumn<ProposalResultsListItem, ProposalResultsListItem>,
                TableCell<ProposalResultsListItem, ProposalResultsListItem>>() {
            @Override
            public TableCell<ProposalResultsListItem, ProposalResultsListItem> call(TableColumn<ProposalResultsListItem,
                    ProposalResultsListItem> column) {
                return new TableCell<ProposalResultsListItem, ProposalResultsListItem>() {
                    Label icon;

                    @Override
                    public void updateItem(final ProposalResultsListItem item, boolean empty) {
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

        resultColumn.setComparator(Comparator.comparing(ProposalResultsListItem::getThreshold));
        tableView.getColumns().add(resultColumn);
    }
}
