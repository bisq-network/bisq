package io.bitsquare.gui.orders;

import io.bitsquare.gui.CachedViewController;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.ViewController;
import io.bitsquare.gui.components.CachingTabPane;
import io.bitsquare.storage.Persistence;
import java.net.URL;
import java.util.ResourceBundle;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrdersController extends CachedViewController
{
    private static final Logger log = LoggerFactory.getLogger(OrdersController.class);
    private static OrdersController INSTANCE;
    private final Persistence persistence;

    @Inject
    private OrdersController(Persistence persistence)
    {
        this.persistence = persistence;
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
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        super.initialize(url, rb);

        ((CachingTabPane) root).initialize(this, persistence, NavigationItem.OFFER.getFxmlUrl(), NavigationItem.PENDING_TRADE.getFxmlUrl(), NavigationItem.CLOSED_TRADE.getFxmlUrl());
    }

    @Override
    public void deactivate()
    {
        super.deactivate();
    }

    @Override
    public void activate()
    {
        super.activate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ViewController loadViewAndGetChildController(NavigationItem navigationItem)
    {
        childController = ((CachingTabPane) root).loadViewAndGetChildController(navigationItem.getFxmlUrl());
        return childController;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setSelectedTabIndex(int index)
    {
        log.trace("setSelectedTabIndex " + index);
        ((CachingTabPane) root).setSelectedTabIndex(index);
        persistence.write(this, "selectedTabIndex", index);
    }

}

