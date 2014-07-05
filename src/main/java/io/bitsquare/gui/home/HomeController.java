package io.bitsquare.gui.home;

import io.bitsquare.BitSquare;
import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.arbitrators.registration.ArbitratorRegistrationController;
import io.bitsquare.locale.Localisation;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class HomeController implements Initializable, ChildController, NavigationController
{
    @FXML
    public Pane rootContainer;
    private ArbitratorRegistrationController arbitratorRegistrationController;

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        // navigateToView(NavigationController.ARBITRATOR_REGISTRATION, "Registration as Arbitrator");
    }

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
    }

    @Override
    public void cleanup()
    {

    }
    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: NavigationController
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public ChildController navigateToView(NavigationItem navigationItem)
    {
        if (arbitratorRegistrationController != null)
        {
            arbitratorRegistrationController.cleanup();
        }

        final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource(navigationItem.getFxmlUrl()), Localisation.getResourceBundle());
        try
        {
            final Node view = loader.load();
            arbitratorRegistrationController = loader.getController();
            arbitratorRegistrationController.setNavigationController(this);

            final Stage rootStage = BitSquare.getStage();
            final Stage stage = new Stage();
            stage.setTitle("Arbitrator");
            stage.setMinWidth(800);
            stage.setMinHeight(400);
            stage.setWidth(800);
            stage.setHeight(600);
            stage.setX(rootStage.getX() + 50);
            stage.setY(rootStage.getY() + 50);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(rootStage);
            Scene scene = new Scene((Parent) view, 800, 600);
            stage.setScene(scene);
            stage.show();

            return arbitratorRegistrationController;
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    @FXML
    public void onArbitratorRegistration()
    {
        navigateToView(NavigationItem.ARBITRATOR_REGISTRATION);
    }

    @FXML
    public void onArbitratorEdit()
    {
        navigateToView(NavigationItem.ARBITRATOR_REGISTRATION);
        arbitratorRegistrationController.setEditMode(true);
    }


}

