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

package io.bitsquare.gui.main.funds.reserved;

import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.GUIUtil;
import io.bitsquare.trade.TradeManager;

import org.bitcoinj.core.Coin;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Callback;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

@FxmlView
public class ReservedView extends ActivatableViewAndModel {

    @FXML TableView<ReservedListItem> table;
    @FXML TableColumn<ReservedListItem, ReservedListItem> labelColumn, addressColumn, balanceColumn, copyColumn,
            confidenceColumn;

    private final WalletService walletService;
    private final TradeManager tradeManager;
    private final BSFormatter formatter;
    private final ObservableList<ReservedListItem> addressList = FXCollections.observableArrayList();

    @Inject
    private ReservedView(WalletService walletService, TradeManager tradeManager, BSFormatter formatter) {
        this.walletService = walletService;
        this.tradeManager = tradeManager;
        this.formatter = formatter;
    }


    @Override
    public void initialize() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No funded are reserved in open offers or trades"));

        setLabelColumnCellFactory();
        setBalanceColumnCellFactory();
        setCopyColumnCellFactory();
        setConfidenceColumnCellFactory();
    }

    @Override
    public void doActivate() {
        fillList();
        table.setItems(addressList);

        walletService.addBalanceListener(new BalanceListener() {
            @Override
            public void onBalanceChanged(Coin balance) {
                fillList();
            }
        });
    }

    @Override
    public void doDeactivate() {
        addressList.forEach(ReservedListItem::cleanup);
    }


    private void fillList() {
        addressList.clear();
        addressList.addAll(Stream.concat(tradeManager.getOpenOfferTrades().stream(), tradeManager.getPendingTrades().stream())
                .map(trade -> new ReservedListItem(walletService.getAddressEntry(trade.getId()), walletService, formatter))
                .collect(Collectors.toList()));

        // List<AddressEntry> addressEntryList = walletService.getAddressEntryList();
      /*  addressList.addAll(addressEntryList.stream()
                .filter(e -> walletService.getBalanceForAddress(e.getAddress()).isPositive())
                .map(anAddressEntryList -> new ReservedListItem(anAddressEntryList, walletService, formatter))
                .collect(Collectors.toList()));*/
    }

    private void setLabelColumnCellFactory() {
        labelColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        labelColumn.setCellFactory(new Callback<TableColumn<ReservedListItem, ReservedListItem>,
                TableCell<ReservedListItem,
                        ReservedListItem>>() {

            @Override
            public TableCell<ReservedListItem, ReservedListItem> call(TableColumn<ReservedListItem,
                    ReservedListItem> column) {
                return new TableCell<ReservedListItem, ReservedListItem>() {

                    private Hyperlink hyperlink;

                    @Override
                    public void updateItem(final ReservedListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            hyperlink = new Hyperlink(item.getLabel());
                            hyperlink.setId("id-link");
                            if (item.getAddressEntry().getOfferId() != null) {
                                Tooltip tooltip = new Tooltip(item.getAddressEntry().getOfferId());
                                Tooltip.install(hyperlink, tooltip);

                                hyperlink.setOnAction(event -> log.info("Show trade details " + item.getAddressEntry
                                        ().getOfferId()));
                            }
                            setGraphic(hyperlink);
                        }
                        else {
                            setGraphic(null);
                            setId(null);
                        }
                    }
                };
            }
        });
    }

    private void setBalanceColumnCellFactory() {
        balanceColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        balanceColumn.setCellFactory(
                new Callback<TableColumn<ReservedListItem, ReservedListItem>, TableCell<ReservedListItem,
                        ReservedListItem>>() {

                    @Override
                    public TableCell<ReservedListItem, ReservedListItem> call(TableColumn<ReservedListItem,
                            ReservedListItem> column) {
                        return new TableCell<ReservedListItem, ReservedListItem>() {
                            @Override
                            public void updateItem(final ReservedListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic((item != null && !empty) ? item.getBalanceLabel() : null);
                            }
                        };
                    }
                });
    }

    private void setCopyColumnCellFactory() {
        copyColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        copyColumn.setCellFactory(
                new Callback<TableColumn<ReservedListItem, ReservedListItem>, TableCell<ReservedListItem,
                        ReservedListItem>>() {

                    @Override
                    public TableCell<ReservedListItem, ReservedListItem> call(TableColumn<ReservedListItem,
                            ReservedListItem> column) {
                        return new TableCell<ReservedListItem, ReservedListItem>() {
                            final Label copyIcon = new Label();

                            {
                                copyIcon.getStyleClass().add("copy-icon");
                                AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
                                Tooltip.install(copyIcon, new Tooltip("Copy address to clipboard"));
                            }

                            @Override
                            public void updateItem(final ReservedListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setGraphic(copyIcon);
                                    copyIcon.setOnMouseClicked(e -> GUIUtil.copyToClipboard(item
                                            .addressStringProperty().get()));

                                }
                                else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setConfidenceColumnCellFactory() {
        confidenceColumn.setCellValueFactory((addressListItem) ->
                new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        confidenceColumn.setCellFactory(
                new Callback<TableColumn<ReservedListItem, ReservedListItem>, TableCell<ReservedListItem,
                        ReservedListItem>>() {

                    @Override
                    public TableCell<ReservedListItem, ReservedListItem> call(TableColumn<ReservedListItem,
                            ReservedListItem> column) {
                        return new TableCell<ReservedListItem, ReservedListItem>() {

                            @Override
                            public void updateItem(final ReservedListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setGraphic(item.getProgressIndicator());
                                }
                                else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

}


