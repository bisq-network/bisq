package io.bitsquare.gui.orders.offer;

import com.google.inject.Inject;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.util.Icons;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.Trading;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class OfferController implements Initializable, ChildController
{
    private static final Logger log = LoggerFactory.getLogger(OfferController.class);
    protected ObservableList<OfferListItem> offerListItems = FXCollections.observableArrayList();

    @FXML
    private TableColumn<String, OfferListItem> offerIdColumn, dateColumn, amountColumn, priceColumn, volumeColumn, removeColumn;
    @FXML
    private TableView offerTable;
    private Trading trading;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OfferController(Trading trading)
    {
        this.trading = trading;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        setOfferIdColumnColumnCellFactory();
        setRemoveColumnCellFactory();

        Map<String, Offer> offerMap = trading.getOffers();
        List<Offer> offerList = new ArrayList<>(offerMap.values());
        for (int i = 0; i < offerList.size(); i++)
        {
            offerListItems.add(new OfferListItem(offerList.get(i)));
        }
        offerTable.setItems(offerListItems);
        offerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: ChildController
    ///////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void setNavigationController(NavigationController navigationController)
    {
        log.debug("setNavigationController" + this);
    }

    @Override
    public void cleanup()
    {
        log.debug("cleanup" + this);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // GUI Event handlers
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private Methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    private void removeOffer(Offer offer)
    {
        try
        {
            trading.removeOffer(offer);
        } catch (IOException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table columns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setOfferIdColumnColumnCellFactory()
    {
        offerIdColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper(offerListItem.getValue()));
        offerIdColumn.setCellFactory(new Callback<TableColumn<String, OfferListItem>, TableCell<String, OfferListItem>>()
        {
            @Override
            public TableCell<String, OfferListItem> call(TableColumn<String, OfferListItem> column)
            {
                return new TableCell<String, OfferListItem>()
                {
                    Hyperlink hyperlink;

                    @Override
                    public void updateItem(final OfferListItem item, boolean empty)
                    {
                        super.updateItem(item, empty);

                        if (item != null)
                        {
                            hyperlink = new Hyperlink(item.getOfferId());
                            //hyperlink.getStyleClass().setAll("aaa");
                            if (item != null)
                            {
                                Tooltip tooltip = new Tooltip(item.getOfferId());
                                Tooltip.install(hyperlink, tooltip);
                                hyperlink.setOnAction(new EventHandler<ActionEvent>()
                                {
                                    @Override
                                    public void handle(ActionEvent event)
                                    {
                                        log.info("Show offer details " + item.getOfferId());
                                    }
                                });

                               /* hyperlink.setOnMouseEntered(new EventHandler<MouseEvent>()
                                {
                                    @Override
                                    public void handle(MouseEvent event)
                                    {
                                        log.info("Show offer details " + item.getOfferId());
                                    }
                                });  */
                            }
                            setGraphic(hyperlink);
                        }
                        else
                        {
                            setGraphic(null);
                            setId(null);
                        }
                    }
                };
            }
        });
    }

    private void setRemoveColumnCellFactory()
    {
        removeColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper(offerListItem.getValue()));
        removeColumn.setCellFactory(new Callback<TableColumn<String, OfferListItem>, TableCell<String, OfferListItem>>()
        {
            @Override
            public TableCell<String, OfferListItem> call(TableColumn<String, OfferListItem> directionColumn)
            {
                return new TableCell<String, OfferListItem>()
                {
                    final ImageView iconView = Icons.getIconImageView(Icons.REMOVE);
                    final Button button = new Button();

                    {
                        button.setText("Remove");
                        button.setGraphic(iconView);
                        button.setMinWidth(70);
                    }

                    @Override
                    public void updateItem(final OfferListItem offerListItem, boolean empty)
                    {
                        super.updateItem(offerListItem, empty);

                        if (offerListItem != null)
                        {
                            button.setOnAction(event -> removeOffer(offerListItem.getOffer()));
                            setGraphic(button);
                        }
                        else
                        {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
    }
}

