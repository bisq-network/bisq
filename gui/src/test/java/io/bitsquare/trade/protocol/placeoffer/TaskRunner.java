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

package io.bitsquare.trade.protocol.placeoffer;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskRunner {
    private static final Logger log = LoggerFactory.getLogger(TaskRunner.class);

    private Queue<Class> tasks;
    private SharedModel sharedModel = new SharedModel();
    private FaultHandler faultHandler;
    private ResultHandler taskCompleted;
    private final boolean[] failed = {false};

    @Test
    public void test() {
        // Task1.run();
        //Task2.run();

        tasks = new LinkedBlockingQueue<>();
        tasks.add(Task1.class);
        tasks.add(Task2.class);

        faultHandler = (throwable) -> {
            log.debug(throwable.getMessage());
            failed[0] = true;
        };
        taskCompleted = () -> {
            next();
        };
        next();
      /*  ResultHandler handleResult = () -> {
            Class task = tasks.poll();
            try {
                if (!failed[0])
                    ((Task) task.newInstance()).run(sharedModel, taskCompleted, faultHandler);
            } catch (InstantiationException e1) {
                e1.printStackTrace();
            } catch (IllegalAccessException e1) {
                e1.printStackTrace();
            }

        };*/
       


      /*  tasks.stream().forEach((e) -> {
            try {
                ((Task) e.newInstance()).run(sharedModel, faultHandler);
            } catch (InstantiationException e1) {
                e1.printStackTrace();
            } catch (IllegalAccessException e1) {
                e1.printStackTrace();
            }
        });*/
    }

    private void next() {
        Class task = tasks.poll();
        if (task != null) {
            try {
                if (!failed[0])
                    ((Task) task.newInstance()).run(sharedModel, taskCompleted, faultHandler);
            } catch (InstantiationException e1) {
                e1.printStackTrace();
            } catch (IllegalAccessException e1) {
                e1.printStackTrace();
            }
        }
    }
}

interface ResultHandler {
    void handleResult();
}

interface FaultHandler {
    void handleFault(Throwable throwable);
}

class SharedModel {
    public int data = 42;
}

class Task {

    protected void run(SharedModel sharedModel, ResultHandler resultHandler, FaultHandler faultHandler) {
    }

}

class Task1 extends Task {
    private static final Logger log = LoggerFactory.getLogger(Task1.class);

    @Override
    public void run(SharedModel sharedModel, ResultHandler resultHandler, FaultHandler faultHandler) {
        log.debug("run " + Task1.class);
        log.debug("data " + sharedModel.data);
        // faultHandler.handleFault(new Exception("test"));
        sharedModel.data++;
        resultHandler.handleResult();
    }
}

class Task2 extends Task {
    private static final Logger log = LoggerFactory.getLogger(Task2.class);

    @Override
    public void run(SharedModel sharedModel, ResultHandler resultHandler, FaultHandler faultHandler) {
        log.debug("run " + Task2.class);
        log.debug("data " + sharedModel.data);
        resultHandler.handleResult();
    }
}