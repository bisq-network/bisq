/*
Copyright (c) Microsoft Open Technologies, Inc.
All Rights Reserved
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0

THIS CODE IS PROVIDED ON AN *AS IS* BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED,
INCLUDING WITHOUT LIMITATION ANY IMPLIED WARRANTIES OR CONDITIONS OF TITLE, FITNESS FOR A PARTICULAR PURPOSE,
MERCHANTABLITY OR NON-INFRINGEMENT.

See the Apache 2 License for the specific language governing permissions and limitations under the License.
*/


package com.msopentech.thali.toronionproxy;

import java.util.concurrent.TimeUnit;

/**
 * Android uses FileObserver and Java uses the WatchService, this class abstracts the two.
 */
public interface WriteObserver {
    /**
     * Waits timeout of unit to see if file is modified
     * @param timeout How long to wait before returning
     * @param unit Unit to wait in
     * @return True if file was modified, false if it was not
     */
    boolean poll(long timeout, TimeUnit unit);
}
