package com.aspect;

import com.aspect.exception.ConfigurationException;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Configuration {
    public final String SERVER_ADDRESS;
    public final int SERVER_IMAP_PORT;
    public final boolean USE_STARTTLS;
    public final String IMAP_USER_NAME;
    public final String IMAP_USER_PASS;
    public final String IMAP_FOLDER_NAME;
    public final String SUBJECT_PATTERN;
    public final WorkingMode WORKING_MODE;
    public final String DESTINATION_FOLDER;
    public final String DB_HOST;
    public final String DB_NAME;
    public final String DB_USER;
    public final String DB_PASSWORD;

    public static final DateTimeFormatter dtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Configuration(String[] args) throws ConfigurationException {
        Map<String, String> params = defineParametersMap(args);

        if(params.containsKey("SERVER_ADDRESS")) SERVER_ADDRESS = params.get("SERVER_ADDRESS");
        else throw new ConfigurationException("SERVER_ADDRESS not provided");

        if(params.containsKey("SERVER_IMAP_PORT")) SERVER_IMAP_PORT = Integer.parseInt(params.get("SERVER_IMAP_PORT"));
        else throw new ConfigurationException("SERVER_IMAP_PORT not provided");

        if(params.containsKey("USE_STARTTLS")) USE_STARTTLS = Boolean.parseBoolean(params.get("USE_STARTTLS"));
        else throw new ConfigurationException("USE_STARTTLS not provided");

        if(params.containsKey("IMAP_USER_NAME")) IMAP_USER_NAME = params.get("IMAP_USER_NAME");
        else throw new ConfigurationException("IMAP_USER_NAME not provided");

        if(params.containsKey("IMAP_USER_PASS")) IMAP_USER_PASS = params.get("IMAP_USER_PASS");
        else throw new ConfigurationException("IMAP_USER_PASS not provided");

        if(params.containsKey("IMAP_FOLDER_NAME")) IMAP_FOLDER_NAME = params.get("IMAP_FOLDER_NAME");
        else throw new ConfigurationException("IMAP_FOLDER_NAME not provided");

        if(params.containsKey("SUBJECT_PATTERN")) SUBJECT_PATTERN = params.get("SUBJECT_PATTERN");
        else throw new ConfigurationException("SUBJECT_PATTERN not provided");

        try{
            if(params.containsKey("WORKING_MODE")) WORKING_MODE = WorkingMode.valueOf(params.get("WORKING_MODE"));
            else throw new ConfigurationException("WORKING_MODE not provided");
        } catch (IllegalArgumentException ignored){
            throw new ConfigurationException("WORKING_MODE is invalid");
        }

        switch (WORKING_MODE){
            case DB:
                DESTINATION_FOLDER = null;
                if(params.containsKey("DB_HOST")) DB_HOST = params.get("DB_HOST");
                else throw new ConfigurationException("DB_HOST not provided");

                if(params.containsKey("DB_NAME")) DB_NAME = params.get("DB_NAME");
                else throw new ConfigurationException("DB_NAME not provided");

                if(params.containsKey("DB_USER")) DB_USER = params.get("DB_USER");
                else throw new ConfigurationException("DB_USER not provided");

                if(params.containsKey("DB_PASSWORD")) DB_PASSWORD = params.get("DB_PASSWORD");
                else throw new ConfigurationException("DB_PASSWORD not provided");
                break;
            case HDD:
                DB_HOST = null;
                DB_USER = null;
                DB_NAME = null;
                DB_PASSWORD = null;
                if(params.containsKey("DESTINATION_FOLDER")) DESTINATION_FOLDER = params.get("DESTINATION_FOLDER");
                else throw new ConfigurationException("DESTINATION_FOLDER not provided");
                break;
            case BOTH:
                if(params.containsKey("DESTINATION_FOLDER")) DESTINATION_FOLDER = params.get("DESTINATION_FOLDER");
                else throw new ConfigurationException("DESTINATION_FOLDER not provided");

                if(params.containsKey("DB_HOST")) DB_HOST = params.get("DB_HOST");
                else throw new ConfigurationException("DB_HOST not provided");

                if(params.containsKey("DB_NAME")) DB_NAME = params.get("DB_NAME");
                else throw new ConfigurationException("DB_NAME not provided");

                if(params.containsKey("DB_USER")) DB_USER = params.get("DB_USER");
                else throw new ConfigurationException("DB_USER not provided");

                if(params.containsKey("DB_PASSWORD")) DB_PASSWORD = params.get("DB_PASSWORD");
                else throw new ConfigurationException("DB_PASSWORD not provided");
                break;
            default:
                throw new ConfigurationException("WORKING_MODE is invalid");
        }


    }

    private Map<String, String> defineParametersMap(String[] args) throws ConfigurationException{
        Map<String, String> params = new HashMap<>();
        for(String arg : args){
            int delimiter = arg.indexOf(':');
            if(delimiter > 0){
                params.put(arg.substring(0, delimiter), arg.substring(delimiter + 1));
            } else  throw new ConfigurationException("Invalid parameters format - use \"ParameterName:Value\"");
        }
        return params;
    }

    public Properties prepareProperties(){
        Properties properties = new Properties();
        properties.put("mail.imap.host", SERVER_ADDRESS);
        properties.put("mail.imap.port", SERVER_IMAP_PORT);
        properties.put("mail.store.protocol" , "imaps"  );
        properties.put("mail.imap.starttls.enable", USE_STARTTLS);
        properties.put("mail.imap.user", IMAP_USER_NAME);

        return properties;
    }
}
