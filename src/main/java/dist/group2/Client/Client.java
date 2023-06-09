package dist.group2.Client;

import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.ip.udp.MulticastReceivingChannelAdapter;
import org.springframework.integration.ip.udp.UnicastReceivingChannelAdapter;
import org.springframework.messaging.Message;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

//@SpringBootApplication
public class Client implements Runnable {
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
    private int unicastPort;
    private int previousID;
    private int nextID;
    private boolean shuttingDown=false;
    public Client() throws UnknownHostException {
        name = InetAddress.getLocalHost().getHostName();
        IPAddress = InetAddress.getLocalHost().getHostAddress();
        namingPort = 8080;
        restTemplate = new RestTemplate();

        // Choose a random IP in the 224.0.0.0 to 239.255.255.255 range (reserved for multicast)
        multicastIP = "224.0.0.5";
        multicastGroup = InetAddress.getByName(multicastIP);
        multicastPort = 4446;
        unicastPort = 4448;
        previousID = -1;
        nextID = -1;

        System.out.println("<---> " + name + " Instantiated with IP " + IPAddress + " <--->");
        bootstrap();
    }

    public void run() {
        while (true) {
            try {
                // Shut down after 10s
                sleep(10000);
                shutdown();

                //findFile("file1.txt");
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
            System.out.println("Received answer to multicast from naming server - " + numberOfNodes + " node(s) in the network");

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

        // Set the nextID value of the previous node to nextID of this node
        if (previousID != hashValue(name)) {
            System.out.println("Sending nextID to the previous node");
            String messageToPrev = nextID + "|" + "nextID";
            sendUnicast(messageToPrev, getIPAddress(previousID), unicastPort);
        }

        // Set the previousID value of the next node to previousID of this node
        if (nextID != hashValue(name)) {
            System.out.println("Sending previousID to the next node");
            String messageToNext = previousID + "|" + "previousID";
            sendUnicast(messageToNext, getIPAddress(previousID), unicastPort);
        }

        // Delete this node from the Naming Server's database
        deleteNode(name);

        // Set isInterrupted flag high to stop the client thread
        Thread.currentThread().interrupt();

        // Enter infinite while loop
        while(true) {}
    }

    public void failure() {
        System.out.println("<---> " + name + " Failure <--->");
        shutdown();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //                                  DISCOVERY & BOOTSTRAP ASSISTANCE METHODS
    // -----------------------------------------------------------------------------------------------------------------
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

    public void compareIDs(String RxData) {
        String newNodeName = RxData.split("\\|")[0];
        String newNodeIP = RxData.split("\\|")[1];

        int newNodeID = hashValue(newNodeName);
        int currentID = hashValue(name);

        // Test if this node should become the previousID of the new node
        if (currentID <= newNodeID & newNodeID <= nextID) {
            nextID = newNodeID;
            sleep(500);    // Wait so the responses don't collide
            respondToMulticast(newNodeIP, currentID, "previousID");
        }

        // Test if this node should become the nextID of the new node
        if(previousID <= newNodeID & newNodeID <= currentID) {
            previousID = newNodeID;
            sleep(1000);    // Wait so the responses don't collide
            respondToMulticast(newNodeIP, currentID, "nextID");
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //                                            MULTICAST LISTENER
    // -----------------------------------------------------------------------------------------------------------------
    @Bean
    public MulticastReceivingChannelAdapter multicastReceiver(DatagramSocket socket) {
        MulticastReceivingChannelAdapter adapter = new MulticastReceivingChannelAdapter(multicastIP, 4446);
        adapter.setOutputChannelName("Multicast");
        adapter.setSocket(socket);
        return adapter;
    }

    @Bean
    public DatagramSocket datagramSocket() throws IOException {
        MulticastSocket socket = new MulticastSocket(4446);
        InetAddress group = InetAddress.getByName(multicastIP);
        socket.joinGroup(group);
        return socket;
    }

    @ServiceActivator(inputChannel = "Multicast")
    public void multicastEvent(Message<byte[]> message) throws IOException {
        byte[] payload = message.getPayload();
        DatagramPacket dataPacket = new DatagramPacket(payload, payload.length);

        String RxData = new String(dataPacket.getData(), 0, dataPacket.getLength());
        compareIDs(RxData);
        System.out.println(name + " - Received multicast message from other node: " + RxData + InetAddress.getLocalHost().getHostAddress());

        // Use this multicast data to update your previous & next node IDs
        compareIDs(RxData);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //                                              UNICAST LISTENER
    // -----------------------------------------------------------------------------------------------------------------
    @Bean
    public UnicastReceivingChannelAdapter unicastReceiver() {
        UnicastReceivingChannelAdapter adapter = new UnicastReceivingChannelAdapter(unicastPort);
        adapter.setOutputChannelName("Unicast");
        return adapter;
    }

    @ServiceActivator(inputChannel = "Unicast")
    public void unicastEvent(Message<byte[]> message) {
        byte[] payload = message.getPayload();
        DatagramPacket dataPacket = new DatagramPacket(payload, payload.length);

        String RxData = new String(dataPacket.getData(), 0, dataPacket.getLength());
        System.out.println("Received unicast message: " + RxData);

        int currentID = Integer.parseInt(RxData.split("\\|")[0]);
        String previousOrNext = RxData.split("\\|")[1];

        if (previousOrNext.equals("previousID")) {      // Transmitter becomes previous ID
            previousID = currentID; // Set previous ID
        } else if (previousOrNext.equals("nextID")) {   // Transmitter becomes next ID
            nextID = currentID;
        } else {
            System.out.println("<" + this.name + "> - ERROR - Unicast received 2nd parameter other than 'previousID' or 'nextID'");
            failure();
        }

        System.out.println("<---> previousID changed - previousID: " + previousID + ", thisID: " + hashValue(name) + ", nextID: " + nextID + " <--->");


        System.out.println("<---> nextID changed - previousID: " + previousID + ", thisID: " + hashValue(name) + ", nextID: " + nextID + " <--->");
    }

    public void setNeighbouringNodeIDs(int numberOfNodes) {
        if (numberOfNodes == 1) {
            // No other nodes in the network -> set previous & next ID to itself
            previousID = hashValue(name);
            nextID = hashValue(name);
        } else {
            // Other nodes detected -> wait 5s for response from previous & next node in the chain
            int timeElapsed = 0;
            while (previousID == -1 || nextID == -1) {
                sleep(10);
                timeElapsed += 10;

                // Failure if IDs are not received after 5s
                if (timeElapsed > 5000) {
                    System.out.println("<" + this.name + "> - ERROR - No unicast with IDs received after 5s");
                    failure();
                }
            }
        }
        System.out.println("<---> IDs successfully set - previousID: " + previousID + ", thisID: " + hashValue(name) + ", nextID: " + nextID + " <--->");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //                                          GENERAL PURPOSE METHODS
    // -----------------------------------------------------------------------------------------------------------------
    public String receiveUnicast(int port) {
        try {
            System.out.println("<---> Waiting for unicast response from NS to multicast of node " + IPAddress + " <--->");

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

    public void respondToMulticast(String newNodeIP, int currentID, String previousOrNext) {
        String message = currentID + "|" + previousOrNext;
        sendUnicast(message, newNodeIP, unicastPort);
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
            // Avoid calling failure if the node is already shutting down (to prevent infinite loops)
            if(!shuttingDown) {
                failure();
            }
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