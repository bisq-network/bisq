package bisq.desktop.util.filtering;

public interface FilterableListItem {
    boolean match(String filterString);
}
