package com.dempsey.ClosedNigglesMover;

import com.dempsey.ClosedNigglesMover.service.SheetAPIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ClosedNigglesMoverApplication implements CommandLineRunner {

	public static void main(String[] args) {


	    SpringApplication.run(ClosedNigglesMoverApplication.class, args);
	}


	@Autowired
	private SheetAPIService sheetService;

	@Override
	public void run(String... args) throws Exception {
		sheetService.moveClosedNiggles();
	}
}
