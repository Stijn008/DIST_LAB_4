package dist.group2.TestOnPC;

import dist.group2.TestOnPC.Client;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.net.UnknownHostException;

@SpringBootApplication
public class NamingServerApplication {
	public static void main(String[] args) throws UnknownHostException {
		// Run Naming Server
		SpringApplication.run(NamingServerApplication.class, args);

		// Wait 5 seconds (until the server is up and running)
		sleep(7000);

		// Instantiate Client threads
		Client client1 = new Client();
		// Client client2 = new Client();
		// Client client3 = new Client();
		Thread clientThread1 = new Thread(client1);

		// Start client thread
		clientThread1.start();	// Only client1 is running and asking for the file locations

		while(!clientThread1.isInterrupted()) {
			sleep(10);
		}
		System.out.println("<-> Client Thread Stopped <->");
		clientThread1.interrupt();

		//while(true) {
		//	System.out.println(clientThread1.isInterrupted());
		//	if (clientThread1.isInterrupted()) {
		//		System.out.println("<-> Client Thread Stopped <->");
		//		clientThread1.stop();
		//	}
		//	sleep(10);
		//}
	}
	public static void sleep(int time) {
		try {
			Thread.sleep(time); // Wait 5 seconds for server to start up
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}