/*
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.pyroscope.javaagent.util.zip;

public class Util {
    /**
     * <a href="https://github.com/square/okio/blob/f60e79ec801ba517a73acaf951e2a089d43666fc/okio/src/main/java/okio/Util.java#L59">origin</a>
     * Throws {@code t}, even if the declared throws clause doesn't permit it.
     * This is a terrible – but terribly convenient – hack that makes it easy to
     * catch and rethrow exceptions after cleanup. See Java Puzzlers #43.
     */
    public static void sneakyRethrow(Throwable t) {
        Util.<Error> sneakyThrow2(t);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow2(Throwable t) throws T {
        throw (T) t;
    }
}
