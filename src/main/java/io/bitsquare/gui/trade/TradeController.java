package io.bitsquare.gui.trade;

import io.bitsquare.BitSquare;
import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.IChildController;
import io.bitsquare.gui.INavigationController;
import io.bitsquare.gui.trade.orderbook.OrderBookController;
import io.bitsquare.trade.Direction;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class TradeController implements Initializable, INavigationController, IChildController
{
    @FXML
    private TabPane tabPane;

    private IChildController childController;
    private boolean orderbookCreated;
    private INavigationController navigationController;
    private OrderBookController orderBookController;

    @Override
    public IChildController navigateToView(String fxmlView, String title)
    {
        if (fxmlView.equals(INavigationController.TRADE__ORDER_BOOK) && orderbookCreated)
        {
            tabPane.getSelectionModel().select(0);
            return null;
        }

        FXMLLoader loader = new GuiceFXMLLoader();
        try
        {
            Pane view = loader.load(BitSquare.class.getResourceAsStream(fxmlView));
            childController = loader.getController();
            childController.setNavigationController(this);

            if (childController instanceof OrderBookController)
                orderBookController = (OrderBookController) childController;

            Tab tab = new Tab(title);
            tab.setContent(view);
            tabPane.getTabs().add(tab);

            if (fxmlView.equals(INavigationController.TRADE__ORDER_BOOK))
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
        navigateToView(INavigationController.TRADE__ORDER_BOOK, "Orderbook");
    }

    @Override
    public void setNavigationController(INavigationController navigationController)
    {

        this.navigationController = navigationController;
    }

    public void setDirection(Direction direction)
    {
        tabPane.getSelectionModel().select(0);
        orderBookController.setDirection(direction);
    }

}

