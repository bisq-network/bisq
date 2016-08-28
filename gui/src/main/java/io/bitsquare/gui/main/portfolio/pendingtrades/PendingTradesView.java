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

package io.bitsquare.gui.main.portfolio.pendingtrades;

import io.bitsquare.alert.PrivateNotificationManager;
import io.bitsquare.common.UserThread;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.HyperlinkWithIcon;
import io.bitsquare.gui.components.PeerInfoIcon;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.main.overlays.windows.TradeDetailsWindow;
import io.bitsquare.gui.util.BSFormatter;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.transformation.SortedList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
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
    private PrivateNotificationManager privateNotificationManager;
    @FXML
    TableView<PendingTradesListItem> tableView;
    @FXML
    TableColumn<PendingTradesListItem, PendingTradesListItem> priceColumn, tradeVolumeColumn, tradeAmountColumn, avatarColumn, marketColumn, roleColumn, paymentMethodColumn, idColumn, dateColumn;
    @FXML

    private SortedList<PendingTradesListItem> sortedList;
    private TradeSubView selectedSubView;
    private EventHandler<KeyEvent> keyEventEventHandler;
    private Scene scene;
    private Subscription selectedTableItemSubscription;
    private Subscription selectedItemSubscription;
    private Subscription appFocusSubscription;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PendingTradesView(PendingTradesViewModel model, TradeDetailsWindow tradeDetailsWindow, BSFormatter formatter, PrivateNotificationManager privateNotificationManager) {
        super(model);
        this.tradeDetailsWindow = tradeDetailsWindow;
        this.formatter = formatter;
        this.privateNotificationManager = privateNotificationManager;
    }

    @Override
    public void initialize() {
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
        tableView.setPlaceholder(new Label("No pending trades available"));
        tableView.setMinHeight(100);

        idColumn.setComparator((o1, o2) -> o1.getTrade().getId().compareTo(o2.getTrade().getId()));
        dateColumn.setComparator((o1, o2) -> o1.getTrade().getDate().compareTo(o2.getTrade().getDate()));
        tradeVolumeColumn.setComparator((o1, o2) -> {
            if (o1.getTrade().getTradeVolume() != null && o2.getTrade().getTradeVolume() != null)
                return o1.getTrade().getTradeVolume().compareTo(o2.getTrade().getTradeVolume());
            else
                return 0;
        });
        tradeAmountColumn.setComparator((o1, o2) -> {
            if (o1.getTrade().getTradeAmount() != null && o2.getTrade().getTradeAmount() != null)
                return o1.getTrade().getTradeAmount().compareTo(o2.getTrade().getTradeAmount());
            else
                return 0;
        });
        priceColumn.setComparator((o1, o2) -> o1.getPrice().compareTo(o2.getPrice()));
        paymentMethodColumn.setComparator((o1, o2) -> o1.getTrade().getOffer().getPaymentMethod().getId().compareTo(o2.getTrade().getOffer().getPaymentMethod().getId()));
        avatarColumn.setComparator((o1, o2) -> {
            if (o1.getTrade().getTradingPeerNodeAddress() != null && o2.getTrade().getTradingPeerNodeAddress() != null)
                return o1.getTrade().getTradingPeerNodeAddress().hostName.compareTo(o2.getTrade().getTradingPeerNodeAddress().hostName);
            else
                return 0;
        });
        roleColumn.setComparator((o1, o2) -> model.getMyRole(o1).compareTo(model.getMyRole(o2)));
        marketColumn.setComparator((o1, o2) -> model.getMarketLabel(o1).compareTo(model.getMarketLabel(o2)));

        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(dateColumn);


        // we use a hidden emergency shortcut to open support ticket
        keyEventEventHandler = event -> {
            if (new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN).match(event)) {
                Popup popup = new Popup();
                popup.headLine("Open support ticket")
                        .message("Please use that only in emergency case if you don't get displayed a \"Open support\" or \"Open dispute\" button.\n\n" +
                                "When you open a support ticket the trade will be interrupted and handled by the arbitrator\n\n" +
                                "Unjustified support tickets (e.g. caused by usability problems or questions) will " +
                                "cause a loss of the security deposit by the trader who opened the ticket.")
                        .actionButtonText("Open support ticket")
                        .onAction(model.dataModel::onOpenSupportTicket)
                        .closeButtonText("Cancel")
                        .onClose(() -> popup.hide())
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
        if (appFocusSubscription != null)
            appFocusSubscription.unsubscribe();

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
        idColumn.setCellValueFactory((pendingTradesListItem) -> new ReadOnlyObjectWrapper<>(pendingTradesListItem.getValue()));
        idColumn.setCellFactory(
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
                                    field.setTooltip(new Tooltip("Open popup for details"));
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
        tradeAmountColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        tradeAmountColumn.setCellFactory(
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
        tradeVolumeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        tradeVolumeColumn.setCellFactory(
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

                                if (newItem != null && !empty && newItem.getTrade().getTradingPeerNodeAddress() != null) {
                                    String hostName = newItem.getTrade().getTradingPeerNodeAddress().hostName;
                                    int numPastTrades = model.getNumPastTrades(newItem.getTrade());
                                    boolean hasTraded = numPastTrades > 0;
                                    String tooltipText = hasTraded ? "Trading peers onion address: " + hostName + "\n" +
                                            "You have already traded " + numPastTrades + " times with that peer." : "Trading peers onion address: " + hostName;
                                    Node identIcon = new PeerInfoIcon(hostName, tooltipText, numPastTrades, privateNotificationManager, newItem.getTrade().getOffer());
                                    setPadding(new Insets(-2, 0, -2, 0));
                                    if (identIcon != null)
                                        setGraphic(identIcon);
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

