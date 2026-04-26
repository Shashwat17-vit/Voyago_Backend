package backend.voyago.SpringBackend;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import backend.voyago.SpringBackend.service.WelcomeMessage;

@SpringBootApplication
public class SpringBackendApplication {

	private static final Logger log = LoggerFactory.getLogger(SpringBackendApplication.class);

	public static void main(String[] args) {
	
		SpringApplication.run(SpringBackendApplication.class, args);
		log.info("Application Started1!");
		WelcomeMessage message = new WelcomeMessage();
		System.out.println(message);
	}
}
