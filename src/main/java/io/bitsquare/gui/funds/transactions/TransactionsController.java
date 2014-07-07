package io.bitsquare.gui.funds.transactions;

import com.google.bitcoin.core.Transaction;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.Hibernate;
import io.bitsquare.gui.NavigationController;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.util.Callback;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionsController implements Initializable, ChildController, Hibernate
{
    private static final Logger log = LoggerFactory.getLogger(TransactionsController.class);

    private final WalletFacade walletFacade;
    private ObservableList<TransactionsListItem> transactionsListItems;

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
    private TransactionsController(WalletFacade walletFacade)
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
        for (TransactionsListItem transactionsListItem : transactionsListItems)
        {
            transactionsListItem.cleanup();
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
        transactionsListItems.addAll(transactions.stream().map(transaction -> new TransactionsListItem(transaction, walletFacade)).collect(Collectors.toList()));

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
                            hyperlink.setOnAction(event -> log.info("Show trade details " + item.getAddressString()));
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

