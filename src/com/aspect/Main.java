package com.aspect;

import com.aspect.exception.ConfigurationException;

import java.time.LocalDateTime;

import static com.aspect.Configuration.dtFormatter;

public class Main {

    public static void main(String[] args) {
        try {
            Configuration configuration = new Configuration(args);
            MailHandler handler = new MailHandler(configuration);
            handler.processEmails();
        } catch (ConfigurationException e){
            System.err.println(LocalDateTime.now().format(dtFormatter) + " - ERROR -- " + e.getMessage());
        }
    }
}
