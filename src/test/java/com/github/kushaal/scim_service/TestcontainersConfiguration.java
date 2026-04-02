package com.github.kushaal.scim_service;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.spec.SecretKeySpec;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
	}

	// Provides the signing key for SecurityConfig.jwtDecoder() in the test context.
	// AwsSecretsManagerConfig is skipped in tests (scim.jwt.secret-name is not set),
	// so this bean fills the gap. Must stay in sync with JwtTestHelper.TEST_KEY.
	@Bean
	SecretKeySpec jwtSigningKey() {
		return JwtTestHelper.TEST_KEY;
	}

}
