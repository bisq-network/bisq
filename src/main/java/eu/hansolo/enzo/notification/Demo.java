/*
 * Copyright (c) 2013 by Gerrit Grunwald
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.hansolo.enzo.notification;

/**
 * Created by
 * User: hansolo
 * Date: 01.07.13
 * Time: 07:10
 */

import java.util.Random;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;


public class Demo extends Application {
    private static final Random RND = new Random();
    private static final Notification[] NOTIFICATIONS = {
            NotificationBuilder.create().title("Info").message("New Information").image(Notification.INFO_ICON).build(),
            NotificationBuilder.create().title("Warning").message("Attention, somethings wrong").image(Notification
                    .WARNING_ICON).build(),
            NotificationBuilder.create().title("Success").message("Great it works").image(Notification.SUCCESS_ICON)
                    .build(),
            NotificationBuilder.create().title("Error").message("ZOMG").image(Notification.ERROR_ICON).build()
    };
    private Notification.Notifier notifier;
    private Button button;


    // ******************** Initialization ************************************
    @Override
    public void init() {
        button = new Button("Notify");
        button.setOnAction(event -> {
            notifier.notify(NOTIFICATIONS[RND.nextInt(4)]);
        });
    }


    // ******************** Application start *********************************
    @Override
    public void start(Stage stage) {
        notifier = NotifierBuilder.create()
                //.popupLocation(Pos.BOTTOM_RIGHT)
                .build();
        notifier.setOnNotificationPressed(event -> System.out.println("Notification pressed: " + event.NOTIFICATION
                .TITLE));
        notifier.setOnShowNotification(event -> System.out.println("Notification shown: " + event.NOTIFICATION.TITLE));
        notifier.setOnHideNotification(event -> System.out.println("Notification hidden: " + event.NOTIFICATION.TITLE));

        StackPane pane = new StackPane();
        pane.setPadding(new Insets(10, 10, 10, 10));
        pane.getChildren().addAll(button);

        Scene scene = new Scene(pane);
        stage.setOnCloseRequest(observable -> notifier.stop());
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
    }

    public static void main(String[] args) {
        launch(args);
    }
}
