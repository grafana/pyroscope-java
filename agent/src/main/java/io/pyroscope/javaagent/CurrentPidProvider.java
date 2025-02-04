package io.pyroscope.javaagent;

import sun.management.VMManagement;

import java.lang.ProcessHandle;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Hacky implementation of JVM process id (pid) handler, only used with JDK 8 implementation of {@link JFRJCMDProfilerDelegate}
 */
public final class CurrentPidProvider {
    public static long getCurrentProcessId() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        Field jvm = null;
        try {
            jvm = runtime.getClass().getDeclaredField("jvm");
            jvm.setAccessible(true);

            VMManagement management = (VMManagement) jvm.get(runtime);
            Method method = management.getClass().getDeclaredMethod("getProcessId");
            method.setAccessible(true);

            return (Integer) method.invoke(management);
        } catch (NoSuchFieldException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
