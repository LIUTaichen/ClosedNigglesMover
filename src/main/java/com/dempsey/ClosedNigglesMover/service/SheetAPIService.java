package com.dempsey.ClosedNigglesMover.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SheetAPIService {

    private Logger log = LoggerFactory.getLogger(SheetAPIService.class);

    @Value("${email.google.api}")
    private String serviceAccountEmail;

    @Value("${path.google.api.key}")
    private String apiKeyPath;

    @Value("${sheet.id}")
    private String sheetId;

    @Value("${sheet.range.niggles.open}")
    private String openNigglesRange;

    @Value("${sheet.range.niggles.closed}")
    private String closedNigglesRange;




    /*public Sheets getSheetsService() throws IOException, GeneralSecurityException {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        // TODO: Change placeholder below to generate authentication credentials. See
        // https://developers.google.com/sheets/quickstart/java#step_3_set_up_the_sample
        //
        // Authorize using one of the following scopes:
        //   "https://www.googleapis.com/auth/drive"
        //   "https://www.googleapis.com/auth/drive.file"
        //   "https://www.googleapis.com/auth/spreadsheets"
        GoogleCredential credential = GoogleCredential.fromStream(new FileInputStream(apiKeyPath))
                .createScoped(Collections.singleton("https://www.googleapis.com/auth/spreadsheets"));


        return new Sheets.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName("Plant List Synchronizer/0.1")
                .build();
    }

    public ValueRange loadSheet(){
        String spreadsheetId = plantSheetId;
        String range = plantsRange;
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
                    getSheetsService().spreadsheets().values().batchUpdate(plantSheetId, body).execute();
            log.info("Result of sheet update: " + result.toPrettyString());
        }
        catch (Exception e){
            log.error("error when updating sheet", e);
        }
    }

    public void updateOneRange( List<ValueRange> updates){
        try {
            ValueRange update = updates.get(0);
            String range = updates.get(0).getRange();
            UpdateValuesResponse result =
                    getSheetsService().spreadsheets().values().update(plantSheetId, range, update ).setValueInputOption("RAW").execute();
            log.info("Result of sheet update: " + result.toPrettyString());
        }
        catch (Exception e){
            log.error("error when updating sheet", e);
        }
    }

    public List<ValueRange> generateUpdate(Map<String, Plant> plantMap, List<String> headers, List<Map<String, String>> converted){
        List<ValueRange> updatesRequired = new ArrayList<ValueRange>();
        Map<Integer, String> projectMap = new HashMap<Integer, String>();
        Map<Integer, String> ownerMap = new HashMap<Integer, String>();

        projectRepository.findAll().forEach(project -> projectMap.put(project.getId(), project.getProject_ID()));
        resourceOwnerRepository.findAll().forEach(owner -> ownerMap.put(owner.getId(),owner.getCompany()));



        for(Map<String, String> row: converted){
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/YYYY");
            String fleetId = row.get("Plant #");
            Plant plantInShirley = plantMap.get(fleetId);
            if(plantInShirley == null){
                continue;
            }else{
                Map<String, String> shirleyValueMap = new HashMap<String, String>();
                shirleyValueMap.put("Rego Due Date", plantInShirley.getRegistration_Due_Date() != null ? sdf.format(plantInShirley.getRegistration_Due_Date()) : "");
                shirleyValueMap.put("Last Log", plantInShirley.getLast_Log() != null ? plantInShirley.getLast_Log().toString() : "");
                shirleyValueMap.put("Last Log Date", plantInShirley.getLast_Log_Date() != null ? sdf.format(plantInShirley.getLast_Log_Date()) : "");
                shirleyValueMap.put("Maintenance Due",plantInShirley.getMaintenance_Due() != null ?  plantInShirley.getMaintenance_Due().toString(): "");
                shirleyValueMap.put("Maintenance Units", plantInShirley.getMaintenanceUnits() != null ? plantInShirley.getMaintenanceUnits(): "");
                shirleyValueMap.put("Units Til Maintenance", plantInShirley.getUnits_Til_Maintenance() != null ? plantInShirley.getUnits_Til_Maintenance().toString(): "");
                shirleyValueMap.put("WOF Due", plantInShirley.getCert_Due() != null ? sdf.format(plantInShirley.getCert_Due()): "");
                shirleyValueMap.put("Hub KM",plantInShirley.getHub_Reading() != null ?  plantInShirley.getHub_Reading().toString(): "");
                shirleyValueMap.put("RUC Due", plantInShirley.getRUC_Due() != null ? plantInShirley.getRUC_Due().toString(): "");
                //for owner
                String plantOwner =ownerMap.get(plantInShirley.getOwner());
                if(plantOwner!= null &&!plantOwner.isEmpty()){
                    shirleyValueMap.put("Plant Owner", plantOwner);
                }


                //for  job no

                String department =projectMap.get(plantInShirley.getLast_Log_Project());
                if(department!= null &&!department.isEmpty()){
                    shirleyValueMap.put("Department", department);
                }


                shirleyValueMap.keySet().forEach(key -> {
                        String oldValue = row.get(key);
                        String newValue = shirleyValueMap.get(key);
                        if(!oldValue.equals(newValue)){
                            log.info("found difference in row " + (converted.indexOf(row) + 2) + " Column : " + key + " , original value :  " + oldValue
                                    +  " , new value from Shirley : " + newValue);

                            ValueRange valueRange = new ValueRange();
                            Integer columnIndex = headers.indexOf(key) + 1;
                            Integer rowNumber = converted.indexOf(row) + 2;


                            String range = SheetAPIUtil.getRangeString(columnIndex, rowNumber);
                            valueRange.setRange("Plants!" + range);
                            List<List<Object>> outerList = new ArrayList<List<Object>>();
                            List<Object> innerList = new ArrayList<>();
                            innerList.add(newValue);
                            outerList.add(innerList);
                            valueRange.setValues(outerList);
                            updatesRequired.add(valueRange);
                         }

                });
            }
        }
        return updatesRequired;

    }

    public void syncPlantListWithShirley(){
        log.debug("calling sheet service");
        ValueRange range = this.loadSheet();
        List<String> headers = SheetAPIUtil.getHeaders(range.getValues().get(0));
        range.getValues().remove(0);
        List<Map<String, String>> converted = SheetAPIUtil.convertRange(range.getValues(), headers);
        Set<String> fleetIdInSheet = new HashSet<String>();
        for(Map<String, String> map: converted){
            if(!map.get("Plant #").isEmpty()){
                fleetIdInSheet.add(map.get("Plant #"));
            }
        }
        log.debug("calling plantService");
        List<Plant> plants = plantRepository.findByFleetIdIsNotNullAndActiveTrueAndFleetIdIn(fleetIdInSheet);

        Map<String, Plant> plantMap = new HashMap<String, Plant>();
        plantMap=   plants.stream().collect(Collectors.toMap(Plant::getFleetId, p -> p, (p1, p2 ) ->{
            log.info("duplicate fleet Id " + p1.getFleetId());
            return PlantsUtil.findBetterPlant(p1, p2);
        }));

        log.info("size of plant list : " + plants.size());



        List<ValueRange> toBeUpdated = this.generateUpdate(plantMap,headers, converted);
        if(toBeUpdated.isEmpty()){
            log.info("No update needs to be done. Plant list in sync with Shirley.");
        }else{
            log.info("Calling sheets api to update plant list.");
            this.updateRange(toBeUpdated);
        }

    }*/
}
