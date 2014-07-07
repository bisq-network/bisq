package io.bitsquare.util;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.Initializable;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;

public class Controller implements Initializable
{

    public GridPane root;

    public void initialize(URL url, ResourceBundle rb)
    {
        BorderPane borderPane = new BorderPane();
        Text text = new Text("BEFORE");
        borderPane.setCenter(text);

        MenuBar menuBar = new MenuBar();
        Menu mainMenu = new Menu("File");
        MenuItem exitCmd = new MenuItem("Exit");
        MenuItem textCmd = new MenuItem("Colour Text");
        mainMenu.getItems().addAll(textCmd, exitCmd);
        // borderPane.setTop(menuBar);

        root.getChildren().addAll(menuBar);
    }


}
