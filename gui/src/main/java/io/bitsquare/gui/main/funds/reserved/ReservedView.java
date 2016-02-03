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

import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.HyperlinkWithIcon;
import io.bitsquare.gui.popups.OfferDetailsPopup;
import io.bitsquare.gui.popups.Popup;
import io.bitsquare.gui.popups.TradeDetailsPopup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.trade.Tradable;
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
    @FXML
    TableView<ReservedListItem> table;
    @FXML
    TableColumn<ReservedListItem, ReservedListItem> dateColumn, detailsColumn, addressColumn, balanceColumn, confidenceColumn;

    private final WalletService walletService;
    private final TradeManager tradeManager;
    private final OpenOfferManager openOfferManager;
    private final Preferences preferences;
    private final BSFormatter formatter;
    private final OfferDetailsPopup offerDetailsPopup;
    private final TradeDetailsPopup tradeDetailsPopup;
    private final ObservableList<ReservedListItem> reservedAddresses = FXCollections.observableArrayList();
    private BalanceListener balanceListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

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
        table.setPlaceholder(new Label("No funds are reserved in open offers or trades"));
        setDateColumnCellFactory();
        setDetailsColumnCellFactory();
        setAddressColumnCellFactory();
        setBalanceColumnCellFactory();
        table.getSortOrder().add(dateColumn);
        balanceListener = new BalanceListener() {
            @Override
            public void onBalanceChanged(Coin balance) {
                updateList();
            }
        };
    }

    @Override
    protected void activate() {
        updateList();

        walletService.addBalanceListener(balanceListener);
    }

    @Override
    protected void deactivate() {
        reservedAddresses.forEach(ReservedListItem::cleanup);
        walletService.removeBalanceListener(balanceListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateList() {
        reservedAddresses.forEach(ReservedListItem::cleanup);
        reservedAddresses.setAll(Stream.concat(openOfferManager.getOpenOffers().stream(), tradeManager.getTrades().stream())
                .map(tradable -> new ReservedListItem(tradable, walletService.getAddressEntryByOfferId(tradable.getOffer().getId()), walletService, formatter))
                .collect(Collectors.toList()));

        reservedAddresses.sort((o1, o2) -> getTradable(o2).get().getDate().compareTo(getTradable(o1).get().getDate()));
        table.setItems(reservedAddresses);
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

    private Optional<Tradable> getTradable(ReservedListItem item) {
        String offerId = item.getAddressEntry().getOfferId();
        Optional<Trade> tradeOptional = tradeManager.getTradeById(offerId);
        if (tradeOptional.isPresent()) {
            return Optional.of(tradeOptional.get());
        } else if (openOfferManager.getOpenOfferById(offerId).isPresent()) {
            return Optional.of(openOfferManager.getOpenOfferById(offerId).get());
        } else {
            return Optional.empty();
        }
    }

    private void openDetailPopup(ReservedListItem item) {
        Optional<Tradable> tradableOptional = getTradable(item);
        if (tradableOptional.isPresent()) {
            Tradable tradable = tradableOptional.get();
            if (tradable instanceof Trade) {
                tradeDetailsPopup.show((Trade) tradable);
            } else if (tradable instanceof OpenOffer) {
                offerDetailsPopup.show(tradable.getOffer());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ColumnCellFactories
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setDateColumnCellFactory() {
        dateColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        dateColumn.setCellFactory(new Callback<TableColumn<ReservedListItem, ReservedListItem>,
                TableCell<ReservedListItem, ReservedListItem>>() {

            @Override
            public TableCell<ReservedListItem, ReservedListItem> call(TableColumn<ReservedListItem,
                    ReservedListItem> column) {
                return new TableCell<ReservedListItem, ReservedListItem>() {

                    @Override
                    public void updateItem(final ReservedListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            if (getTradable(item).isPresent())
                                setText(formatter.formatDateTime(getTradable(item).get().getDate()));
                            else
                                setText("No date available");
                        } else {
                            setText("");
                        }
                    }
                };
            }
        });
    }

    private void setDetailsColumnCellFactory() {
        detailsColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper<>(addressListItem.getValue()));
        detailsColumn.setCellFactory(new Callback<TableColumn<ReservedListItem, ReservedListItem>,
                TableCell<ReservedListItem, ReservedListItem>>() {

            @Override
            public TableCell<ReservedListItem, ReservedListItem> call(TableColumn<ReservedListItem,
                    ReservedListItem> column) {
                return new TableCell<ReservedListItem, ReservedListItem>() {

                    private HyperlinkWithIcon field;

                    @Override
                    public void updateItem(final ReservedListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            Optional<Tradable> tradableOptional = getTradable(item);
                            if (tradableOptional.isPresent()) {
                                AddressEntry addressEntry = item.getAddressEntry();
                                String details;
                                if (addressEntry.getContext() == AddressEntry.Context.TRADE) {
                                    String prefix;
                                    Tradable tradable = tradableOptional.get();
                                    if (tradable instanceof Trade)
                                        prefix = "Trade ID: ";
                                    else if (tradable instanceof OpenOffer)
                                        prefix = "Offer ID: ";
                                    else
                                        prefix = "";

                                    details = prefix + addressEntry.getShortOfferId();
                                } else if (addressEntry.getContext() == AddressEntry.Context.ARBITRATOR) {
                                    details = "Arbitration fee";
                                } else {
                                    details = "-";
                                }

                                field = new HyperlinkWithIcon(details + " (" + item.getFundsInfo() + ")",
                                        AwesomeIcon.INFO_SIGN);
                                field.setOnAction(event -> openDetailPopup(item));
                                field.setTooltip(new Tooltip("Open popup for details"));
                                setGraphic(field);
                            } else if (item.getAddressEntry().getContext() == AddressEntry.Context.ARBITRATOR) {
                                setGraphic(new Label("Arbitrators fee"));
                            } else {
                                setGraphic(new Label("No details available"));
                            }

                        } else {
                            setGraphic(null);
                            if (field != null)
                                field.setOnAction(null);
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
                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(final ReservedListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String address = item.getAddressString();
                                    hyperlinkWithIcon = new HyperlinkWithIcon(address, AwesomeIcon.EXTERNAL_LINK);
                                    hyperlinkWithIcon.setOnAction(event -> openBlockExplorer(item));
                                    hyperlinkWithIcon.setTooltip(new Tooltip("Open external blockchain explorer for " +
                                            "address: " + address));
                                    setGraphic(hyperlinkWithIcon);
                                } else {
                                    setGraphic(null);
                                    if (hyperlinkWithIcon != null)
                                        hyperlinkWithIcon.setOnAction(null);
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

}


