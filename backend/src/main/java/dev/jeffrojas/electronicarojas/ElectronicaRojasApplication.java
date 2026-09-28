package dev.jeffrojas.electronicarojas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ElectronicaRojasApplication {

	public static void main(String[] args) {
		SpringApplication.run(ElectronicaRojasApplication.class, args);
	}

}
