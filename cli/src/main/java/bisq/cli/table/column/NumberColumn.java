package bisq.cli.table.column;

abstract class NumberColumn<C extends NumberColumn<C, T>, T extends Number> extends AbstractColumn<C, T> implements Column<T> {

    public NumberColumn(String name, JUSTIFICATION justification) {
        super(name, justification);
    }

}
