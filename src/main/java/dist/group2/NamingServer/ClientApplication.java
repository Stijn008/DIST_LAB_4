package dist.group2.NamingServer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.net.UnknownHostException;

@SpringBootApplication
public class ClientApplication {
    public static void main(String[] args) throws UnknownHostException {
        // Run Client thread
        SpringApplication.run(Client.class, args);
        //Thread clientThread1 = new Thread(client);
        clientThread1.start();	// Only client1 is running and asking for the file locations

        while(!clientThread1.isInterrupted()) {}
        System.out.println("<-> Client Thread Stopped <->");
        clientThread1.stop();

        //while(true) {
        //	System.out.println(clientThread1.isInterrupted());
        //	if (clientThread1.isInterrupted()) {
        //		System.out.println("<-> Client Thread Stopped <->");
        //		clientThread1.stop();
        //	}
        //	sleep(10);
        //}
    }
}
