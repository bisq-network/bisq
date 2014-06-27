package io.bitsquare.gui.market;

import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.NavigationViewURL;
import io.bitsquare.gui.market.orderbook.OrderBookController;
import io.bitsquare.locale.Localisation;
import io.bitsquare.trade.Direction;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;

public class MarketController implements Initializable, NavigationController, ChildController
{
    private ChildController childController;
    private boolean orderbookCreated;
    private NavigationController navigationController;
    private OrderBookController orderBookController;

    @FXML
    private TabPane tabPane;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        navigateToView(NavigationViewURL.ORDER_BOOK);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: NavigationController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ChildController navigateToView(NavigationItem navigationItem)
    {
        return navigateToView(navigationItem);
    }

    @Override
    public ChildController navigateToView(String fxmlView)
    {
        if (fxmlView.equals(NavigationViewURL.ORDER_BOOK) && orderbookCreated)
        {
            tabPane.getSelectionModel().select(0);
            return null;
        }

        final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource(fxmlView), Localisation.getResourceBundle());
        try
        {
            Pane view = loader.load();
            childController = loader.getController();
            childController.setNavigationController(this);

            if (childController instanceof OrderBookController)
                orderBookController = (OrderBookController) childController;

            Tab tab = new Tab("Orderbook");
            tab.setContent(view);
            tabPane.getTabs().add(tab);

            if (fxmlView.equals(NavigationViewURL.ORDER_BOOK))
            {
                tab.setClosable(false);
                orderbookCreated = true;
            }

            tabPane.getSelectionModel().select(tabPane.getTabs().size() - 1);

            return childController;
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: ChildController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setNavigationController(NavigationController navigationController)
    {

        this.navigationController = navigationController;
    }

    @Override
    public void cleanup()
    {
        if (orderBookController != null)
        {
            orderBookController.cleanup();
            orderBookController = null;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setDirection(Direction direction)
    {
        tabPane.getSelectionModel().select(0);
        if (orderBookController != null)
            orderBookController.setDirection(direction);
    }

}

