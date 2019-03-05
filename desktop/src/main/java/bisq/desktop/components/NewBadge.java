package bisq.desktop.components;

import bisq.core.locale.Res;
import bisq.core.user.Preferences;

import com.jfoenix.controls.JFXBadge;

import javafx.scene.Node;

import javafx.collections.MapChangeListener;

public class NewBadge extends JFXBadge {

    private final String key;

    public NewBadge(Node control, String key, Preferences preferences) {
        super(control);

        this.key = key;

        setText(Res.get("shared.new"));
        getStyleClass().add("new");

        setEnabled(!preferences.getDontShowAgainMap().containsKey(key));
        refreshBadge();

        preferences.getDontShowAgainMapAsObservable().addListener((MapChangeListener<? super String, ? super Boolean>) change -> {
            if (change.getKey().equals(key)) {
                setEnabled(!change.wasAdded());
                refreshBadge();
            }
        });
    }
}
