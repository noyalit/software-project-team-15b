package com.software_project_team_15b.Ticketmaster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableRetry
@EnableScheduling
public class TicketmasterApplication {

	public static void main(String[] args) {
		SpringApplication.run(TicketmasterApplication.class, args);
	}

}
