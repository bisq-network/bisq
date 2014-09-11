package io.bitsquare.gui;

public class PresentationModel<T extends UIModel> {

    protected T model;

    public PresentationModel(T model) {
        this.model = model;
    }

    // TODO Still open question if we enforce a model or not? For small UIs it might be too much overhead.
    public PresentationModel() {
    }

    public void initialize() {
        if (model != null)
            model.initialize();

        activate();
    }

    public void activate() {
        if (model != null)
            model.activate();
    }

    public void deactivate() {
        if (model != null)
            model.deactivate();
    }

    public void terminate() {
        if (model != null)
            model.terminate();
        
        deactivate();
    }
}
