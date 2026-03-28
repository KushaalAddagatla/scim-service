package com.github.kushaal.scim_service;

import org.springframework.boot.SpringApplication;

public class TestScimServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(ScimServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
