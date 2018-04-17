package bisq.desktop;

import javafx.stage.Stage;

public class PrimaryStageWrapper {

    private Stage stage;

    public Stage get() {
        return stage;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }
}
