package com.dempsey.ClosedNigglesMover.service;

import com.dempsey.ClosedNigglesMover.util.SheetAPIUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class SheetAPIService {

    public static final int INDEX_OF_COLUMN_OF_DATE_CLOSED = 14;
    public static final String NAME_OF_LAST_COLUMN = "Priority";
    private Logger log = LoggerFactory.getLogger(SheetAPIService.class);

    @Value("${email.google.api}")
    private String serviceAccountEmail;

    @Value("${path.google.api.key}")
    private String apiKeyPath;

    @Value("${sheet.id}")
    private String spreadSheetId;

    @Value("${sheet.range.niggles.open}")
    private String openNigglesRange;

    @Value("${sheet.range.niggles.closed}")
    private String closedNigglesRange;

    @Value("${sheet.id.niggles.closed}")
    private Integer closedNigglesSheetId;

    @Value("${sheet.id.niggles.open}")
    private Integer opendNigglesSheetId;

    @Value("${row.index.header}")
    private int indexOfHeaderRow;

    private static final String JOB_STATE_CLOSED="Closed";
    @Value("${last.column}")
    private String lastColumn;


    public Sheets getSheetsService() throws IOException, GeneralSecurityException {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        GoogleCredential credential = GoogleCredential.fromStream(new FileInputStream(apiKeyPath))
                .createScoped(Collections.singleton("https://www.googleapis.com/auth/spreadsheets"));


        return new Sheets.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName("Closed Niggles Mover/0.1")
                .build();
    }

    public ValueRange loadSheet(){
        String spreadsheetId = spreadSheetId;
        String range = openNigglesRange;
        try {
            Sheets sheetsService = getSheetsService();
            ValueRange response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, range)
                    .execute();

            List<List<Object>> values = response.getValues();
            if (values == null || values.size() == 0) {
                log.info("No data found.");
            } else {
                log.info("values found");

                log.info("number of entries: " + values.size());

            }
            return response;
        }
        catch(Exception e){
            log.error("error when reading data from google sheet", e);
            return null;
        }

    }

    public void updateRange( List<ValueRange> updates){
        try {
            BatchUpdateValuesRequest body = new BatchUpdateValuesRequest()
                    .setValueInputOption("RAW")
                    .setData(updates);
            BatchUpdateValuesResponse result =
                    getSheetsService().spreadsheets().values().batchUpdate(spreadSheetId, body).execute();
            log.info("Result of sheet update: " + result.toPrettyString());
        }
        catch (Exception e){
            log.error("error when updating sheet", e);
        }
    }

    public void moveClosedNiggles(){
        log.debug("calling sheet service");
        ValueRange range = this.loadSheet();
        List<String> headers = SheetAPIUtil.getHeaders(range.getValues().get(indexOfHeaderRow));
        List<Map<String, String>> converted = SheetAPIUtil.convertRange(range.getValues(), headers);
        List<Map<String, String>> rowsClosed = new ArrayList<>();
        List<Integer> rowsToDelete = new ArrayList<>();
        for (int i = 0; i < converted.size(); i++) {
            Map<String, String> row = converted.get(i);
            String jobState = row.get("Job State");
            if (JOB_STATE_CLOSED.equals(jobState)) {
                log.info("found closed case with Plant # "  +  row.get("Plant #") + " , and created date: " + row.get("Date Created"));
                rowsClosed.add(row);
                rowsToDelete.add(i + 1);
            }
        }

        if(rowsClosed.isEmpty()) {
            log.info("New closed niggles not found. No need to proceed");
            return;
        }
        try {
            this.createNewRowsInClosedSheet(rowsClosed.size());
        } catch (Exception e) {
            log.error("error when creating rows", e);
        }

        List<ValueRange> updatesRequired = getValueRangeForUpdate(headers, rowsClosed);

        updateRange(updatesRequired);

        deleteRowsInNigglesSheet(rowsToDelete);

    }

    public List<ValueRange> getValueRangeForUpdate(List<String> headers, List<Map<String, String>> rowsClosed) {
        List<ValueRange> updatesRequired = new ArrayList<ValueRange>();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        String dateClosed = sdf.format(new Date());
        for(int i = 0; i < rowsClosed.size(); i++){
            Map<String,String> rowValues = rowsClosed.get(i);
            ValueRange valueRange = new ValueRange();
            int rowIndex = i+2;
            valueRange.setRange("Closed Cases!A" + rowIndex + ":" + lastColumn + rowIndex);
            List<List<Object>> outerList = new ArrayList<List<Object>>();
            List<Object> innerList = new ArrayList<>(headers.size() + 1);
            for(int j = 0; j <headers.size() + 1; j++ ){
                innerList.add(null);
            }

            rowValues.entrySet().forEach(row -> {
                int columnIndex = headers.indexOf(row.getKey());
                if(NAME_OF_LAST_COLUMN.equals(row.getKey())){
                    columnIndex++;
                }
                innerList.set(columnIndex, row.getValue());
            });
            innerList.set(INDEX_OF_COLUMN_OF_DATE_CLOSED, dateClosed);
            outerList.add(innerList);
            valueRange.setValues(outerList);
            updatesRequired.add(valueRange);
        }
        return updatesRequired;
    }



    public boolean createNewRowsInClosedSheet(Integer numberOfRows)  {
        BatchUpdateSpreadsheetRequest requestBody = new BatchUpdateSpreadsheetRequest();
        List<Request> requests = new ArrayList<>();
        requests.add(new Request().setInsertDimension(new InsertDimensionRequest().setRange(new DimensionRange().setStartIndex(1)
                .setEndIndex(1 + numberOfRows)
                .setSheetId(closedNigglesSheetId)
                .setDimension("ROWS"))));
        requestBody.setRequests(requests);
        try {
            Sheets sheetsService = this.getSheetsService();
            Sheets.Spreadsheets.BatchUpdate request =
                    sheetsService.spreadsheets().batchUpdate(spreadSheetId, requestBody);

            BatchUpdateSpreadsheetResponse response = request.execute();

            // TODO: Change code below to process the `response` object:
            log.info(response.toPrettyString());
        }catch(Exception e){
            log.error("error when calling api", e);
            return false;
        }
        return true;
    }

    public boolean deleteRowsInNigglesSheet(List<Integer> rowsToBeDeleted){
        BatchUpdateSpreadsheetRequest requestBody = new BatchUpdateSpreadsheetRequest();
        List<Request> requests = new ArrayList<>();
        Collections.sort(rowsToBeDeleted);
        Collections.reverse(rowsToBeDeleted);
        rowsToBeDeleted.forEach(rowIndex -> {
            requests.add(new Request().setDeleteDimension(new DeleteDimensionRequest().setRange(new DimensionRange().setStartIndex(rowIndex)
                    .setEndIndex(rowIndex + 1)
                    .setSheetId(opendNigglesSheetId)
                    .setDimension("ROWS"))));
        });


        requestBody.setRequests(requests);
        try {
            Sheets sheetsService = this.getSheetsService();
            Sheets.Spreadsheets.BatchUpdate request =
                    sheetsService.spreadsheets().batchUpdate(spreadSheetId, requestBody);

            BatchUpdateSpreadsheetResponse response = request.execute();

            log.info(response.toPrettyString());
        }catch(Exception e){
            log.error("error when calling api", e);
            return false;
        }
        return true;
    }
}
