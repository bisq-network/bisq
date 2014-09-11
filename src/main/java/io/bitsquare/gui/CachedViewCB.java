package io.bitsquare.gui;

import java.net.URL;

import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * If caching is used for loader we use the CachedViewController for turning the controller into sleep mode if not
 * active and awake it at reactivation.
 * * @param <T>       The PresentationModel used in that class
 */
public class CachedViewCB<T extends PresentationModel> extends ViewCB<T> {
    private static final Logger log = LoggerFactory.getLogger(CachedViewCB.class);

    public CachedViewCB(T presentationModel) {
        super(presentationModel);
    }

    /**
     * Get called form GUI framework when the UI is ready.
     * In caching controllers the initialize is only used for static UI setup.
     * The activate() method is called to start resources like.
     *
     * @param url
     * @param rb
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        log.trace("Lifecycle: initialize " + this.getClass().getSimpleName());
        if (root != null) {
            root.sceneProperty().addListener((ov, oldValue, newValue) -> {
                // we got removed from the scene
                // lets terminate
                log.trace("Lifecycle: sceneProperty changed: " + this.getClass().getSimpleName() + " / oldValue=" +
                        oldValue + " / newValue=" + newValue);
                if (oldValue == null && newValue != null) activate();
                else if (oldValue != null && newValue == null) deactivate();
            });
        }

        if (presentationModel != null)
            presentationModel.initialize();
    }

    /**
     * Used to activate resources (adding listeners, starting timers or animations,...)
     */
    public void activate() {
        log.trace("Lifecycle: activate " + this.getClass().getSimpleName());
        if (childController instanceof CachedViewCB) ((CachedViewCB) childController).activate();

        if (presentationModel != null)
            presentationModel.activate();
    }

    /**
     * Used for deactivating resources (removing listeners, stopping timers or animations,...)
     */
    public void deactivate() {
        log.trace("Lifecycle: deactivate " + this.getClass().getSimpleName());
        if (childController instanceof CachedViewCB) ((CachedViewCB) childController).deactivate();

        if (presentationModel != null)
            presentationModel.deactivate();
    }

    /**
     * In caching controllers the terminate calls the deactivate method.
     */
    @Override
    public void terminate() {
        log.trace("Lifecycle: terminate " + this.getClass().getSimpleName());
        super.terminate();

        if (presentationModel != null)
            presentationModel.terminate();
    }

}
