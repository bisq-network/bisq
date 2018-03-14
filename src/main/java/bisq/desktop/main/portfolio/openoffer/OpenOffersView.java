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

package bisq.desktop.main.portfolio.openoffer;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.main.MainView;
import bisq.desktop.main.funds.FundsView;
import bisq.desktop.main.funds.withdrawal.WithdrawalView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.OfferDetailsWindow;

import bisq.core.offer.OpenOffer;
import bisq.core.user.DontShowAgainLookup;

import bisq.common.locale.Res;
import bisq.common.monetary.Price;
import bisq.common.monetary.Volume;

import javax.inject.Inject;

import javafx.fxml.FXML;

import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import org.jetbrains.annotations.NotNull;

@FxmlView
public class OpenOffersView extends ActivatableViewAndModel<VBox, OpenOffersViewModel> {

    @FXML
    TableView<OpenOfferListItem> tableView;
    @FXML
    TableColumn<OpenOfferListItem, OpenOfferListItem> priceColumn, amountColumn, volumeColumn,
            marketColumn, directionColumn, dateColumn, offerIdColumn, deactivateItemColumn, removeItemColumn;
    private final Navigation navigation;
    private final OfferDetailsWindow offerDetailsWindow;
    private SortedList<OpenOfferListItem> sortedList;

    @Inject
    public OpenOffersView(OpenOffersViewModel model, Navigation navigation, OfferDetailsWindow offerDetailsWindow) {
        super(model);
        this.navigation = navigation;
        this.offerDetailsWindow = offerDetailsWindow;
    }

    @Override
    public void initialize() {
        priceColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.price")));
        amountColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.BTCMinMax")));
        volumeColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.amountMinMax")));
        marketColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.market")));
        directionColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.offerType")));
        dateColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.dateTime")));
        offerIdColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.offerId")));
        deactivateItemColumn.setGraphic(new AutoTooltipLabel(""));
        removeItemColumn.setGraphic(new AutoTooltipLabel(""));

        setOfferIdColumnCellFactory();
        setDirectionColumnCellFactory();
        setMarketColumnCellFactory();
        setPriceColumnCellFactory();
        setAmountColumnCellFactory();
        setVolumeColumnCellFactory();
        setDateColumnCellFactory();
        setDeactivateColumnCellFactory();
        setRemoveColumnCellFactory();

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noItems", Res.get("shared.openOffers"))));

        offerIdColumn.setComparator((o1, o2) -> o1.getOffer().getId().compareTo(o2.getOffer().getId()));
        directionColumn.setComparator((o1, o2) -> o1.getOffer().getDirection().compareTo(o2.getOffer().getDirection()));
        marketColumn.setComparator((o1, o2) -> model.getMarketLabel(o1).compareTo(model.getMarketLabel(o2)));
        amountColumn.setComparator((o1, o2) -> o1.getOffer().getAmount().compareTo(o2.getOffer().getAmount()));
        priceColumn.setComparator((o1, o2) -> {
            Price price1 = o1.getOffer().getPrice();
            Price price2 = o2.getOffer().getPrice();
            return price1 != null && price2 != null ? price1.compareTo(price2) : 0;
        });
        volumeColumn.setComparator((o1, o2) -> {
            Volume offerVolume1 = o1.getOffer().getVolume();
            Volume offerVolume2 = o2.getOffer().getVolume();
            return offerVolume1 != null && offerVolume2 != null ? offerVolume1.compareTo(offerVolume2) : 0;
        });
        dateColumn.setComparator((o1, o2) -> o1.getOffer().getDate().compareTo(o2.getOffer().getDate()));

        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(dateColumn);
    }

    @Override
    protected void activate() {
        sortedList = new SortedList<>(model.getList());
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();
    }

    private void onDeactivateOpenOffer(OpenOffer openOffer) {
        if (model.isBootstrapped()) {
            model.onDeactivateOpenOffer(openOffer,
                    () -> {
                        log.debug("Deactivate offer was successful");
                    },
                    (message) -> {
                        log.error(message);
                        new Popup<>().warning(Res.get("offerbook.deactivateOffer.failed", message)).show();
                    });
        } else {
            new Popup<>().information(Res.get("popup.warning.notFullyConnected")).show();
        }
    }

