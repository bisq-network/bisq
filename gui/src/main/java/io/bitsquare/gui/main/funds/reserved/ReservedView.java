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
import io.bitsquare.common.util.Utilities;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.popups.OfferDetailsPopup;
import io.bitsquare.gui.popups.Popup;
import io.bitsquare.gui.popups.TradeDetailsPopup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.offer.OpenOffer;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.user.Preferences;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@FxmlView
public class ReservedView extends ActivatableView<VBox, Void> {
    @FXML TableView<ReservedListItem> table;
    @FXML
    TableColumn<ReservedListItem, ReservedListItem> labelColumn, addressColumn, balanceColumn, confidenceColumn;

    private final WalletService walletService;
    private final TradeManager tradeManager;
    private final OpenOfferManager openOfferManager;
    private final Preferences preferences;
    private final BSFormatter formatter;
    private final OfferDetailsPopup offerDetailsPopup;
    private final TradeDetailsPopup tradeDetailsPopup;
    private final ObservableList<ReservedListItem> addressList = FXCollections.observableArrayList();

    @Inject
    private ReservedView(WalletService walletService, TradeManager tradeManager, OpenOfferManager openOfferManager, Preferences preferences,
                         BSFormatter formatter, OfferDetailsPopup offerDetailsPopup, TradeDetailsPopup tradeDetailsPopup) {
        this.walletService = walletService;
        this.tradeManager = tradeManager;
        this.openOfferManager = openOfferManager;
        this.preferences = preferences;
        this.formatter = formatter;
        this.offerDetailsPopup = offerDetailsPopup;
        this.tradeDetailsPopup = tradeDetailsPopup;
    }


    @Override
    public void initialize() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No funded are reserved in open offers or trades"));

        setLabelColumnCellFactory();
        setAddressColumnCellFactory();
        setBalanceColumnCellFactory();
        setConfidenceColumnCellFactory();
    }

    @Override
    protected void activate() {
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
    protected void deactivate() {
        addressList.forEach(ReservedListItem::cleanup);
    }


    private void fillList() {
        addressList.forEach(ReservedListItem::cleanup);
        addressList.clear();
        addressList.addAll(Stream.concat(openOfferManager.getOpenOffers().stream(), tradeManager.getTrades().stream())
                .map(tradable -> new ReservedListItem(tradable, walletService.getAddressEntryByOfferId(tradable.getOffer().getId()), walletService, formatter))
                .collect(Collectors.toList()));
    }

    private void openBlockExplorer(ReservedListItem item) {
        try {
            Utilities.openWebPage(preferences.getBlockChainExplorer().addressUrl + item.getAddressString());
        } catch (Exception e) {
            log.error(e.getMessage());
            new Popup().warning("Opening browser failed. Please check your internet " +
                    "connection.").show();
        }
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
                            if (item.getAddressEntry().getOfferId() != null) {
                                Tooltip tooltip = new Tooltip(item.getAddressEntry().getOfferId());
                                Tooltip.install(hyperlink, tooltip);

                                hyperlink.setOnAction(event -> {
                                    Optional<Trade> tradeOptional = tradeManager.getTradeById(item.getAddressEntry().getOfferId());
                                    Optional<OpenOffer> openOfferOptional = openOfferManager.getOpenOfferById(item.getAddressEntry().getOfferId());
                                    if (tradeOptional.isPresent())
                                        tradeDetailsPopup.show(tradeOptional.get());
                                    else if (openOfferOptional.isPresent())
                                        offerDetailsPopup.show(openOfferOptional.get().getOffer());
                                });
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

    private void setAddressColumnCellFactory() {
        addressColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        addressColumn.setCellFactory(
                new Callback<TableColumn<ReservedListItem, ReservedListItem>, TableCell<ReservedListItem,
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
                                    hyperlink = new Hyperlink(item.getAddressString());
                                    hyperlink.setOnAction(event -> openBlockExplorer(item));
                                    setGraphic(hyperlink);
                                } else {
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


