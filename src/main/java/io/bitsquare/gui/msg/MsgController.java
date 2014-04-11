package io.bitsquare.gui.msg;

import io.bitsquare.gui.IChildController;
import io.bitsquare.gui.INavigationController;
import javafx.fxml.Initializable;

import java.net.URL;
import java.util.ResourceBundle;

public class MsgController implements Initializable, IChildController
{
    private INavigationController navigationController;

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {

    }

    @Override
    public void setNavigationController(INavigationController navigationController)
    {
        this.navigationController = navigationController;
    }

}

