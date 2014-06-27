package io.bitsquare.gui.funds.transactions;

import com.google.bitcoin.core.Transaction;
import com.google.inject.Inject;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.Hibernate;
import io.bitsquare.gui.NavigationController;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class TransactionsController implements Initializable, ChildController, Hibernate
{
    private static final Logger log = LoggerFactory.getLogger(TransactionsController.class);

    private WalletFacade walletFacade;
    protected ObservableList<TransactionsListItem> transactionsListItems;

    @FXML
    private TableView<TransactionsListItem> tableView;
    @FXML
    private TableColumn<String, TransactionsListItem> dateColumn, addressColumn, amountColumn, typeColumn, confidenceColumn;
    @FXML
    private Button addNewAddressButton;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TransactionsController(WalletFacade walletFacade)
    {
        this.walletFacade = walletFacade;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        awake();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        setAddressColumnCellFactory();
        setConfidenceColumnCellFactory();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: ChildController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
    }

    @Override
    public void cleanup()
    {
        for (int i = 0; i < transactionsListItems.size(); i++)
        {
            transactionsListItems.get(i).cleanup();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Hibernate
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void sleep()
    {
        cleanup();
    }

    @Override
    public void awake()
    {
        List<Transaction> transactions = walletFacade.getWallet().getRecentTransactions(10000, true);
        transactionsListItems = FXCollections.observableArrayList();
        for (int i = 0; i < transactions.size(); i++)
        {
            transactionsListItems.add(new TransactionsListItem(transactions.get(i), walletFacade));
        }

        tableView.setItems(transactionsListItems);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Cell factories
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setAddressColumnCellFactory()
    {
        addressColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper(addressListItem.getValue()));
        addressColumn.setCellFactory(new Callback<TableColumn<String, TransactionsListItem>, TableCell<String, TransactionsListItem>>()
        {
            @Override
            public TableCell<String, TransactionsListItem> call(TableColumn<String, TransactionsListItem> column)
            {
                return new TableCell<String, TransactionsListItem>()
                {
                    Hyperlink hyperlink;

                    @Override
                    public void updateItem(final TransactionsListItem item, boolean empty)
                    {
                        super.updateItem(item, empty);

                        if (item != null && !empty)
                        {
                            hyperlink = new Hyperlink(item.getAddressString());
                            hyperlink.setId("id-link");
                            hyperlink.setOnAction(new EventHandler<ActionEvent>()
                            {
                                @Override
                                public void handle(ActionEvent event)
                                {
                                    log.info("Show trade details " + item.getAddressString());
                                }
                            });
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

    private void setConfidenceColumnCellFactory()
    {
        confidenceColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper(addressListItem.getValue()));
        confidenceColumn.setCellFactory(new Callback<TableColumn<String, TransactionsListItem>, TableCell<String, TransactionsListItem>>()
        {
            @Override
            public TableCell<String, TransactionsListItem> call(TableColumn<String, TransactionsListItem> column)
            {
                return new TableCell<String, TransactionsListItem>()
                {

                    @Override
                    public void updateItem(final TransactionsListItem item, boolean empty)
                    {
                        super.updateItem(item, empty);

                        if (item != null && !empty)
                        {
                            setGraphic(item.getProgressIndicator());
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

