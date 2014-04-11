package io.bitsquare.gui.components;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableView;

/**
 * This class allows to specify a percentage for the width of the column of a
 * TableView.
 *
 * @author twasyl
 */
public class PTableColumn<S, T> extends javafx.scene.control.TableColumn<S, T>
{

    private final DoubleProperty percentageWidth = new SimpleDoubleProperty(0);

    public PTableColumn()
    {
        tableViewProperty().addListener(new ChangeListener<TableView<S>>()
        {

            @Override
            public void changed(ObservableValue<? extends TableView<S>> ov, TableView<S> t, TableView<S> t1)
            {
                if (PTableColumn.this.prefWidthProperty().isBound())
                {
                    PTableColumn.this.prefWidthProperty().unbind();
                }
                if (percentageWidth.get() != 0)
                {
                    PTableColumn.this.prefWidthProperty().bind(t1.widthProperty().multiply(percentageWidth));
                }
                else
                {
                    double tempPercentageWidthLeft = 1;
                    for (int i = 0; i < t1.getColumns().size(); i++)
                    {
                        tempPercentageWidthLeft -= ((PTableColumn) t1.getColumns().get(i)).getPercentageWidth();
                    }
                    PTableColumn.this.prefWidthProperty().bind(t1.widthProperty().multiply(tempPercentageWidthLeft));
                }
            }
        });
    }

    public final DoubleProperty percentageWidthProperty()
    {
        return percentageWidth;
    }

    public final double getPercentageWidth()
    {
        return this.percentageWidthProperty().get();
    }

    public final void setPercentageWidth(double value) throws IllegalArgumentException
    {
        if (value >= 0 && value <= 1)
            this.percentageWidthProperty().set(value);
        else
            throw new IllegalArgumentException(String.format("The provided percentage width is not between 0.0 and 1.0. Value is: %1$s", value));
    }
}
