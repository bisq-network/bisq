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

package bisq.desktop.main.portfolio.pendingtrades;

import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.PeerInfoIcon;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.TradeDetailsWindow;

import bisq.core.alert.PrivateNotificationManager;
import bisq.core.app.AppOptionKeys;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.trade.Trade;
import bisq.core.user.Preferences;
import bisq.core.util.BSFormatter;

import bisq.network.p2p.NodeAddress;

import bisq.common.UserThread;
import bisq.common.util.Utilities;

import com.google.inject.name.Named;

import javax.inject.Inject;

import javafx.fxml.FXML;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.event.EventHandler;

import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Comparator;

import static com.google.common.base.Preconditions.checkNotNull;

@FxmlView
public class PendingTradesView extends ActivatableViewAndModel<VBox, PendingTradesViewModel> {

    private final TradeDetailsWindow tradeDetailsWindow;
    private final BSFormatter formatter;
    private final PrivateNotificationManager privateNotificationManager;
    private final boolean useDevPrivilegeKeys;
    @FXML
    TableView<PendingTradesListItem> tableView;
    @FXML
    TableColumn<PendingTradesListItem, PendingTradesListItem> priceColumn, volumeColumn, amountColumn, avatarColumn, marketColumn, roleColumn, paymentMethodColumn, tradeIdColumn, dateColumn;

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
    public PendingTradesView(PendingTradesViewModel model,
                             TradeDetailsWindow tradeDetailsWindow,
                             BSFormatter formatter,
                             PrivateNotificationManager privateNotificationManager,
                             Preferences preferences,
                             @Named(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        super(model);
        this.tradeDetailsWindow = tradeDetailsWindow;
        this.formatter = formatter;
        this.privateNotificationManager = privateNotificationManager;
        this.preferences = preferences;
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
    }

    @Override
    public void initialize() {
        priceColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.price")));
        amountColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.amountWithCur", Res.getBaseCurrencyCode())));
        volumeColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.amount")));
        marketColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.market")));
        roleColumn.setGraphic(new AutoTooltipLabel(Res.get("portfolio.pending.role")));
        dateColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.dateTime")));
        tradeIdColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.tradeId")));
        paymentMethodColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.paymentMethod")));
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
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noItems", Res.get("shared.openTrades"))));
        tableView.setMinHeight(100);

        tradeIdColumn.setComparator(Comparator.comparing(o -> o.getTrade().getId()));
        dateColumn.setComparator(Comparator.comparing(o -> o.getTrade().getDate()));
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
        priceColumn.setComparator(Comparator.comparing(PendingTradesListItem::getPrice));
        paymentMethodColumn.setComparator(Comparator.comparing(o -> o.getTrade().getOffer() != null ?
                o.getTrade().getOffer().getPaymentMethod().getId() : null));
        avatarColumn.setComparator((o1, o2) -> {
            if (o1.getTrade().getTradingPeerNodeAddress() != null && o2.getTrade().getTradingPeerNodeAddress() != null)
                return o1.getTrade().getTradingPeerNodeAddress().getFullAddress().compareTo(o2.getTrade().getTradingPeerNodeAddress().getFullAddress());
            else
                return 0;
        });
        roleColumn.setComparator(Comparator.comparing(model::getMyRole));
        marketColumn.setComparator(Comparator.comparing(model::getMarketLabel));

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

            //TODO: in what cases is it necessary to request focus?
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

                    selectedSubView.setMinHeight(440);
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
        tradeIdColumn.getStyleClass().add("first-column");
        tradeIdColumn.setCellValueFactory((pendingTradesListItem) -> new ReadOnlyObjectWrapper<>(pendingTradesListItem.getValue()));
        tradeIdColumn.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(TableColumn<PendingTradesListItem,
                            PendingTradesListItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    field = new HyperlinkWithIcon(item.getTrade().getShortId());
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
                new Callback<>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    setGraphic(new AutoTooltipLabel(formatter.formatDateTime(item.getTrade().getDate())));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setAmountColumnCellFactory() {
        amountColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        amountColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setGraphic(new AutoTooltipLabel(formatter.formatCoin(item.getTrade().getTradeAmount())));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    private void setPriceColumnCellFactory() {
        priceColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        priceColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setGraphic(new AutoTooltipLabel(formatter.formatPrice(item.getPrice())));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    private void setVolumeColumnCellFactory() {
        volumeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        volumeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setGraphic(new AutoTooltipLabel(formatter.formatVolumeWithCode(item.getTrade().getTradeVolume())));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    private void setPaymentMethodColumnCellFactory() {
        paymentMethodColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        paymentMethodColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setGraphic(new AutoTooltipLabel(model.getPaymentMethod(item)));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    private void setMarketColumnCellFactory() {
        marketColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        marketColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(new AutoTooltipLabel(model.getMarketLabel(item)));
                            }
                        };
                    }
                });
    }

    private void setRoleColumnCellFactory() {
        roleColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        roleColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setGraphic(new AutoTooltipLabel(model.getMyRole(item)));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
    }

    @SuppressWarnings("UnusedReturnValue")
    private TableColumn<PendingTradesListItem, PendingTradesListItem> setAvatarColumnCellFactory() {
        avatarColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        avatarColumn.getStyleClass().addAll("last-column", "avatar-column");
        avatarColumn.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<>() {

                            @Override
                            public void updateItem(final PendingTradesListItem newItem, boolean empty) {
                                super.updateItem(newItem, empty);
                                if (!empty && newItem != null) {
                                    final Trade trade = newItem.getTrade();
                                    final NodeAddress tradingPeerNodeAddress = trade.getTradingPeerNodeAddress();
                                    int numPastTrades = model.getNumPastTrades(trade);
                                    final Offer offer = trade.getOffer();
                                    checkNotNull(offer, "Offer must not be null in PendingTradesView");
                                    String role = Res.get("peerInfoIcon.tooltip.tradePeer");
                                    Node peerInfoIcon = new PeerInfoIcon(tradingPeerNodeAddress,
                                            role,
                                            numPastTrades,
                                            privateNotificationManager,
                                            offer,
                                            preferences,
                                            model.accountAgeWitnessService,
                                            formatter,
                                            useDevPrivilegeKeys);
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

