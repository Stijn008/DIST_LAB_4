package dist.group2.Client;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.net.UnknownHostException;

@SpringBootApplication
public class ClientApplication {
	public static void main(String[] args) throws UnknownHostException {
		// Instantiate Client
		Client client = new Client();
		Thread clientThread = new Thread(client);

		// Start client thread
		clientThread.start();
	}
}