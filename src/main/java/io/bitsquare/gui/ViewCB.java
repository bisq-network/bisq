package io.bitsquare.gui;

import java.net.URL;

import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Non caching version for code behind classes using the PM pattern
 *
 * @param <T> The PresentationModel used in that class
 */
public class ViewCB<T extends PresentationModel> implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(ViewCB.class);

    protected T presentationModel;
    //TODO Initializable has to be changed to CodeBehind<? extends PresentationModel> when all UIs are updated
    protected Initializable childController;
    //TODO Initializable has to be changed to CodeBehind<? extends PresentationModel> when all UIs are updated
    protected Initializable parentController;
    @FXML protected Parent root;

    public ViewCB(T presentationModel) {
        this.presentationModel = presentationModel;
    }

    public ViewCB() {
    }

    /**
     * Get called form GUI framework when the UI is ready.
     *
     * @param url
     * @param rb
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        log.trace("Lifecycle: initialize " + this.getClass().getSimpleName());
        root.sceneProperty().addListener((ov, oldValue, newValue) -> {
            // we got removed from the scene
            // lets terminate
            if (oldValue != null && newValue == null)
                terminate();

        });

        presentationModel.initialized();
        presentationModel.activate();
    }

    /**
     * Called automatically when view gets removed. Used for house keeping (removing listeners,
     * stopping timers or animations,...).
     */
    public void terminate() {
        log.trace("Lifecycle: terminate " + this.getClass().getSimpleName());
        if (childController != null)
            ((ViewCB<? extends PresentationModel>) childController).terminate();

        presentationModel.deactivate();
        presentationModel.terminate();
    }

    /**
     * @param parentController Controller who has created this.getClass().getSimpleName() instance (via
     *                         navigateToView/FXMLLoader).
     */
    public void setParentController(Initializable parentController) {
        log.trace("Lifecycle: setParentController " + this.getClass().getSimpleName() + " / parent = " +
                parentController);
        this.parentController = parentController;
    }

    /**
     * @param navigationItem NavigationItem to be loaded.
     * @return The ViewController of the loaded view.
     */
    public Initializable loadView(NavigationItem navigationItem) {
        log.trace("Lifecycle: loadViewAndGetChildController " + this.getClass().getSimpleName() + " / navigationItem " +
                "= " + navigationItem);
        return null;
    }

}
