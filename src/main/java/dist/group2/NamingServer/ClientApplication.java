package dist.group2.NamingServer;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class ClientApplication {
	private final String name;
	private final String IPAddress;
	private final int namingPort;
	private final RestTemplate restTemplate;
	private String baseUrl;

	// Discovery Parameters
	private String namingServerIP;
	private DatagramSocket multicastTxSocket;
	private MulticastSocket multicastRxSocket;
	private String multicastIP;
	private InetAddress multicastGroup;
	private int multicastPort;
	private int previousID;
	private int nextID;

	public static void main(String[] args) {
		// Run Naming Server
		SpringApplication.run(ClientApplication.class, args);
	}

	public ClientApplication() throws UnknownHostException {
		name = InetAddress.getLocalHost().getHostName();
		IPAddress = InetAddress.getLocalHost().getHostAddress();
		namingPort = 8080;
		restTemplate = new RestTemplate();

		// Choose a random IP in the 224.0.0.0 to 239.255.255.255 range (reserved for multicast)
		multicastIP = "224.0.0.5";
		multicastGroup = InetAddress.getByName(multicastIP);
		multicastPort = 4446;

		System.out.println("<---> " + name + " Instantiated with IP " + IPAddress + " <--->");
		bootstrap();
	}

	public void run() {
		while (true) {
			try {
				sleep(100);
			} catch (Exception e) {
				System.out.println("\t"+e.getMessage());
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	//                                       BOOTSTRAP, SHUTDOWN & FAILURE
	// -----------------------------------------------------------------------------------------------------------------
	public void bootstrap() {
		System.out.println("<---> " + name + " Bootstrap <--->");
		try {
			// Send multicast to other nodes and naming server
			sendMulticast();

			// Listen on port 4447 for a response with the number of nodes & IP address of the naming server
			String RxData = receiveUnicast(4447);
			namingServerIP = RxData.split("\\|")[0];
			int numberOfNodes = Integer.parseInt(RxData.split("\\|")[1]);
			System.out.println("Received answer to multicast from naming server - " + numberOfNodes + " other nodes");

			// Set the baseURL for further communication with the naming server
			baseUrl = "http://" + namingServerIP + ":" + namingPort + "/api/naming";

			// Initialise the previous & next node's IP addresses
			setNeighbouringNodeIDs(numberOfNodes);
		} catch (Exception e) {
			System.out.println("\t"+e.getMessage());
		}
	}

	@PreDestroy
	public void shutdown() {
		System.out.println("<---> " + name + " Shutdown <--->");
		System.out.println("");
		deleteNode(name);
	}


	public void failure() {
		System.out.println("<---> " + name + " Failure <--->");


	}

	// -----------------------------------------------------------------------------------------------------------------
	//                                  DISCOVERY & BOOTSTRAP ASSISTANCE METHODS
	// -----------------------------------------------------------------------------------------------------------------
	public void setNeighbouringNodeIDs(int numberOfNodes) {
		if (numberOfNodes == 1) {
			// No other nodes in the network -> set previous & next ID to itself
			previousID = hashValue(name);
			nextID = hashValue(name);
		} else {
			// Other nodes detected -> wait for response from previous & next node in the chain
			String RxData = receiveUnicast(4448);
			int currentID = Integer.parseInt(RxData.split("\\|")[0]);
			int previousOrNextID = Integer.parseInt(RxData.split("\\|")[1]);

			if (currentID < previousOrNextID) {     // Transmitter becomes previous ID
				previousID = currentID; // Set previous ID

				// Receive next ID (other transmitter has to be the next ID)
				RxData = receiveUnicast(4448);
				nextID = Integer.parseInt(RxData.split("\\|")[0]);
			} else {                                // Transmitter becomes next ID
				nextID = previousOrNextID;

				// Receive previous ID (other transmitter has to be the next ID)
				RxData = receiveUnicast(4448);
				previousID = Integer.parseInt(RxData.split("\\|")[0]);
			}
		}
		System.out.println("<---> IDs successfully set - previousID: " + previousID + ", thisID: " + hashValue(name) + ", nextID: " + nextID + " <--->");

	}

	public void sendMulticast() {
		try {
			System.out.println("<---> " + name + " Discovery Multicast Sending <--->");

			multicastTxSocket = new DatagramSocket();
			String data = name + "|" + IPAddress;
			byte[] Txbuffer = data.getBytes();
			DatagramPacket packet = new DatagramPacket(Txbuffer, Txbuffer.length, multicastGroup, multicastPort);

			multicastTxSocket.send(packet);
			multicastTxSocket.close();
		} catch (IOException e) {
			System.out.println("<" + this.name + "> - ERROR - Failed to send multicast - " + e);
			failure();
		}
	}

	public void receiveMulticast(String data) {
		try {
			System.out.println("<---> " + name + " Listening for multicasts <--->");

			multicastRxSocket = new MulticastSocket(multicastPort);
			multicastRxSocket.joinGroup(multicastGroup);

			byte[] RxBuffer = new byte[256];
			String RxData;

			// !RxData.equals("end")
			while (true) {
				DatagramPacket dataPacket = new DatagramPacket(RxBuffer, RxBuffer.length);
				multicastRxSocket.receive(dataPacket);
				RxData = new String(dataPacket.getData(), 0, dataPacket.getLength());
				compareIDs(RxData);
			}
		} catch (IOException e) {
			System.out.println("<" + this.name + "> - ERROR - Failed to receive multicast - " + e);
			failure();
		}
	}

	public void compareIDs(String RxData) {
		String newNodeName = RxData.split("\\|")[0];
		String newNodeIP = RxData.split("\\|")[1];

		int newNodeID = hashValue(newNodeName);
		int currentID = hashValue(name);

		if (currentID <= newNodeID & newNodeID <= nextID) {
			nextID = newNodeID;
			sleep(1000);    // Wait so the responses don't collide
			respondToMulticast(newNodeIP, currentID, nextID);
		}
		if(previousID <= newNodeID & newNodeID <= currentID) {
			previousID = newNodeID;
			sleep(2000);    // Wait so the responses don't collide
			respondToMulticast(newNodeIP, currentID, previousID);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	//                                          GENERAL PURPOSE METHODS
	// -----------------------------------------------------------------------------------------------------------------
	public String receiveUnicast(int port) {
		try {
			System.out.println("<---> Waiting for unicast response to multicast of node " + IPAddress + " <--->");

			// Prepare receiving socket & packet
			byte[] RxBuffer = new byte[256];
			DatagramSocket socket = new DatagramSocket(port);
			DatagramPacket dataPacket = new DatagramPacket(RxBuffer, RxBuffer.length);

			// Wait to receive & close socket
			socket.receive(dataPacket);
			socket.close();
			System.out.println("<---> Received unicast response to multicast of node " + IPAddress + " <--->");

			// Read data from dataPacket
			String RxData = new String(dataPacket.getData(), 0, dataPacket.getLength());
			return RxData;
		} catch (IOException e) {
			System.out.println("<" + this.name + "> - ERROR - Failed to receive unicast - " + e);
			failure();
			throw new IllegalStateException("Client has failed and should have been stopped by now");
		}
	}

	public void sendUnicast(String message, String IPAddress, int port) {
		try {
			System.out.println("<---> Send response to multicast of node " + IPAddress + " <--->");

			// Prepare response packet
			byte[] Txbuffer = message.getBytes();
			DatagramPacket packet = new DatagramPacket(Txbuffer, Txbuffer.length, InetAddress.getByName(IPAddress), port);

			// Send response to the IP of the node on port 4447
			DatagramSocket socket = new DatagramSocket();
			socket.send(packet);
			socket.close();
		} catch (IOException e) {
			System.out.println("<" + this.name + "> - ERROR - Failed to send unicast - " + e);
			failure();
			throw new IllegalStateException("Client has failed and should have been stopped by now");
		}
	}

	public void respondToMulticast(String newNodeIP, int currentID, int previousOrNextID) {
		String message = currentID + "|" + previousOrNextID;
		sendUnicast(message, newNodeIP, 4448);
	}

	public Integer hashValue(String name) {
		Integer hash = Math.abs(name.hashCode()) % 32769;
		return hash;
	}

	public void sleep(int time) {
		try {
			Thread.sleep(time);
		} catch (InterruptedException e) {
			failure();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	//                                        REST METHODS (NAMING SERVER)
	// -----------------------------------------------------------------------------------------------------------------
	@PostMapping
	public void addNode(String nodeName, String IPAddress) {
		String url = baseUrl;

		Map<String, String> requestBody = new HashMap<>();
		requestBody.put("nodeName", nodeName);
		requestBody.put("IPAddress", IPAddress);
		try {
			System.out.println("<" + this.name + "> - Add node with name " + nodeName + " and IP address " + IPAddress);
			restTemplate.postForObject(url, requestBody, Void.class);
		} catch(Exception e) {
			System.out.println("<" + this.name + "> - ERROR - Failed to add " + nodeName + ", hash collision occurred - " + e);
			failure();
		}
	}

	@DeleteMapping
	public void deleteNode(String nodeName) {
		String url = baseUrl + "/" + nodeName;
		try {
			restTemplate.delete(url);
			System.out.println("<" + this.name + "> - Deleted node with name " + nodeName);
		} catch(Exception e) {
			System.out.println("<" + this.name + "> - ERROR - Failed to delete " + nodeName + " - " + e);
			failure();
		}
	}

	@GetMapping
	public void findFile(String fileName) {
		String url = baseUrl + "?fileName=" + fileName;
		try {
			String IPAddress = restTemplate.getForObject(url, String.class);
			System.out.println("<" + this.name + "> - " + fileName + " is stored at IPAddress " + IPAddress);
		} catch(Exception e) {
			System.out.println("<" + this.name + "> - ERROR - Failed to find " + fileName + ", no nodes in database - " + e);
			failure();
		}
	}

	@GetMapping
	public String getIPAddress(int nodeID) {
		String url = baseUrl + "/translate/" + "?nodeID=" + nodeID;
		try {
			String IPAddress = restTemplate.getForObject(url, String.class);
			System.out.println("<" + this.name + "> - Node with ID " + nodeID + " has IPAddress " + IPAddress);
			return IPAddress;
		} catch(Exception e) {
			System.out.println("<" + this.name + "> - ERROR - Failed to find IPAddress of node with ID" + nodeID + " - " + e);
			failure();
			throw new IllegalStateException("Client has failed and should have been stopped by now");
		}
	}
}