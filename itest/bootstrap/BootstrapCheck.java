public class BootstrapCheck {
    public static void main(String[] args) throws Exception {
        Class<?> holderClass = Class.forName("io.pyroscope.javaagent.api.ProfilerApiHolder");
        ClassLoader cl = holderClass.getClassLoader();
        if (cl == null) {
            System.out.println("BOOTSTRAP_CHECK: PASS classloader=null (bootstrap)");
        } else {
            System.out.println("BOOTSTRAP_CHECK: FAIL classloader=" + cl);
        }
        System.out.flush();

        Thread.sleep(Long.MAX_VALUE);
    }
}
