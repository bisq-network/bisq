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

package io.bitsquare.gui.main.portfolio.pending;

import io.bitsquare.common.viewfx.view.ActivatableViewAndModel;
import io.bitsquare.common.viewfx.view.FxmlView;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import java.util.Date;

import javax.inject.Inject;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.*;
import javafx.scene.layout.*;
import javafx.util.Callback;
import javafx.util.StringConverter;

@FxmlView
public class PendingTradesView extends ActivatableViewAndModel<VBox, PendingTradesViewModel> {

    @FXML AnchorPane tradeStepPane;

    @FXML TableView<PendingTradesListItem> table;

    @FXML TableColumn<PendingTradesListItem, Fiat> priceColumn;
    @FXML TableColumn<PendingTradesListItem, Fiat> tradeVolumeColumn;
    @FXML TableColumn<PendingTradesListItem, PendingTradesListItem> directionColumn;
    @FXML TableColumn<PendingTradesListItem, String> idColumn;
    @FXML TableColumn<PendingTradesListItem, Date> dateColumn;
    @FXML TableColumn<PendingTradesListItem, Coin> tradeAmountColumn;

    private ChangeListener<PendingTradesListItem> selectedItemChangeListener;
    private TradeSubView currentSubView;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PendingTradesView(PendingTradesViewModel model) {
        super(model);
    }

    @Override
    public void initialize() {
        setTradeIdColumnCellFactory();
        setDirectionColumnCellFactory();
        setAmountColumnCellFactory();
        setPriceColumnCellFactory();
        setVolumeColumnCellFactory();
        setDateColumnCellFactory();

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No pending trades available"));

        selectedItemChangeListener = (ov, oldValue, newValue) -> {
            if (newValue != null)
                addSubView();

            model.selectTrade(newValue);
        };
    }

    @Override
    public void doActivate() {
        table.setItems(model.getList());

        table.getSelectionModel().selectedItemProperty().addListener(selectedItemChangeListener);
        PendingTradesListItem selectedItem = model.getSelectedItem();
        if (selectedItem != null) {
            addSubView();

            // Select and focus selectedItem from model
            int index = table.getItems().indexOf(selectedItem);
            Platform.runLater(() -> {
                table.getSelectionModel().select(index);
                table.requestFocus();
                Platform.runLater(() -> table.getFocusModel().focus(index));
            });
        }
        else {
            removeSubView();
        }

        if (currentSubView != null)
            currentSubView.activate();
    }

    @Override
    public void doDeactivate() {
        table.getSelectionModel().selectedItemProperty().removeListener(selectedItemChangeListener);

        if (currentSubView != null)
            currentSubView.deactivate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addSubView() {
        removeSubView();

        if (model.isOfferer()) {
            if (model.isBuyOffer())
                currentSubView = new OffererAsBuyerSubView(model);
            else
                currentSubView = new OffererAsSellerSubView(model);
        }
        else {
            if (model.isBuyOffer())
                currentSubView = new TakerAsSellerSubView(model);
            else
                currentSubView = new TakerAsBuyerSubView(model);
        }
        currentSubView.activate();

        AnchorPane.setTopAnchor(currentSubView, 0d);
        AnchorPane.setRightAnchor(currentSubView, 0d);
        AnchorPane.setBottomAnchor(currentSubView, 0d);
        AnchorPane.setLeftAnchor(currentSubView, 0d);
        tradeStepPane.getChildren().setAll(currentSubView);
    }

    private void removeSubView() {
        if (currentSubView != null) {
            currentSubView.deactivate();
            tradeStepPane.getChildren().remove(currentSubView);
            currentSubView = null;
        }
    }

    private void openOfferDetails(String id) {
        // TODO Open popup with details view
        log.debug("Trade details " + id);
        Utilities.copyToClipboard(id);
        Popups.openWarningPopup("Under construction",
                "The trader ID was copied to the clipboard. " +
                        "Use that to identify your trading peer in the IRC chat room \n\n" +
                        "Later this will open a details popup but that is not implemented yet.");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // CellFactories
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setTradeIdColumnCellFactory() {
        idColumn.setCellFactory(
                new Callback<TableColumn<PendingTradesListItem, String>, TableCell<PendingTradesListItem, String>>() {

                    @Override
                    public TableCell<PendingTradesListItem, String> call(TableColumn<PendingTradesListItem,
                            String> column) {
                        return new TableCell<PendingTradesListItem, String>() {
                            private Hyperlink hyperlink;

                            @Override
                            public void updateItem(final String id, boolean empty) {
                                super.updateItem(id, empty);

                                if (id != null && !empty) {
                                    hyperlink = new Hyperlink(model.formatTradeId(id));
                                    hyperlink.setId("id-link");
                                    Tooltip.install(hyperlink, new Tooltip(model.formatTradeId(id)));
                                    hyperlink.setOnAction(event -> openOfferDetails(id));
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
        dateColumn.setCellFactory(TextFieldTableCell.<PendingTradesListItem, Date>forTableColumn(
                new StringConverter<Date>() {
                    @Override
                    public String toString(Date value) {
                        return model.formatDate(value);
                    }

                    @Override
                    public Date fromString(String string) {
                        return null;
                    }
                }));
    }

    private void setAmountColumnCellFactory() {
        tradeAmountColumn.setCellFactory(TextFieldTableCell.<PendingTradesListItem, Coin>forTableColumn(
                new StringConverter<Coin>() {
                    @Override
                    public String toString(Coin value) {
                        return model.formatTradeAmount(value);
                    }

                    @Override
                    public Coin fromString(String string) {
                        return null;
                    }
                }));
    }

    private void setPriceColumnCellFactory() {
        priceColumn.setCellFactory(TextFieldTableCell.<PendingTradesListItem, Fiat>forTableColumn(
                new StringConverter<Fiat>() {
                    @Override
                    public String toString(Fiat value) {
                        return model.formatPrice(value);
                    }

                    @Override
                    public Fiat fromString(String string) {
                        return null;
                    }
                }));

    }

    private void setVolumeColumnCellFactory() {
        tradeVolumeColumn.setCellFactory(TextFieldTableCell.<PendingTradesListItem, Fiat>forTableColumn(
                new StringConverter<Fiat>() {
                    @Override
                    public String toString(Fiat value) {
                        return model.formatTradeVolume(value);
                    }

                    @Override
                    public Fiat fromString(String string) {
                        return null;
                    }
                }));
    }

    private void setDirectionColumnCellFactory() {
        directionColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        directionColumn.setCellFactory(
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
                                    setText(model.evaluateDirection(item));
                                else
                                    setText(null);
                            }
                        };
                    }
                });
    }
}

