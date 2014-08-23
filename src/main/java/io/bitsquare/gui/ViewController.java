package io.bitsquare.gui;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all controllers.
 */
public abstract class ViewController implements Initializable
{
    private static final Logger log = LoggerFactory.getLogger(ViewController.class);

    protected ViewController childController;
    protected ViewController parentController;

    @FXML protected Parent root;

    /**
     * Get called form GUI framework when the UI is ready.
     *
     * @param url
     * @param rb
     */
    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        log.trace("Lifecycle: initialize " + this.getClass().getSimpleName());
        root.sceneProperty().addListener((ov, oldValue, newValue) -> {
            // we got removed from the scene
            // lets terminate
            if (oldValue != null && newValue == null) terminate();
        });
    }

    /**
     * Called automatically when view gets removed. Used for house keeping (removing listeners, stopping timers or animations,...).
     */
    public void terminate()
    {
        log.trace("Lifecycle: terminate " + this.getClass().getSimpleName());
        if (childController != null) childController.terminate();
    }

    /**
     * @param parentController Controller who has created this.getClass().getSimpleName() instance (via navigateToView/FXMLLoader).
     */
    public void setParentController(ViewController parentController)
    {
        log.trace("Lifecycle: setParentController " + this.getClass().getSimpleName() + " / parent = " + parentController);
        this.parentController = parentController;
    }

    /**
     * @param navigationItem NavigationItem to be loaded.
     * @return The ViewController of the loaded view.
     */
    public ViewController loadViewAndGetChildController(NavigationItem navigationItem)
    {
        log.trace("Lifecycle: loadViewAndGetChildController " + this.getClass().getSimpleName() + " / navigationItem = " + navigationItem);
        return null;
    }

}
