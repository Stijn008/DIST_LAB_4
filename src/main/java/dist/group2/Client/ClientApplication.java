package dist.group2.Client;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.net.UnknownHostException;

@SpringBootApplication
public class ClientApplication {
	public static void main(String[] args) throws UnknownHostException {
		// Instantiate Client
		// SpringApplication.run(NamingServerApplication2.class, args);

		Client client = new Client();
		Thread clientThread = new Thread(client);
		// Start client thread
		clientThread.start();
	}

	public static void sleep(int time) {
		try {
			Thread.sleep(time); // Wait 5 seconds for server to start up
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}