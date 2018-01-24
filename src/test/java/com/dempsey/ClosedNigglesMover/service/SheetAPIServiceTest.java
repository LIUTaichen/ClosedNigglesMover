package com.dempsey.ClosedNigglesMover.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.*;
@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("dev")
public class SheetAPIServiceTest {

    @Autowired
    private SheetAPIService apiService;
    @Test
    public void getSheetsService() throws Exception {
    }

    @Test
    public void loadSheet() throws Exception {
    }

    @Test
    public void moveClosedNiggles() throws Exception {

    }

    @Test
    public void testInsertRows() throws Exception{
        apiService.createNewRowsInClosedSheet(3);
        System.out.println("success");

    }

}