    private void onActivateOpenOffer(OpenOffer openOffer) {
        if (model.isBootstrapped()) {
            model.onActivateOpenOffer(openOffer,
                    () -> {
                        log.debug("Activate offer was successful");
                    },
                    (message) -> {
                        log.error(message);
                        new Popup<>().warning(Res.get("offerbook.activateOffer.failed", message)).show();
                    });
        } else {
            new Popup<>().information(Res.get("popup.warning.notFullyConnected")).show();
        }
    }

    private void onRemoveOpenOffer(OpenOffer openOffer) {
        if (model.isBootstrapped()) {
            String key = "RemoveOfferWarning";
            if (DontShowAgainLookup.showAgain(key))
                new Popup<>().warning(Res.get("popup.warning.removeOffer", model.formatter.formatCoinWithCode(openOffer.getOffer().getMakerFee())))
                        .actionButtonText(Res.get("shared.removeOffer"))
                        .onAction(() -> doRemoveOpenOffer(openOffer))
                        .closeButtonText(Res.get("shared.dontRemoveOffer"))
                        .dontShowAgainId(key)
                        .show();
            else
                doRemoveOpenOffer(openOffer);
        } else {
            new Popup<>().information(Res.get("popup.warning.notFullyConnected")).show();
        }
    }

    private void doRemoveOpenOffer(OpenOffer openOffer) {
        model.onRemoveOpenOffer(openOffer,
                () -> {
                    log.debug("Remove offer was successful");

                    tableView.refresh();

                    String key = "WithdrawFundsAfterRemoveOfferInfo";
                    if (DontShowAgainLookup.showAgain(key))
                        //noinspection unchecked
                        new Popup<>().instruction(Res.get("offerbook.withdrawFundsHint", Res.get("navigation.funds.availableForWithdrawal")))
                                .actionButtonTextWithGoTo("navigation.funds.availableForWithdrawal")
                                .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class))
                                .dontShowAgainId(key)
                                .show();
                },
                (message) -> {
                    log.error(message);
                    new Popup<>().warning(Res.get("offerbook.removeOffer.failed", message)).show();
                });
    }

    private void setOfferIdColumnCellFactory() {
        offerIdColumn.setCellValueFactory((openOfferListItem) -> new ReadOnlyObjectWrapper<>(openOfferListItem.getValue()));
        offerIdColumn.setCellFactory(
                new Callback<TableColumn<OpenOfferListItem, OpenOfferListItem>, TableCell<OpenOfferListItem, OpenOfferListItem>>() {

                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(TableColumn<OpenOfferListItem,
                            OpenOfferListItem> column) {
                        return new TableCell<OpenOfferListItem, OpenOfferListItem>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    field = new HyperlinkWithIcon(model.getTradeId(item));
                                    field.setOnAction(event -> offerDetailsWindow.show(item.getOffer()));
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
        dateColumn.setCellValueFactory((openOfferListItem) -> new ReadOnlyObjectWrapper<>(openOfferListItem.getValue()));
        dateColumn.setCellFactory(
                new Callback<TableColumn<OpenOfferListItem, OpenOfferListItem>, TableCell<OpenOfferListItem,
                        OpenOfferListItem>>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(
                            TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<OpenOfferListItem, OpenOfferListItem>() {
                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null) {
                                    if (model.isDeactivated(item)) getStyleClass().add("offer-disabled");
                                    setGraphic(new AutoTooltipLabel(model.getDate(item)));
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
                new Callback<TableColumn<OpenOfferListItem, OpenOfferListItem>, TableCell<OpenOfferListItem,
                        OpenOfferListItem>>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(
                            TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<OpenOfferListItem, OpenOfferListItem>() {
                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (model.isDeactivated(item)) getStyleClass().add("offer-disabled");
                                setGraphic(new AutoTooltipLabel(model.getAmount(item)));
                            }
                        };
                    }
                });
    }

    private void setPriceColumnCellFactory() {
        priceColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        priceColumn.setCellFactory(
                new Callback<TableColumn<OpenOfferListItem, OpenOfferListItem>, TableCell<OpenOfferListItem,
                        OpenOfferListItem>>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(
                            TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<OpenOfferListItem, OpenOfferListItem>() {
                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (model.isDeactivated(item)) getStyleClass().add("offer-disabled");
                                setGraphic(new AutoTooltipLabel(model.getPrice(item)));
                            }
                        };
                    }
                });
    }

    private void setVolumeColumnCellFactory() {
        volumeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        volumeColumn.setCellFactory(
                new Callback<TableColumn<OpenOfferListItem, OpenOfferListItem>, TableCell<OpenOfferListItem,
                        OpenOfferListItem>>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(
                            TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<OpenOfferListItem, OpenOfferListItem>() {
                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null) {
                                    if (model.isDeactivated(item)) getStyleClass().add("offer-disabled");
                                    setGraphic(new AutoTooltipLabel(model.getVolume(item)));
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setDirectionColumnCellFactory() {
        directionColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        directionColumn.setCellFactory(
                new Callback<TableColumn<OpenOfferListItem, OpenOfferListItem>, TableCell<OpenOfferListItem,
                        OpenOfferListItem>>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(
                            TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<OpenOfferListItem, OpenOfferListItem>() {
                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (model.isDeactivated(item)) getStyleClass().add("offer-disabled");
                                setGraphic(new AutoTooltipLabel(model.getDirectionLabel(item)));
                            }
                        };
                    }
                });
    }

    private void setMarketColumnCellFactory() {
        marketColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        marketColumn.setCellFactory(
                new Callback<TableColumn<OpenOfferListItem, OpenOfferListItem>, TableCell<OpenOfferListItem,
                        OpenOfferListItem>>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(
                            TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<OpenOfferListItem, OpenOfferListItem>() {
                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (model.isDeactivated(item)) getStyleClass().add("offer-disabled");
                                setGraphic(new AutoTooltipLabel(model.getMarketLabel(item)));
                            }
                        };
                    }
                });
    }

    private void setDeactivateColumnCellFactory() {
        deactivateItemColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        deactivateItemColumn.setCellFactory(
                new Callback<TableColumn<OpenOfferListItem, OpenOfferListItem>, TableCell<OpenOfferListItem, OpenOfferListItem>>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<OpenOfferListItem, OpenOfferListItem>() {
                            final ImageView iconView = new ImageView();
                            Button button;

                            private void updateState(@NotNull OpenOffer openOffer) {
                                if (openOffer.isDeactivated()) {
                                    button.setText(Res.get("shared.activate"));
                                    iconView.setId("image-alert-round");
                                    button.setGraphic(iconView);
                                } else {
                                    button.setText(Res.get("shared.deactivate"));
                                    iconView.setId("image-green_circle");
                                    button.setGraphic(iconView);
                                }
                            }

                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    if (button == null) {
                                        button = new AutoTooltipButton();
                                        button.setGraphic(iconView);
                                        updateState(item.getOpenOffer());
                                        button.setMinWidth(70);
                                        setGraphic(button);
                                    }
                                    button.setOnAction(event -> {
                                        if (item.getOpenOffer().isDeactivated()) {
                                            onActivateOpenOffer(item.getOpenOffer());
                                        } else {
                                            onDeactivateOpenOffer(item.getOpenOffer());
                                        }
                                        updateState(item.getOpenOffer());
                                        tableView.refresh();
                                    });
                                } else {
                                    setGraphic(null);
                                    if (button != null) {
                                        button.setOnAction(null);
                                        button = null;
                                    }
                                }
                            }
                        };
                    }
                });
    }

    private void setRemoveColumnCellFactory() {
        removeItemColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        removeItemColumn.setCellFactory(
                new Callback<TableColumn<OpenOfferListItem, OpenOfferListItem>, TableCell<OpenOfferListItem, OpenOfferListItem>>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<OpenOfferListItem, OpenOfferListItem>() {
                            final ImageView iconView = new ImageView();
                            Button button;

                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    if (button == null) {
                                        button = new AutoTooltipButton(Res.get("shared.remove"));
                                        button.setMinWidth(70);
                                        iconView.setId("image-remove");
                                        button.setGraphic(iconView);
                                        setGraphic(button);
                                    }
                                    button.setOnAction(event -> onRemoveOpenOffer(item.getOpenOffer()));
                                } else {
                                    setGraphic(null);
                                    if (button != null) {
                                        button.setOnAction(null);
                                        button = null;
                                    }
                                }
                            }
                        };
                    }
                });
    }
}

