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

package io.bisq.gui.main.funds.reserved;

import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bisq.common.locale.Res;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.listeners.BalanceListener;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.offer.OpenOffer;
import io.bisq.core.offer.OpenOfferManager;
import io.bisq.core.trade.Tradable;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.TradeManager;
import io.bisq.core.user.Preferences;
import io.bisq.gui.common.view.ActivatableView;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.components.HyperlinkWithIcon;
import io.bisq.gui.main.overlays.windows.OfferDetailsWindow;
import io.bisq.gui.main.overlays.windows.TradeDetailsWindow;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.GUIUtil;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;
import java.util.Optional;
import java.util.stream.Collectors;

@FxmlView
public class ReservedView extends ActivatableView<VBox, Void> {
    @FXML
    TableView<ReservedListItem> tableView;
    @FXML
    TableColumn<ReservedListItem, ReservedListItem> dateColumn, detailsColumn, addressColumn, balanceColumn;

    private final BtcWalletService btcWalletService;
    private final TradeManager tradeManager;
    private final OpenOfferManager openOfferManager;
    private final Preferences preferences;
    private final BSFormatter formatter;
    private final OfferDetailsWindow offerDetailsWindow;
    private final TradeDetailsWindow tradeDetailsWindow;
    private final ObservableList<ReservedListItem> observableList = FXCollections.observableArrayList();
    private final SortedList<ReservedListItem> sortedList = new SortedList<>(observableList);
    private BalanceListener balanceListener;
    private ListChangeListener<OpenOffer> openOfferListChangeListener;
    private ListChangeListener<Trade> tradeListChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ReservedView(BtcWalletService btcWalletService, TradeManager tradeManager, OpenOfferManager openOfferManager, Preferences preferences,
                         BSFormatter formatter, OfferDetailsWindow offerDetailsWindow, TradeDetailsWindow tradeDetailsWindow) {
        this.btcWalletService = btcWalletService;
        this.tradeManager = tradeManager;
        this.openOfferManager = openOfferManager;
        this.preferences = preferences;
        this.formatter = formatter;
        this.offerDetailsWindow = offerDetailsWindow;
        this.tradeDetailsWindow = tradeDetailsWindow;
    }

    @Override
    public void initialize() {
        dateColumn.setText(Res.get("shared.dateTime"));
        detailsColumn.setText(Res.get("shared.details"));
        addressColumn.setText(Res.get("shared.address"));
        balanceColumn.setText(Res.get("shared.balanceWithCur", Res.getBaseCurrencyCode()));

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new Label(Res.get("funds.reserved.noFunds")));

        setDateColumnCellFactory();
        setDetailsColumnCellFactory();
        setAddressColumnCellFactory();
        setBalanceColumnCellFactory();

        addressColumn.setComparator((o1, o2) -> o1.getAddressString().compareTo(o2.getAddressString()));
        detailsColumn.setComparator((o1, o2) -> o1.getOpenOffer().getId().compareTo(o2.getOpenOffer().getId()));
        balanceColumn.setComparator((o1, o2) -> o1.getBalance().compareTo(o2.getBalance()));
        dateColumn.setComparator((o1, o2) -> {
            if (getTradable(o1).isPresent() && getTradable(o2).isPresent())
                return getTradable(o2).get().getDate().compareTo(getTradable(o1).get().getDate());
            else
                return 0;
        });
        tableView.getSortOrder().add(dateColumn);
        dateColumn.setSortType(TableColumn.SortType.DESCENDING);

        balanceListener = new BalanceListener() {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                updateList();
            }
        };
        openOfferListChangeListener = c -> updateList();
        tradeListChangeListener = c -> updateList();
    }

    @Override
    protected void activate() {
        openOfferManager.getObservableList().addListener(openOfferListChangeListener);
        tradeManager.getTradableList().addListener(tradeListChangeListener);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);
        updateList();

        btcWalletService.addBalanceListener(balanceListener);
    }

    @Override
    protected void deactivate() {
        openOfferManager.getObservableList().removeListener(openOfferListChangeListener);
        tradeManager.getTradableList().removeListener(tradeListChangeListener);
        sortedList.comparatorProperty().unbind();
        observableList.forEach(ReservedListItem::cleanup);
        btcWalletService.removeBalanceListener(balanceListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateList() {
        observableList.forEach(ReservedListItem::cleanup);
        observableList.setAll(openOfferManager.getObservableList().stream()
                .map(openOffer -> {
                    Optional<AddressEntry> addressEntryOptional = btcWalletService.getAddressEntry(openOffer.getId(), AddressEntry.Context.RESERVED_FOR_TRADE);
                    if (addressEntryOptional.isPresent()) {
                        return new ReservedListItem(openOffer,
                                addressEntryOptional.get(),
                                btcWalletService,
                                formatter);
                    } else {
                        return null;
                    }
                })
                .filter(e -> e != null)
                .collect(Collectors.toList()));
    }

    private void openBlockExplorer(ReservedListItem item) {
        GUIUtil.openWebPage(preferences.getBlockChainExplorer().addressUrl + item.getAddressString());
    }

    private Optional<Tradable> getTradable(ReservedListItem item) {
        String offerId = item.getAddressEntry().getOfferId();
        Optional<Trade> tradeOptional = tradeManager.getTradeById(offerId);
        if (tradeOptional.isPresent()) {
            return Optional.of(tradeOptional.get());
        } else if (openOfferManager.getOpenOfferById(offerId).isPresent()) {
            return Optional.of(openOfferManager.getOpenOfferById(offerId).get());
        } else {
            return Optional.<Tradable>empty();
        }
    }

    private void openDetailPopup(ReservedListItem item) {
        Optional<Tradable> tradableOptional = getTradable(item);
        if (tradableOptional.isPresent()) {
            Tradable tradable = tradableOptional.get();
            if (tradable instanceof Trade) {
                tradeDetailsWindow.show((Trade) tradable);
            } else if (tradable instanceof OpenOffer) {
                offerDetailsWindow.show(tradable.getOffer());
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
                                setText(Res.get("shared.noDateAvailable"));
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
                                field = new HyperlinkWithIcon(Res.get("funds.reserved.reserved", item.getAddressEntry().getShortOfferId()),
                                        AwesomeIcon.INFO_SIGN);
                                field.setOnAction(event -> openDetailPopup(item));
                                field.setTooltip(new Tooltip(Res.get("tooltip.openPopupForDetails")));
                                setGraphic(field);
                            } else if (item.getAddressEntry().getContext() == AddressEntry.Context.ARBITRATOR) {
                                setGraphic(new Label(Res.get("shared.arbitratorsFee")));
                            } else {
                                setGraphic(new Label(Res.get("shared.noDetailsAvailable")));
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
                                    hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForAddress", address)));
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


