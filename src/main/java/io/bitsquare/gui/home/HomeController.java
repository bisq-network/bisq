package io.bitsquare.gui.home;

import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.Pane;

import java.net.URL;
import java.util.ResourceBundle;

public class HomeController implements Initializable, ChildController
{
    private NavigationController navigationController;

    @FXML
    public Pane rootContainer;

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {

    }

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
        this.navigationController = navigationController;
    }

    @Override
    public void cleanup()
    {

    }

}

