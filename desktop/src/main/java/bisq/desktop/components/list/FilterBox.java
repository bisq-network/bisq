package bisq.desktop.components.list;

import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.InputTextField;
import bisq.desktop.util.filtering.FilterableListItem;

import bisq.core.locale.Res;

import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;

import javafx.geometry.Insets;
import javafx.beans.value.ChangeListener;
import javafx.collections.transformation.FilteredList;

public class FilterBox extends HBox {
    private final InputTextField textField;
    private FilteredList<? extends FilterableListItem> filteredList;

    private ChangeListener<String> listener;

    public FilterBox() {
        super();
        setSpacing(5.0);

        AutoTooltipLabel label = new AutoTooltipLabel(Res.get("shared.filter"));
        HBox.setMargin(label, new Insets(5.0, 0, 0, 10.0));

        textField = new InputTextField();
        textField.setMinWidth(500);

        getChildren().addAll(label, textField);
    }

    public void initialize(FilteredList<? extends FilterableListItem> filteredList,
                           TableView<? extends FilterableListItem> tableView) {
        this.filteredList = filteredList;
        listener = (observable, oldValue, newValue) -> {
            tableView.getSelectionModel().clearSelection();
            applyFilteredListPredicate(textField.getText());
        };
    }

    public void activate() {
        textField.textProperty().addListener(listener);
        applyFilteredListPredicate(textField.getText());
    }

    public void deactivate() {
        textField.textProperty().removeListener(listener);
    }

    private void applyFilteredListPredicate(String filterString) {
        filteredList.setPredicate(item -> item.match(filterString));
    }
}
