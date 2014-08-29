package io.bitsquare.gui;

public class PresentationModel<T extends UIModel> {

    private T model;

    public T model() {
        return (T) model;
    }

    public PresentationModel(T model) {
        this.model = model;
    }

    public PresentationModel() {
    }

    public void initialized() {
        model.initialized();
        activate();
    }

    public void activate() {
        model.activate();
    }

    public void deactivate() {
        model.deactivate();
    }

    public void terminate() {
        model.terminate();
        deactivate();
    }

}
