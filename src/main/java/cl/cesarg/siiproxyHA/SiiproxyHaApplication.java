package cl.cesarg.siiproxyHA;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SiiproxyHaApplication {

	public static void main(String[] args) {
		SpringApplication.run(SiiproxyHaApplication.class, args);
	}

}
