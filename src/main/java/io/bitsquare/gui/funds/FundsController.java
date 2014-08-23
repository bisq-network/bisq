package io.bitsquare.gui.funds;

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

public class FundsController extends CachedViewController
{
    private static final Logger log = LoggerFactory.getLogger(FundsController.class);
    private final Persistence persistence;
    private ViewController childController;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private FundsController(Persistence persistence)
    {
        this.persistence = persistence;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        super.initialize(url, rb);

        ((CachingTabPane) root).initialize(this, persistence, NavigationItem.DEPOSIT.getFxmlUrl(), NavigationItem.WITHDRAWAL.getFxmlUrl(), NavigationItem.TRANSACTIONS.getFxmlUrl());
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

}

