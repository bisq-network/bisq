package bisq.desktop.main.presentation;

import bisq.core.user.Preferences;

import javax.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import javafx.collections.MapChangeListener;

public class DaoPresentation {

    private final Preferences preferences;
    public static final String DAO_NEWS = "daoNewsVersion0.9.4";

    private final SimpleBooleanProperty showNotification = new SimpleBooleanProperty(true);

    @Inject
    public DaoPresentation(Preferences preferences) {
        this.preferences = preferences;
        preferences.getDontShowAgainMapAsObservable().addListener((MapChangeListener<? super String, ? super Boolean>) change -> {
            if (change.getKey().equals(DAO_NEWS)) {
                showNotification.set(!change.wasAdded());
            }
        });
    }

    public BooleanProperty getShowDaoUpdatesNotification() {
        return showNotification;
    }

    public void setup() {
        showNotification.set(preferences.showAgain(DAO_NEWS));
    }
}
