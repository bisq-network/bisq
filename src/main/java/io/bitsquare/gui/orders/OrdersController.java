package io.bitsquare.gui.orders;

import com.google.inject.Inject;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.components.LazyLoadingTabPane;
import io.bitsquare.storage.Storage;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrdersController implements Initializable, ChildController, NavigationController
{
    private static final Logger log = LoggerFactory.getLogger(OrdersController.class);
    private static int SELECTED_TAB_INDEX = -1;
    private static OrdersController INSTANCE;
    private final Storage storage;
    @FXML
    private LazyLoadingTabPane tabPane;

    @Inject
    private OrdersController(Storage storage)
    {
        this.storage = storage;
        INSTANCE = this;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static OrdersController GET_INSTANCE()
    {
        return INSTANCE;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setSelectedTabIndex(int index)
    {
        log.trace("setSelectedTabIndex " + index);
        tabPane.setSelectedTabIndex(index);
        storage.write(this.getClass().getName() + ".selectedTabIndex", index);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        log.trace("initialize ");
        tabPane.initialize(this, storage, NavigationItem.OFFER.getFxmlUrl(), NavigationItem.PENDING_TRADE.getFxmlUrl(), NavigationItem.CLOSED_TRADE.getFxmlUrl());
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
        tabPane.cleanup();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: NavigationController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ChildController navigateToView(NavigationItem navigationItem)
    {
        return tabPane.navigateToView(navigationItem.getFxmlUrl());
    }

}

