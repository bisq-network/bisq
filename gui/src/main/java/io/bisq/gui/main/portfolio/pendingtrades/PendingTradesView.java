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

package io.bisq.gui.main.portfolio.pendingtrades;

import io.bisq.common.UserThread;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Utilities;
import io.bisq.core.alert.PrivateNotificationManager;
import io.bisq.core.offer.Offer;
import io.bisq.core.trade.Trade;
import io.bisq.core.user.Preferences;
import io.bisq.gui.common.view.ActivatableViewAndModel;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.components.HyperlinkWithIcon;
import io.bisq.gui.components.PeerInfoIcon;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.main.overlays.windows.TradeDetailsWindow;
import io.bisq.gui.util.BSFormatter;
import io.bisq.network.p2p.NodeAddress;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.transformation.SortedList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javax.inject.Inject;

@FxmlView
public class PendingTradesView extends ActivatableViewAndModel<VBox, PendingTradesViewModel> {

    private final TradeDetailsWindow tradeDetailsWindow;
    private final BSFormatter formatter;
    private final PrivateNotificationManager privateNotificationManager;
    @FXML
    TableView<PendingTradesListItem> tableView;
    @FXML
    TableColumn<PendingTradesListItem, PendingTradesListItem> priceColumn, volumeColumn, amountColumn, avatarColumn, marketColumn, roleColumn, paymentMethodColumn, tradeIdColumn, dateColumn;
    @FXML

    private SortedList<PendingTradesListItem> sortedList;
    private TradeSubView selectedSubView;
    private EventHandler<KeyEvent> keyEventEventHandler;
    private Scene scene;
    private Subscription selectedTableItemSubscription;
    private Subscription selectedItemSubscription;
    private final Preferences preferences;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PendingTradesView(PendingTradesViewModel model, TradeDetailsWindow tradeDetailsWindow, BSFormatter formatter, PrivateNotificationManager privateNotificationManager, Preferences preferences) {
        super(model);
        this.tradeDetailsWindow = tradeDetailsWindow;
        this.formatter = formatter;
        this.privateNotificationManager = privateNotificationManager;
        this.preferences = preferences;
    }

    @Override
    public void initialize() {
        priceColumn.setText(Res.get("shared.price"));
        amountColumn.setText(Res.get("shared.amountWithCur", Res.getBaseCurrencyCode()));
        volumeColumn.setText(Res.get("shared.volume"));
        marketColumn.setText(Res.get("shared.market"));
        roleColumn.setText(Res.get("portfolio.pending.role"));
        dateColumn.setText(Res.get("shared.dateTime"));
        tradeIdColumn.setText(Res.get("shared.tradeId"));
        paymentMethodColumn.setText(Res.get("shared.paymentMethod"));
        avatarColumn.setText("");

        setTradeIdColumnCellFactory();
        setDateColumnCellFactory();
        setAmountColumnCellFactory();
        setPriceColumnCellFactory();
        setVolumeColumnCellFactory();
        setPaymentMethodColumnCellFactory();
        setMarketColumnCellFactory();
        setRoleColumnCellFactory();
        setAvatarColumnCellFactory();

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new Label(Res.get("table.placeholder.noItems", Res.get("shared.openTrades"))));
        tableView.setMinHeight(100);

        tradeIdColumn.setComparator((o1, o2) -> o1.getTrade().getId().compareTo(o2.getTrade().getId()));
        dateColumn.setComparator((o1, o2) -> o1.getTrade().getDate().compareTo(o2.getTrade().getDate()));
        volumeColumn.setComparator((o1, o2) -> {
            if (o1.getTrade().getTradeVolume() != null && o2.getTrade().getTradeVolume() != null)
                return o1.getTrade().getTradeVolume().compareTo(o2.getTrade().getTradeVolume());
            else
                return 0;
        });
        amountColumn.setComparator((o1, o2) -> {
            if (o1.getTrade().getTradeAmount() != null && o2.getTrade().getTradeAmount() != null)
                return o1.getTrade().getTradeAmount().compareTo(o2.getTrade().getTradeAmount());
            else
                return 0;
        });
        priceColumn.setComparator((o1, o2) -> o1.getPrice().compareTo(o2.getPrice()));
        paymentMethodColumn.setComparator((o1, o2) -> o1.getTrade().getOffer().getPaymentMethod().getId().compareTo(o2.getTrade().getOffer().getPaymentMethod().getId()));
        avatarColumn.setComparator((o1, o2) -> {
            if (o1.getTrade().getTradingPeerNodeAddress() != null && o2.getTrade().getTradingPeerNodeAddress() != null)
                return o1.getTrade().getTradingPeerNodeAddress().getFullAddress().compareTo(o2.getTrade().getTradingPeerNodeAddress().getFullAddress());
            else
                return 0;
        });
        roleColumn.setComparator((o1, o2) -> model.getMyRole(o1).compareTo(model.getMyRole(o2)));
        marketColumn.setComparator((o1, o2) -> model.getMarketLabel(o1).compareTo(model.getMarketLabel(o2)));

        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(dateColumn);


