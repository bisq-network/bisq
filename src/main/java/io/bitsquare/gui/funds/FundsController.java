package io.bitsquare.gui.funds;

import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.Hibernate;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.components.CachingTabPane;
import io.bitsquare.storage.Persistence;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FundsController implements Initializable, ChildController, NavigationController, Hibernate
{
    private static final Logger log = LoggerFactory.getLogger(FundsController.class);
    private final Persistence persistence;
    private ChildController childController;

    @FXML
    private CachingTabPane root;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private FundsController(Persistence persistence)
    {
        this.persistence = persistence;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        root.initialize(this, persistence, NavigationItem.DEPOSIT.getFxmlUrl(), NavigationItem.WITHDRAWAL.getFxmlUrl(), NavigationItem.TRANSACTIONS.getFxmlUrl());
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
        root.cleanup();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: NavigationController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ChildController navigateToView(NavigationItem navigationItem)
    {
        childController = root.navigateToView(navigationItem.getFxmlUrl());
        return childController;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Hibernate
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void sleep()
    {
        if (childController instanceof Hibernate)
            ((Hibernate) childController).sleep();
    }

    @Override
    public void awake()
    {
        if (childController instanceof Hibernate)
            ((Hibernate) childController).awake();
    }
}

