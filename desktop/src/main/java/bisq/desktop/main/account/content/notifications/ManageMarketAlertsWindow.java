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

package bisq.desktop.main.account.content.notifications;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.util.ImageUtil;

import bisq.core.locale.Res;
import bisq.core.notifications.alerts.market.MarketAlertFilter;
import bisq.core.notifications.alerts.market.MarketAlerts;
import bisq.core.util.FormattingUtils;

import bisq.common.UserThread;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;

import javafx.geometry.Insets;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.collections.FXCollections;

import javafx.util.Callback;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ManageMarketAlertsWindow extends Overlay<ManageMarketAlertsWindow> {

    private final MarketAlerts marketAlerts;

    ManageMarketAlertsWindow(MarketAlerts marketAlerts) {
        this.marketAlerts = marketAlerts;
        type = Type.Attention;
    }

    @Override
    public void show() {
        if (headLine == null)
            headLine = Res.get("account.notifications.marketAlert.manageAlerts.title");

        width = 968;
        createGridPane();
        addHeadLine();
        addContent();
        addButtons();
        applyStyles();
        display();
    }

    @Override
    protected void applyStyles() {
        super.applyStyles();
        gridPane.setId("popup-grid-pane-bg");
    }

    private void addContent() {
        TableView<MarketAlertFilter> tableView = new TableView<>();
        GridPane.setRowIndex(tableView, ++rowIndex);
        GridPane.setColumnSpan(tableView, 2);
        GridPane.setMargin(tableView, new Insets(10, 0, 0, 0));
        gridPane.getChildren().add(tableView);
        Label placeholder = new AutoTooltipLabel(Res.get("table.placeholder.noData"));
        placeholder.setWrapText(true);
        tableView.setPlaceholder(placeholder);
        tableView.setPrefHeight(300);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        setColumns(tableView);
        tableView.setItems(FXCollections.observableArrayList(marketAlerts.getMarketAlertFilters()));
    }

    private void removeMarketAlertFilter(MarketAlertFilter marketAlertFilter, TableView<MarketAlertFilter> tableView) {
        marketAlerts.removeMarketAlertFilter(marketAlertFilter);
        UserThread.execute(() -> tableView.setItems(FXCollections.observableArrayList(marketAlerts.getMarketAlertFilters())));
    }

    private void setColumns(TableView<MarketAlertFilter> tableView) {
        TableColumn<MarketAlertFilter, MarketAlertFilter> column;

        column = new AutoTooltipTableColumn<>(Res.get("account.notifications.marketAlert.manageAlerts.header.paymentAccount"));
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<MarketAlertFilter, MarketAlertFilter> call(TableColumn<MarketAlertFilter, MarketAlertFilter> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final MarketAlertFilter item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setText(item.getPaymentAccount().getAccountName());
                                } else {
                                    setText("");
                                }
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("account.notifications.marketAlert.manageAlerts.header.trigger"));
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<MarketAlertFilter, MarketAlertFilter> call(TableColumn<MarketAlertFilter, MarketAlertFilter> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final MarketAlertFilter item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setText(FormattingUtils.formatPercentagePrice(item.getTriggerValue() / 10000d));
                                } else {
                                    setText("");
                                }
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("account.notifications.marketAlert.manageAlerts.header.offerType"));
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<MarketAlertFilter, MarketAlertFilter> call(TableColumn<MarketAlertFilter, MarketAlertFilter> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final MarketAlertFilter item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setText(item.isBuyOffer() ? Res.get("shared.buyBitcoin") : Res.get("shared.sellBitcoin"));
                                } else {
                                    setText("");
                                }
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);

        column = new TableColumn<>();
        column.setMinWidth(40);
        column.setMaxWidth(column.getMinWidth());
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<MarketAlertFilter, MarketAlertFilter> call(TableColumn<MarketAlertFilter, MarketAlertFilter> column) {
                        return new TableCell<>() {
                            final ImageView icon = ImageUtil.getImageViewById(ImageUtil.REMOVE_ICON);
                            final Button removeButton = new AutoTooltipButton("", icon);

                            {
                                removeButton.setId("icon-button");
                                removeButton.setTooltip(new Tooltip(Res.get("shared.remove")));
                            }

                            @Override
                            public void updateItem(final MarketAlertFilter item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    removeButton.setOnAction(e -> removeMarketAlertFilter(item, tableView));
                                    setGraphic(removeButton);
                                } else {
                                    setGraphic(null);
                                    removeButton.setOnAction(null);
                                }
                            }
                        };
                    }
                });

        tableView.getColumns().add(column);
    }
}
