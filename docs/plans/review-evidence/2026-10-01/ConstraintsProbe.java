import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class ConstraintsProbe {
    public static void main(String[] args) throws Exception {
        Class<?> constraints = Class.forName("androidx.compose.ui.unit.Constraints");
        Object companion = constraints.getField("Companion").get(null);
        Method fixed = companion.getClass().getMethod("fixed-JhjzzOo", int.class, int.class);
        for (int width : new int[] {100000, 262143, 300000}) {
            try {
                fixed.invoke(companion, width, 80);
                System.out.println("Constraints.fixed(" + width + ", 80): accepted");
            } catch (InvocationTargetException failure) {
                System.out.println("Constraints.fixed(" + width + ", 80): " + failure.getCause());
            }
        }
    }
}
