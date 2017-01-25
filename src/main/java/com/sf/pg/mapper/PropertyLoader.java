package com.sf.pg.mapper;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Properties;

/**
 * Created with IntelliJ IDEA.
 * User: che
 * Date: 20.01.13
 * Time: 11:07
 */
public class PropertyLoader {

    public static final String PROP_NAME_REGEX = "[a-zA-Z0-9]*.[a-zA-Z0-9]*";
    public static final String LOCAL_PORT = "localPort".toLowerCase();
    public static final String REMOTE_HOST = "remoteHost".toLowerCase();
    public static final String REMOTE_PORT = "remotePort".toLowerCase();

    private PropertyLoader() {
    }

    public static Collection<MappingInfo> loadMappings(String fileName) throws IOException {
        HashMap<String, MappingInfo> bindings = new HashMap<String, MappingInfo>();
        Properties properties = loadProperties(fileName);
        for(String property : properties.stringPropertyNames()) {
            String name;
            String type;
            String value;

            if (!property.matches(PROP_NAME_REGEX)) {
                System.err.println("Invalid property name: " + property);
                continue;
            } else {
                String[] details = property.split("[.]");
                assert details.length == 2;
                name = details[0].toLowerCase();
                type = details[1].toLowerCase();
                value = properties.getProperty(property);
            }

            MappingInfo mapping;
            if (bindings.containsKey(name)) {
                mapping = bindings.get(name);
            } else {
                mapping = new MappingInfo();
                bindings.put(name,  mapping);
            }

            if (type.equals(LOCAL_PORT)) {
                mapping.localPort(Integer.parseInt(value));
            } else if (type.equals(REMOTE_HOST)) {
                mapping.remoteHost(value);
            } else if (type.equals(REMOTE_PORT)) {
                mapping.remotePort(Integer.parseInt(value));
            } else System.err.println("Invalid property name: " + property);
        }
        return bindings.values();
    }

    public static Properties loadProperties(String fileName) throws IOException {
        Properties properties = new Properties();
        properties.load(new FileInputStream(fileName));
        return properties;
    }
}
