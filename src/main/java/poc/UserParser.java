package poc;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import java.io.StringReader;
import org.xml.sax.InputSource;
import java.util.HashMap;
import java.util.Map;

public final class UserParser {
    public Map<String, String> parseUsersUsers(String xmlString) {
        Map<String, String> entriesMap = new HashMap<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xmlString)));
            doc.getDocumentElement().normalize();

            NodeList entries = doc.getElementsByTagName("entry");

            for (int i = 0; i < entries.getLength(); i++) {
                Element entry = (Element) entries.item(i);
                NodeList strings = entry.getElementsByTagName("string");

                if (strings.getLength() >= 2) {
                    String key = strings.item(0).getTextContent();
                    String value = strings.item(1).getTextContent();
                    entriesMap.put(key, value);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return entriesMap;
    }


    public UserInfo parse(String xmlString) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xmlString)));
            doc.getDocumentElement().normalize();

            long timestamp = this.parseTimestamp(doc);
            String name = this.parseID(doc);
            String seed = this.parseSeed(doc);
            String hash = this.parsePasswordHash(doc);
            //System.out.println(name + "," + seed + "," + hash);
            return new UserInfo(timestamp, name, seed, hash);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String parseID(Document doc) {
        NodeList propNodes = doc.getElementsByTagName("user");
        Node propNode = propNodes.item(0);
        Element propElement = (Element)propNode;
        NodeList idNodes = propElement.getElementsByTagName("id");
        return idNodes.item(0).getTextContent();
    }


    private long parseTimestamp(Document doc) {
        long timestamp = System.currentTimeMillis();
        try {
            NodeList propNodes = doc.getElementsByTagName("jenkins.security.LastGrantedAuthoritiesProperty");
            Node propNode = propNodes.item(0);
            Element propElement = (Element)propNode;
            NodeList timestampNodes = propElement.getElementsByTagName("timestamp");
            timestamp = Long.parseLong(timestampNodes.item(0).getTextContent());
        } catch (Exception e) { }
        return timestamp;
    }

    private String parseSeed(Document doc) {
        String seed = "no-seed";

        NodeList propNodes = doc.getElementsByTagName("jenkins.security.seed.UserSeedProperty");
        if (propNodes.getLength() > 0) {
            Node propNode = propNodes.item(0);
            if (propNode != null && propNode instanceof Element) {
                Element propElement = (Element) propNode;
                NodeList seedNodes = propElement.getElementsByTagName("seed");
                if (seedNodes.getLength() > 0 && seedNodes.item(0) != null) {
                    seed = seedNodes.item(0).getTextContent();
                } else {
                    seed = "no-prop";
                }
            } else {
                seed = "no-prop";
            }
        }

        return seed;
    }

    private String parsePasswordHash(Document doc) {
        NodeList propNodes = doc.getElementsByTagName("hudson.security.HudsonPrivateSecurityRealm_-Details");
        Node propNode = propNodes.item(0);
        Element propElement = (Element)propNode;
        NodeList passNodes = propElement.getElementsByTagName("passwordHash");
        return passNodes.item(0).getTextContent();
    }
}
