package com.dempsey.ClosedNigglesMover.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SheetAPIUtil {

    public static List<Map<String, String>> convertRange(List<List<Object>> rangeValue, List<String> headers){
        List<Map<String, String>> converted = new ArrayList<Map<String, String>>();
        for(List<Object> row : rangeValue){
            Map<String, String> valueMap = new HashMap<String, String>();
            for(int i = 0; i < headers.size(); i++){
                valueMap.put(headers.get(i), i < row.size() ? (String)row.get(i) : "");
            }
            converted.add(valueMap);
        }
        return converted;
    }

    public static List<String> getHeaders(List<Object> headerRow){
        return headerRow.stream().map(o -> (String)o).collect(Collectors.toList());
    }

    public static String getRangeString(int columnIndex, int rowNumber) {
        int quotient = (columnIndex -1 ) / 26 ;
        int remainder = columnIndex % 26 == 0 ? 26 : columnIndex % 26;
        String colNameFirtPart = columnIndex  >= 27 ? String.valueOf(Character.toChars((quotient + 64))) : "";
        String colNameSecondPart = String.valueOf(Character.toChars((remainder + 64)));
        String range = colNameFirtPart + colNameSecondPart + rowNumber;

        return range;
    }


}
