/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.util;

import javafx.concurrent.Service;
import javafx.concurrent.Task;

import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Inbox;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.sun.glass.ui.Application;
import scala.concurrent.duration.FiniteDuration;

public abstract class ActorService extends Service<String> {

    private final LoggingAdapter log;

    private final ActorSystem system;
    private final Inbox inbox;
    private ActorSelection actor;

    private MessageHandler handler;

    protected ActorService(ActorSystem system, String actorPath) {
        this.log = Logging.getLogger(system, this);
        this.system = system;
        this.inbox = Inbox.create(system);
        this.actor = system.actorSelection(actorPath);
        log.debug(actor.pathString());
        this.start();
    }

    public void setHandler(MessageHandler handler) {
        this.handler = handler;
    }

    public void send(Object command) {
        if (actor != null) {
            actor.tell(command, inbox.getRef());
        }
    }

    protected Task<String> createTask() {

        return new Task<String>() {
            protected String call() throws Exception {

                while (!isCancelled()) {
                    if (inbox != null) {
                        try {
                            Object result = inbox.receive(FiniteDuration.create(1l, "minute"));
                            if (result != null) {
                                System.out.println(result.toString());
                                if (handler != null) {
                                    Application.invokeLater(new Runnable() {
                                        @Override
                                        public void run() {
                                            handler.handle(result);
                                        }
                                    });
                                }
                            }
                        } catch (Exception e) {
                            //System.out.println(e.toString());
                        }
                    }
                }
                return null;
            }
        };
    }
}
