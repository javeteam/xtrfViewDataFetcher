package com.aspect.exception;

import com.aspect.Configuration;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;

import static com.aspect.Configuration.dtFormatter;

public class DataDao {
    private final Configuration config;
    private final String URL;

    public DataDao(Configuration config) throws ClassNotFoundException{
        this.config = config;
        Class.forName("com.mysql.jdbc.Driver");
        this.URL = "jdbc:mysql://" + config.DB_HOST + ":3306/" + config.DB_NAME + "?characterEncoding=UTF-8";

    }

    public void recreateTable(String tableName, int columnCount){
        if(columnCount <= 0) return;
        final String deleteRequest = "DROP TABLE IF EXISTS " + tableName;
        final String createRequest = prepareTableCreateRequest(tableName, columnCount);


        try(Connection con = DriverManager.getConnection(URL, config.DB_USER, config.DB_PASSWORD); Statement smt = con.createStatement()){
            smt.executeUpdate(deleteRequest);
            smt.executeUpdate(createRequest);
        } catch (SQLException e){
            System.err.println(LocalDateTime.now().format(dtFormatter) + " - ERROR -- " + e.getMessage());
        }
    }



    public void insertRows(String tableName, List<List<String>> data, int columnCount){
        final String insertRequest = prepareInsertRequest(tableName, columnCount);
        try(
                Connection con = DriverManager.getConnection(URL, config.DB_USER, config.DB_PASSWORD);
                PreparedStatement smt = con.prepareStatement(insertRequest);
        ){
            con.setAutoCommit(false);
            BatchHandler batchHandler = new BatchHandler(smt, columnCount);
            for(int i = 0 ; i < data.size(); i++){
                batchHandler.addRow(data.get(i));
                if((i != 0 && i % 500 == 0) || i == data.size() -1){
                    smt.executeBatch();
                    con.commit();
                }
            }
        } catch (SQLException e){
            System.err.println(LocalDateTime.now().format(dtFormatter) + " - ERROR -- " + e.getMessage());
        }
    }

    private String prepareTableCreateRequest(String tableName, int columnCount){
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(tableName).append(" (");

        for(int i = 0; i < columnCount; i++){
            sb.append("c").append(i).append(" tinytext COLLATE utf8mb4_unicode_ci");
            sb.append(i == columnCount -1 ? "" : ", ");
        }
        sb.append(") ENGINE InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        return sb.toString();
    }

    private String prepareInsertRequest(String tableName, int columnCount){
        StringBuilder request = new StringBuilder();
        request.append("INSERT INTO ").append(tableName).append(" (");
        for(int i = 0; i < columnCount; i++){
            if(i > 0) request.append(", ");
            request.append('c').append(i);
        }
        request.append(')').append(" VALUES (");
        for(int i = 0; i < columnCount; i++){
            request.append(i > 0 ? ", ?" : '?');
        }
        request.append(')');
       return request.toString();
    }

    private static class BatchHandler {
        private final PreparedStatement smt;
        private final int columnCount;

        public BatchHandler(PreparedStatement smt, int columnCount){
            this.smt = smt;
            this.columnCount = columnCount;
        }

        public void addRow(List<String> row) throws SQLException{
            for(int i = 0; i < columnCount; i++){
                smt.setString(i+1, row.size() > i ? row.get(i) : "");
            }
            smt.addBatch();
        }



    }
}
