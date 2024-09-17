package io.pyroscope.javaagent;

public class CurrentPidProvider {
    public static long getCurrentProcessId() {
        return ProcessHandle.current().pid();
//        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
//        Field jvm = null;
//        try {
//            jvm = runtime.getClass().getDeclaredField("jvm");
//            jvm.setAccessible(true);
//
//            VMManagement management = (VMManagement) jvm.get(runtime);
//            Method method = management.getClass().getDeclaredMethod("getProcessId");
//            method.setAccessible(true);
//
//            return (Integer) method.invoke(management);
//        } catch (NoSuchFieldException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
//            throw new RuntimeException(e);
//        }
    }
}
