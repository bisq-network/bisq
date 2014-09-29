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

package io.bitsquare.gui.main.orders.offer;

import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.components.Popups;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.util.Callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OffersViewCB extends CachedViewCB<OffersPM> {
    private static final Logger log = LoggerFactory.getLogger(OffersViewCB.class);

    @FXML TableColumn<OfferListItem, OfferListItem> priceColumn, amountColumn, volumeColumn,
            directionColumn, dateColumn, offerIdColumn, removeItemColumn;
    @FXML TableView<OfferListItem> table;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private OffersViewCB(OffersPM presentationModel) {
        super(presentationModel);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setOfferIdColumnCellFactory();
        setDirectionColumnCellFactory();
        setAmountColumnCellFactory();
        setPriceColumnCellFactory();
        setVolumeColumnCellFactory();
        setDateColumnCellFactory();
        setRemoveColumnCellFactory();

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No open offers available"));

        super.initialize(url, rb);
    }

    @Override
    public void activate() {
        super.activate();

        table.setItems(presentationModel.getList());
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void removeOffer(OfferListItem item) {
        presentationModel.removeOffer(item);
    }

    private void openOfferDetails(OfferListItem item) {
        // TODO Open popup with details view
        log.debug("openOfferDetails " + item);
        Popups.openWarningPopup("Under construction",
                "This will open a details popup but that is not implemented yet.");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // CellFactories
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setOfferIdColumnCellFactory() {
        offerIdColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        offerIdColumn.setCellFactory(
                new Callback<TableColumn<OfferListItem, OfferListItem>, TableCell<OfferListItem, OfferListItem>>() {

                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(TableColumn<OfferListItem,
                            OfferListItem> column) {
                        return new TableCell<OfferListItem, OfferListItem>() {
                            private Hyperlink hyperlink;

                            @Override
                            public void updateItem(final OfferListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    hyperlink = new Hyperlink(presentationModel.getTradeId(item));
                                    hyperlink.setId("id-link");
                                    Tooltip.install(hyperlink, new Tooltip(presentationModel.getTradeId(item)));
                                    hyperlink.setOnAction(event -> openOfferDetails(item));
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

    private void setDateColumnCellFactory() {
        dateColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        dateColumn.setCellFactory(
                new Callback<TableColumn<OfferListItem, OfferListItem>, TableCell<OfferListItem,
                        OfferListItem>>() {
                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(
                            TableColumn<OfferListItem, OfferListItem> column) {
                        return new TableCell<OfferListItem, OfferListItem>() {
                            @Override
                            public void updateItem(final OfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(presentationModel.getDate(item));
                                else
                                    setText("");
                            }
                        };
                    }
                });
    }


    private void setAmountColumnCellFactory() {
        amountColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        amountColumn.setCellFactory(
                new Callback<TableColumn<OfferListItem, OfferListItem>, TableCell<OfferListItem,
                        OfferListItem>>() {
                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(
                            TableColumn<OfferListItem, OfferListItem> column) {
                        return new TableCell<OfferListItem, OfferListItem>() {
                            @Override
                            public void updateItem(final OfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(presentationModel.getAmount(item));
                            }
                        };
                    }
                });
    }

    private void setPriceColumnCellFactory() {
        priceColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        priceColumn.setCellFactory(
                new Callback<TableColumn<OfferListItem, OfferListItem>, TableCell<OfferListItem,
                        OfferListItem>>() {
                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(
                            TableColumn<OfferListItem, OfferListItem> column) {
                        return new TableCell<OfferListItem, OfferListItem>() {
                            @Override
                            public void updateItem(final OfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(presentationModel.getPrice(item));
                            }
                        };
                    }
                });
    }

    private void setVolumeColumnCellFactory() {
        volumeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        volumeColumn.setCellFactory(
                new Callback<TableColumn<OfferListItem, OfferListItem>, TableCell<OfferListItem,
                        OfferListItem>>() {
                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(
                            TableColumn<OfferListItem, OfferListItem> column) {
                        return new TableCell<OfferListItem, OfferListItem>() {
                            @Override
                            public void updateItem(final OfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(presentationModel.getVolume(item));
                                else
                                    setText("");
                            }
                        };
                    }
                });
    }

    private void setDirectionColumnCellFactory() {
        directionColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        directionColumn.setCellFactory(
                new Callback<TableColumn<OfferListItem, OfferListItem>, TableCell<OfferListItem,
                        OfferListItem>>() {
                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(
                            TableColumn<OfferListItem, OfferListItem> column) {
                        return new TableCell<OfferListItem, OfferListItem>() {
                            @Override
                            public void updateItem(final OfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(presentationModel.getDirectionLabel(item));
                            }
                        };
                    }
                });
    }


    private void setRemoveColumnCellFactory() {
        removeItemColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        removeItemColumn.setCellFactory(
                new Callback<TableColumn<OfferListItem, OfferListItem>, TableCell<OfferListItem, OfferListItem>>() {
                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(TableColumn<OfferListItem,
                            OfferListItem> directionColumn) {
                        return new TableCell<OfferListItem, OfferListItem>() {
                            final ImageView iconView = new ImageView();
                            final Button button = new Button();

                            {
                                iconView.setId("image-remove");
                                button.setText("Remove");
                                button.setGraphic(iconView);
                                button.setMinWidth(70);
                            }

                            @Override
                            public void updateItem(final OfferListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null) {
                                    button.setOnAction(event -> removeOffer(item));
                                    setGraphic(button);
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

