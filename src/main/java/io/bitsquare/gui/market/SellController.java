package io.bitsquare.gui.market;

import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.components.ValidatingTextField;
import io.bitsquare.gui.market.orderbook.OrderBookController;
import io.bitsquare.trade.Direction;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;

public class SellController implements Initializable, NavigationController, ChildController
{
    private static final Logger log = LoggerFactory.getLogger(SellController.class);

    protected OrderBookController orderBookController;
    protected GuiceFXMLLoader orderBookLoader;

    @FXML
    protected TabPane tabPane;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        // TODO find better solution
        // Textfield focus out triggers validation, use runLater as quick fix...
        tabPane.getSelectionModel().selectedIndexProperty().addListener((observableValue) -> Platform.runLater(() -> ValidatingTextField.hidePopover()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: NavigationController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ChildController navigateToView(NavigationItem navigationItem)
    {
        if (navigationItem == NavigationItem.ORDER_BOOK)
        {
            return loadOrderBook();
        }
        else
        {
            checkArgument(navigationItem.equals(NavigationItem.CREATE_OFFER) || navigationItem.equals(NavigationItem.TAKE_OFFER));

            // CreateOffer and TakeOffer must not be cached by GuiceFXMLLoader as we cannot use a view multiple times in different graphs
            GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource(navigationItem.getFxmlUrl()), false);
            try
            {
                final Parent view = loader.load();
                ChildController childController = loader.getController();
                childController.setNavigationController(this);

                String tabLabel = navigationItem.equals(NavigationItem.CREATE_OFFER) ? "Create offer" : "Take offer";
                final Tab tab = new Tab(tabLabel);
                tab.setContent(view);
                tabPane.getTabs().add(tab);
                tabPane.getSelectionModel().select(tabPane.getTabs().size() - 1);

                return childController;
            } catch (IOException e)
            {
                e.printStackTrace();
                log.error(e.getMessage());
            }
            return null;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: ChildController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
        navigateToView(NavigationItem.ORDER_BOOK);
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
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void applyDirection()
    {
        orderBookController.applyDirection(Direction.SELL);
    }

    protected ChildController loadOrderBook()
    {
        // Orderbook must not be cached by GuiceFXMLLoader as we use 2 instances for sell and buy screens.
        if (orderBookLoader == null)
        {
            orderBookLoader = new GuiceFXMLLoader(getClass().getResource(NavigationItem.ORDER_BOOK.getFxmlUrl()), false);
            try
            {
                final Parent view = orderBookLoader.load();
                final Tab tab = new Tab("Orderbook");
                tab.setClosable(false);
                tab.setContent(view);
                tabPane.getTabs().add(tab);
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        orderBookController = orderBookLoader.getController();
        orderBookController.setNavigationController(this);
        applyDirection();
        return orderBookController;
    }

}

