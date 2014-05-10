package io.bitsquare.gui.settings;

import com.google.inject.Inject;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.settings.Settings;
import io.bitsquare.storage.Storage;
import io.bitsquare.user.User;
import javafx.fxml.Initializable;

import java.net.URL;
import java.util.ResourceBundle;

public class SettingsController implements Initializable, ChildController
{
    private User user;
    private Settings settings;
    private Storage storage;

    private NavigationController navigationController;

    @Inject
    public SettingsController(User user, Settings settings, Storage storage)
    {
        this.user = user;
        this.settings = settings;
        this.storage = storage;
    }

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

