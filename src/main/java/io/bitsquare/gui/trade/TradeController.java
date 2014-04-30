package io.bitsquare.gui.trade;

import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.trade.orderbook.OrderBookController;
import io.bitsquare.gui.util.Localisation;
import io.bitsquare.trade.Direction;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class TradeController implements Initializable, NavigationController, ChildController
{
    @FXML
    private TabPane tabPane;

    private ChildController childController;
    private boolean orderbookCreated;
    private NavigationController navigationController;
    private OrderBookController orderBookController;

    @Override
    public ChildController navigateToView(String fxmlView, String title)
    {
        if (fxmlView.equals(NavigationController.TRADE__ORDER_BOOK) && orderbookCreated)
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

            Tab tab = new Tab(title);
            tab.setContent(view);
            tabPane.getTabs().add(tab);

            if (fxmlView.equals(NavigationController.TRADE__ORDER_BOOK))
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

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        navigateToView(NavigationController.TRADE__ORDER_BOOK, "Orderbook");
    }

    @Override
    public void setNavigationController(NavigationController navigationController)
    {

        this.navigationController = navigationController;
    }

    public void setDirection(Direction direction)
    {
        tabPane.getSelectionModel().select(0);
        orderBookController.setDirection(direction);
    }

    public void cleanup()
    {
        if (orderBookController != null)
        {
            orderBookController.cleanup();
            orderBookController = null;
        }
    }

}

