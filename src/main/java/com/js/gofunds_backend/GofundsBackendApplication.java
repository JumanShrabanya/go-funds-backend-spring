package com.js.gofunds_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class GofundsBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(GofundsBackendApplication.class, args);
	}

}
