public class BootstrapCheck {
    public static void main(String[] args) throws Exception {
        String[] classes = {
            "io.pyroscope.javaagent.api.ProfilerApiHolder",
            "io.pyroscope.javaagent.api.ProfilerApi",
        };
        boolean allPassed = true;
        for (String name : classes) {
            Class<?> cls = Class.forName(name);
            ClassLoader cl = cls.getClassLoader();
            if (cl == null) {
                System.out.println("BOOTSTRAP_CHECK " + name + ": PASS classloader=null (bootstrap)");
            } else {
                System.out.println("BOOTSTRAP_CHECK " + name + ": FAIL classloader=" + cl);
                allPassed = false;
            }
        }
        System.out.flush();
        System.exit(allPassed ? 0 : 1);
    }
}
