package io.bitsquare.gui.funds;

import com.google.inject.Inject;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.components.LazyLoadingTabPane;
import io.bitsquare.storage.Storage;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ResourceBundle;

public class FundsController implements Initializable, ChildController, NavigationController
{
    private static final Logger log = LoggerFactory.getLogger(FundsController.class);
    private Storage storage;

    @FXML
    private LazyLoadingTabPane tabPane;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FundsController(Storage storage)
    {
        this.storage = storage;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        tabPane.initialize(this, storage, NavigationController.DEPOSIT, NavigationController.WITHDRAWAL);
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
    public ChildController navigateToView(String fxmlView)
    {
        return navigateToView(fxmlView, "");
    }

    @Override
    public ChildController navigateToView(String fxmlView, String title)
    {
        return tabPane.navigateToView(fxmlView);
    }

}

