/*
 * Copyright (c) 2014 by Gerrit Grunwald
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

import java.util.HashMap;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.image.*;


/**
 * User: hansolo
 * Date: 29.04.14
 * Time: 08:53
 */
public class NotificationBuilder<B extends NotificationBuilder<B>> {
    private HashMap<String, Property> properties = new HashMap<>();


    // ******************** Constructors **************************************
    protected NotificationBuilder() {
    }


    // ******************** Methods *******************************************
    public final static NotificationBuilder create() {
        return new NotificationBuilder();
    }

    public final B title(final String TITLE) {
        properties.put("title", new SimpleStringProperty(TITLE));
        return (B) this;
    }

    public final B message(final String MESSAGE) {
        properties.put("message", new SimpleStringProperty(MESSAGE));
        return (B) this;
    }

    public final B image(final Image IMAGE) {
        properties.put("image", new SimpleObjectProperty<>(IMAGE));
        return (B) this;
    }

    public final Notification build() {
        final Notification NOTIFICATION;
        if (properties.keySet().contains("title") && properties.keySet().contains("message") && properties.keySet()
                .contains("image")) {
            NOTIFICATION = new Notification(((StringProperty) properties.get("title")).get(),
                    ((StringProperty) properties.get("message")).get(),
                    ((ObjectProperty<Image>) properties.get("image")).get());
        }
        else if (properties.keySet().contains("title") && properties.keySet().contains("message")) {
            NOTIFICATION = new Notification(((StringProperty) properties.get("title")).get(),
                    ((StringProperty) properties.get("message")).get());
        }
        else if (properties.keySet().contains("message") && properties.keySet().contains("image")) {
            NOTIFICATION = new Notification(((StringProperty) properties.get("message")).get(),
                    ((ObjectProperty<Image>) properties.get("image")).get());
        }
        else {
            throw new IllegalArgumentException("Wrong or missing parameters.");
        }
        return NOTIFICATION;
    }
}
