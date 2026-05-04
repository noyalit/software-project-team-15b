package com.software_project_team_15b.Ticketmaster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class TicketmasterApplication {

	public static void main(String[] args) {
		SpringApplication.run(TicketmasterApplication.class, args);
	}

}
