package poc;

import java.net.URI;
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;


public final class SystemUtils {


    public String getJARFilename() {
        String jarFile = null;
        try {
            URI url = Main.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            jarFile = url.getPath().toString();
        } catch (Exception e) {
            //e.printStackTrace();
        }
        return jarFile;
    }


    public int getRemotingPid() {
        int pid = -1;
        String[] agentFiles = {"agent.jar", "remoting.jar"};

        for (String agentFile : agentFiles) {
            String[] cmd = {"pgrep", "-f", agentFile};
            try {
                Process p = Runtime.getRuntime().exec(cmd);
                p.waitFor();
                BufferedReader b = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line = "";
                while ((line = b.readLine()) != null) {
                    pid = Integer.parseInt(line);
                    break;
                }
                b.close();
            } catch (Exception e) {
                //e.printStackTrace();
            }
            if (pid != -1) break;
        }
        return pid;
    }


    public Map getPidArgs() {
        Map<Integer, String> pidCmdMap = new HashMap<>();
        // linux   ps -xao ppid,pid,args
        // busybox ps -eo pid,ppid,args
        String[] psCmd1 = {"ps", "-eo", "pid,args"};
        if (!isBusyBox()) psCmd1[1] = "-xao";

        try {
            Process p = Runtime.getRuntime().exec(psCmd1);
            p.waitFor();
            BufferedReader b = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = "";
            while ((line = b.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.contains("COMMAND")) continue;
                int firstSpaceIndex = line.indexOf(' ');
                if (firstSpaceIndex != -1) {
                    try {
                        int pid = Integer.parseInt(line.substring(0, firstSpaceIndex).trim());
                        String cmd = line.substring(firstSpaceIndex).trim();
                        pidCmdMap.put(pid, cmd);

                    } catch (NumberFormatException e) {
                       e.printStackTrace();
                    }
                }
            }
            b.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return pidCmdMap;
    }


    public Map getPidPpid() {
        Map<Integer, Integer> pidPPidMap = new HashMap<>();
        // linux   ps -xao pid,ppid
        // busybox ps -eo pid,ppid
        String[] psCmd1 = {"ps", "-eo", "pid,ppid"};
        if (!isBusyBox()) psCmd1[1] = "-xao";

        try {
            Process p = Runtime.getRuntime().exec(psCmd1);
            p.waitFor();
            BufferedReader b = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = "";
            while ((line = b.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.contains("PPID")) continue;
                int firstSpaceIndex = line.indexOf(' ');
                if (firstSpaceIndex != -1) {
                    try {
                        int pid = Integer.parseInt(line.substring(0, firstSpaceIndex).trim());
                        int ppid = Integer.parseInt(line.substring(firstSpaceIndex).trim());
                        pidPPidMap.put(pid, ppid);

                    } catch (NumberFormatException e) {
                       e.printStackTrace();
                    }
                }
            }
            b.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return pidPPidMap;
    }


    public Boolean isBusyBox() {
        Boolean busybox = false;
        String[] cmd = {"ps", "--version"};
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
            BufferedReader b = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String line = "";
            while ((line = b.readLine()) != null) {
                if (line.contains("unrecognized"))
                    busybox = true;
                break;
            }
            b.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return busybox;
    }


    public String getClientFromSSHDPid(int pid) {
        String host = null;
        String myStr = "netstat -tpa | grep 'sshd' | grep 'ESTABLISHED'| grep ' %d'|awk '{print $5}'";
        String innerCmd = String.format(myStr, pid);
        String[] cmd = {"bash", "-c", innerCmd};

        try {
            Process p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
            BufferedReader b = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = "";
            while ((line = b.readLine()) != null) {
                host = line;
                break;
            }
            b.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        int lastIndex = host.lastIndexOf(':');
        return host.substring(0, lastIndex);
    }

}
