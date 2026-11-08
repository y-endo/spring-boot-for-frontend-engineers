package com.example.quickvote;

import org.springframework.boot.SpringApplication;

public class TestQuickvoteApplication {

	public static void main(String[] args) {
		SpringApplication.from(QuickvoteApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