        // we use a hidden emergency shortcut to open support ticket
        keyEventEventHandler = keyEvent -> {
            if (Utilities.isAltOrCtrlPressed(KeyCode.O, keyEvent)) {
                Popup popup = new Popup<>();
                popup.headLine(Res.get("portfolio.pending.openSupportTicket.headline"))
                        .message(Res.get("portfolio.pending.openSupportTicket.msg"))
                        .actionButtonText(Res.get("portfolio.pending.openSupportTicket.headline"))
                        .onAction(model.dataModel::onOpenSupportTicket)
                        .closeButtonText(Res.get("shared.cancel"))
                        .onClose(popup::hide)
                        .show();
            } else if (Utilities.isAltPressed(KeyCode.Y, keyEvent)) {
                new Popup<>().warning(Res.get("portfolio.pending.removeFailedTrade"))
                        .onAction(model.dataModel::onMoveToFailedTrades)
                        .show();
            }
        };
    }

    @Override
    protected void activate() {
        sortedList = new SortedList<>(model.dataModel.list);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);

        scene = root.getScene();
        if (scene != null) {
            scene.addEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);

            /*appFocusSubscription = EasyBind.subscribe(scene.getWindow().focusedProperty(), isFocused -> {
                if (isFocused && model.dataModel.selectedItemProperty.get() != null) {
                    // Focus selectedItem from model
                    int index = table.getItems().indexOf(model.dataModel.selectedItemProperty.get());
                    UserThread.execute(() -> {
                        //TODO app wide focus
                        //table.requestFocus();
                        //UserThread.execute(() -> table.getFocusModel().focus(index));
                    });
                }
            });*/
        }

        selectedItemSubscription = EasyBind.subscribe(model.dataModel.selectedItemProperty, selectedItem -> {
            if (selectedItem != null) {
                if (selectedSubView != null)
                    selectedSubView.deactivate();

                if (selectedItem.getTrade() != null) {
                    selectedSubView = model.dataModel.tradeManager.isBuyer(model.dataModel.getOffer()) ?
                            new BuyerSubView(model) : new SellerSubView(model);

                    selectedSubView.setMinHeight(430);
                    VBox.setVgrow(selectedSubView, Priority.ALWAYS);
                    if (root.getChildren().size() == 1)
                        root.getChildren().add(selectedSubView);
                    else if (root.getChildren().size() == 2)
                        root.getChildren().set(1, selectedSubView);

                }

                updateTableSelection();
            } else {
                removeSelectedSubView();
            }

            model.onSelectedItemChanged(selectedItem);

            if (selectedSubView != null && selectedItem != null)
                selectedSubView.activate();
        });

        selectedTableItemSubscription = EasyBind.subscribe(tableView.getSelectionModel().selectedItemProperty(),
                selectedItem -> {
                    if (selectedItem != null && !selectedItem.equals(model.dataModel.selectedItemProperty.get()))
                        model.dataModel.onSelectItem(selectedItem);
                });

        updateTableSelection();
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();
        selectedItemSubscription.unsubscribe();
        selectedTableItemSubscription.unsubscribe();

        removeSelectedSubView();

        if (scene != null)
            scene.removeEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);
    }

    private void removeSelectedSubView() {
        if (selectedSubView != null) {
            selectedSubView.deactivate();
            root.getChildren().remove(selectedSubView);
            selectedSubView = null;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateTableSelection() {
        PendingTradesListItem selectedItemFromModel = model.dataModel.selectedItemProperty.get();
        if (selectedItemFromModel != null) {
            // Select and focus selectedItem from model
            int index = tableView.getItems().indexOf(selectedItemFromModel);
            UserThread.execute(() -> {
                //TODO app wide focus
                tableView.getSelectionModel().select(index);
                //table.requestFocus();
                //UserThread.execute(() -> table.getFocusModel().focus(index));
            });
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // CellFactories
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setTradeIdColumnCellFactory() {
        tradeIdColumn.setCellValueFactory((pendingTradesListItem) -> new ReadOnlyObjectWrapper<>(pendingTradesListItem.getValue()));
        tradeIdColumn.setCellFactory(
                new Callback<TableColumn<PendingTradesListItem, PendingTradesListItem>, TableCell<PendingTradesListItem, PendingTradesListItem>>() {

                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(TableColumn<PendingTradesListItem,
                            PendingTradesListItem> column) {
                        return new TableCell<PendingTradesListItem, PendingTradesListItem>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    field = new HyperlinkWithIcon(item.getTrade().getShortId(), true);
                                    field.setOnAction(event -> tradeDetailsWindow.show(item.getTrade()));
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
    }

    private void setDateColumnCellFactory() {
        dateColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        dateColumn.setCellFactory(
                new Callback<TableColumn<PendingTradesListItem, PendingTradesListItem>, TableCell<PendingTradesListItem,
                        PendingTradesListItem>>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<PendingTradesListItem, PendingTradesListItem>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                   /* if (model.showDispute(item.getTrade())) {
                                        setStyle("-fx-text-fill: -bs-error-red");
                                    } else if (model.showWarning(item.getTrade())) {
                                        setStyle("-fx-text-fill: -bs-warning");
                                    } else {
                                        setId("-fx-text-fill: black");
                                    }*/
                                    setText(formatter.formatDateTime(item.getTrade().getDate()));
                                } else {
                                    setText(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setAmountColumnCellFactory() {
        amountColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        amountColumn.setCellFactory(
                new Callback<TableColumn<PendingTradesListItem, PendingTradesListItem>, TableCell<PendingTradesListItem,
                        PendingTradesListItem>>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<PendingTradesListItem, PendingTradesListItem>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(formatter.formatCoinWithCode(item.getTrade().getTradeAmount()));
                                else
                                    setText(null);
                            }
                        };
                    }
                });
    }

    private void setPriceColumnCellFactory() {
        priceColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        priceColumn.setCellFactory(
                new Callback<TableColumn<PendingTradesListItem, PendingTradesListItem>, TableCell<PendingTradesListItem,
                        PendingTradesListItem>>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<PendingTradesListItem, PendingTradesListItem>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(formatter.formatPrice(item.getPrice()));
                                else
                                    setText(null);
                            }
                        };
                    }
                });
    }

    private void setVolumeColumnCellFactory() {
        volumeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        volumeColumn.setCellFactory(
                new Callback<TableColumn<PendingTradesListItem, PendingTradesListItem>, TableCell<PendingTradesListItem,
                        PendingTradesListItem>>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<PendingTradesListItem, PendingTradesListItem>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(formatter.formatVolumeWithCode(item.getTrade().getTradeVolume()));
                                else
                                    setText(null);
                            }
                        };
                    }
                });
    }

    private void setPaymentMethodColumnCellFactory() {
        paymentMethodColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        paymentMethodColumn.setCellFactory(
                new Callback<TableColumn<PendingTradesListItem, PendingTradesListItem>, TableCell<PendingTradesListItem,
                        PendingTradesListItem>>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<PendingTradesListItem, PendingTradesListItem>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(model.getPaymentMethod(item));
                                else
                                    setText(null);
                            }
                        };
                    }
                });
    }

    private void setMarketColumnCellFactory() {
        marketColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        marketColumn.setCellFactory(
                new Callback<TableColumn<PendingTradesListItem, PendingTradesListItem>, TableCell<PendingTradesListItem,
                        PendingTradesListItem>>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<PendingTradesListItem, PendingTradesListItem>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(model.getMarketLabel(item));
                            }
                        };
                    }
                });
    }

    private void setRoleColumnCellFactory() {
        roleColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        roleColumn.setCellFactory(
                new Callback<TableColumn<PendingTradesListItem, PendingTradesListItem>, TableCell<PendingTradesListItem,
                        PendingTradesListItem>>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<PendingTradesListItem, PendingTradesListItem>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(model.getMyRole(item));
                                else
                                    setText(null);
                            }
                        };
                    }
                });
    }

    @SuppressWarnings("UnusedReturnValue")
    private TableColumn<PendingTradesListItem, PendingTradesListItem> setAvatarColumnCellFactory() {
        avatarColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        avatarColumn.setCellFactory(
                new Callback<TableColumn<PendingTradesListItem, PendingTradesListItem>, TableCell<PendingTradesListItem,
                        PendingTradesListItem>>() {

                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<PendingTradesListItem, PendingTradesListItem>() {

                            @Override
                            public void updateItem(final PendingTradesListItem newItem, boolean empty) {
                                super.updateItem(newItem, empty);
                                if (!empty && newItem != null) {
                                    final Trade trade = newItem.getTrade();
                                    final NodeAddress tradingPeerNodeAddress = trade.getTradingPeerNodeAddress();
                                    int numPastTrades = model.getNumPastTrades(trade);
                                    final Offer offer = trade.getOffer();
                                    String role = Res.get("peerInfoIcon.tooltip.tradePeer");
                                    Node peerInfoIcon = new PeerInfoIcon(tradingPeerNodeAddress,
                                            role,
                                            numPastTrades,
                                            privateNotificationManager,
                                            offer,
                                            preferences,
                                            model.accountAgeWitnessService,
                                            formatter);
                                    setPadding(new Insets(1, 0, 0, 0));
                                    setGraphic(peerInfoIcon);
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        return avatarColumn;
    }
}

