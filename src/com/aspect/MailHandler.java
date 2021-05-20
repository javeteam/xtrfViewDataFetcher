package com.aspect;

import com.aspect.exception.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import sun.misc.IOUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import javax.mail.*;

import static com.aspect.Configuration.dtFormatter;

public class MailHandler {
    public final String SUBJECT_VIEW_REGEX = ".*subscription for:.*\\bview:.*";
    public final String SUBJECT_REPORT_REGEX = ".*subscription for financial report:.*";
    private final Configuration config;
    private Set<String> handledMessageSubjects;

    public MailHandler(Configuration config){
        this.config = config;
    }

    public void processEmails(){
        Properties properties = config.prepareProperties();
        Session session = Session.getInstance(properties);

        try(Store store = session.getStore()){
            store.connect(config.SERVER_ADDRESS, config.IMAP_USER_NAME, config.IMAP_USER_PASS);
            Folder folder = store.getFolder(config.IMAP_FOLDER_NAME);
            folder.open(Folder.READ_WRITE);
            parseMessages(folder);

            folder.close(true);
        } catch (MessagingException | IOException | ClassNotFoundException e ){
            System.err.println(LocalDateTime.now().format(dtFormatter) + " - ERROR -- " + e.getMessage());
        } catch ( MessageHandlerException e){
            System.out.println(LocalDateTime.now().format(dtFormatter) + " - INFO -- " + e.getMessage());
        }
    }

    private void parseMessages(Folder folder) throws IOException, ClassNotFoundException, MessagingException, MessageHandlerException {
        handledMessageSubjects = new HashSet<>();
        LocalDate minDate = LocalDate.now().minusMonths(1);
        int index = folder.getMessageCount();

        for ( ; index > 0; index-- ) {
            Message message = folder.getMessage(index);
            if (messageFit(message)) {
                try {
                    handleAttachments(message);
                } catch (ExpectedAttachmentNotFoundException | MessageSubjectIncorrectException ex){
                    System.out.println(LocalDateTime.now().format(dtFormatter) + " - INFO -- " + ex.getMessage() + "Message doesn't fit, checking previous messages...");
                }
            } else {
                LocalDate messageDate = message.getReceivedDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                if(messageDate.isBefore(minDate)) break;
            }
        }
        System.out.println(LocalDateTime.now().format(dtFormatter) + " - INFO -- " + (folder.getMessageCount() - index) + " messages processed, " + handledMessageSubjects.size() + " messages saved. Exiting...");
    }

    private boolean messageFit(Message message) throws MessagingException{
        String messageSubject = message.getSubject().toLowerCase();
        if(messageSubject.matches(SUBJECT_VIEW_REGEX) || messageSubject.matches(SUBJECT_REPORT_REGEX)){
            if(handledMessageSubjects.contains(messageSubject)){
                message.setFlag(Flags.Flag.SEEN, true);
                message.setFlag(Flags.Flag.DELETED, true);
                return false;
            }
            Flags mFlags = message.getFlags();
            return !mFlags.contains(Flags.Flag.FLAGGED) || !mFlags.contains(Flags.Flag.SEEN);
        }
        return false;
    }

    private void handleAttachments(Message message) throws IOException, ClassNotFoundException, MessagingException, ExpectedAttachmentNotFoundException, MessageSubjectIncorrectException {
        Multipart multipart = (Multipart) message.getContent();
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);
            if (!"data.csv".equals(bodyPart.getFileName())) continue;
            String targetName = prepareTargetName(message);
            InputStream inputStream = bodyPart.getInputStream();
            byte[] data = IOUtils.readAllBytes(inputStream);
            switch (config.WORKING_MODE){
                case DB:
                    saveAttachmentToDB(targetName, data);
                    break;
                case HDD:
                    saveAttachmentToDisk(targetName, data);
                    break;
                case BOTH:
                    saveAttachmentToDB(targetName, data);
                    saveAttachmentToDisk(targetName, data);
                    break;
            }
            handledMessageSubjects.add(message.getSubject().toLowerCase());
            message.setFlag(Flags.Flag.FLAGGED, true);
            message.setFlag(Flags.Flag.SEEN, true);
            return;
        }
        throw new ExpectedAttachmentNotFoundException("No expected attachments found.");
    }

    private void saveAttachmentToDisk(String targetName, byte[] data) throws IOException{
        File file = new File(config.DESTINATION_FOLDER + targetName + ".csv");
        if(!file.exists()) file.createNewFile();
        if(isLocked(file)) throw new IOException("Can't replace existing file, because it is locked by another process");
        try(FileOutputStream fos = new FileOutputStream(file)){
            fos.write(data);
        }
    }

    private boolean isLocked(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            in.read();
            return false;
        } catch (FileNotFoundException e) {
            return file.exists();
        } catch (IOException ioe) {
            return true;
        }
    }

    private void saveAttachmentToDB(String tableName, byte[] inputData) throws ClassNotFoundException{
        try {
            InputStream in = new ByteArrayInputStream(inputData);
            CSVParser parser = CSVParser.parse(in, StandardCharsets.UTF_8, CSVFormat.DEFAULT.withDelimiter(';'));
            List<CSVRecord> lines = parser.getRecords();
            prepareData(lines);
            if(lines.isEmpty()) return;

            DataDao dao = new DataDao(this.config);
            List<List<String>> data = new ArrayList<>();
            int columnCount = 0;

            for(CSVRecord line : lines){
                columnCount = Math.max(line.size(), columnCount);
                List<String> row = new ArrayList<>();
                for(String cellData : line){
                    row.add(cellData);
                }
                data.add(row);
            }

            dao.recreateTable(tableName, columnCount);
            dao.insertRows(tableName, data, columnCount);

        } catch (IOException ex){
            System.out.println(ex.getMessage());
        }
    }

    private void prepareData(List<CSVRecord> lines){
        Iterator<CSVRecord> iterator = lines.iterator();
        while (iterator.hasNext()){
            CSVRecord line =  iterator.next();
            if(line.size() != 0 && ("ID").equals(line.get(0))){
                iterator.remove();
                break;
            } else iterator.remove();
        }
    }

    private String prepareTargetName(Message message) throws MessagingException, MessageSubjectIncorrectException{
        String str = message.getSubject().toLowerCase().trim();
        try {
            if(str.matches(SUBJECT_VIEW_REGEX)) str = str.substring(str.indexOf("view:") + 6);
            else if (str.matches(SUBJECT_REPORT_REGEX)) str = str.substring(str.indexOf("report:") + 8);
            if(str.length() <= 3) throw new MessageSubjectIncorrectException("Message subject fit the regex but not correct");
        } catch (StringIndexOutOfBoundsException ignored){
            throw new MessageSubjectIncorrectException("Message subject fit the regex but not correct");
        }

        char[] strArray = str.toCharArray();
        for(int i = 0; i < strArray.length; i++){
            if(strArray[i] < 97 || strArray[i] > 122 ) strArray[i] = '_';
        }

        return String.valueOf(strArray);
    }

}
