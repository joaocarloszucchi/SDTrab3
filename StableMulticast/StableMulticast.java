package StableMulticast;

import java.net.*;
import java.io.*;
import java.util.*;

public class StableMulticast {
    public static int maxSize = 3;
    public static String groupIp = "230.0.0.0";
    public static Integer groupPort = 4446;
    private String ip;
    private Integer port;
    private IStableMulticast client;
    private int clientId;
    private int[][] vectorClock;
    private boolean[] activeClocks;
    private List<Message> buffer;
    private List<InetSocketAddress> multicastGroup;
    private DatagramSocket unicastSocket;
    private MulticastSocket groupSocket;
    private InetAddress group;
    private volatile boolean running = true;  // Flag to signal when to stop threads

    public StableMulticast(String ip, Integer port, IStableMulticast client) throws IOException {
        this.ip = ip;
        this.port = port;
        this.client = client;
        this.buffer = new ArrayList<>();
        this.vectorClock = new int[maxSize][maxSize];
        this.activeClocks = new boolean[maxSize];
        for (int i = 0; i < maxSize; i++) {
            this.activeClocks[i] = false;
        }
        this.multicastGroup = Collections.synchronizedList(new ArrayList<>());

        try {
            this.unicastSocket = new DatagramSocket(this.port);
            this.groupSocket = new MulticastSocket(groupPort);
            this.group = InetAddress.getByName(groupIp);

            // Use the new joinGroup method with NetworkInterface
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
            this.groupSocket.joinGroup(new InetSocketAddress(this.group, groupPort), networkInterface);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        // Add the current client to the multicast group and determine the client ID
        this.clientId = 0;
        this.activeClocks[this.clientId] = true;
        joinMulticastGroup(new InetSocketAddress(this.ip, this.port));

        // Start a thread to listen for incoming messages
        new Thread(this::receiveUnicastMessages).start();
        new Thread(this::receiveGroupMessages).start();
    }

    private void joinMulticastGroup(InetSocketAddress newClient) {
        // Send a message to the group to announce the new client and request the current members
        this.multicastGroup.add(newClient);
        sendGroupMessage("NewClient:" + newClient.getAddress().getHostAddress() + ":" + newClient.getPort());
    }

    public void sendUnicastMessage(InetSocketAddress member, Message message) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            ObjectOutputStream os = new ObjectOutputStream(new BufferedOutputStream(byteStream));
            os.flush();
            os.writeObject(message);
            os.flush();

            byte[] sendBuf = byteStream.toByteArray();
            DatagramPacket packet = new DatagramPacket(sendBuf, sendBuf.length, member.getAddress(), member.getPort());
            this.unicastSocket.send(packet);
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendGroupMessage(String message) {
        try {
            byte[] sendBuf = message.getBytes();
            DatagramPacket packet = new DatagramPacket(sendBuf, sendBuf.length, this.group, groupPort);
            this.groupSocket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void receiveUnicastMessages() {
        try {
            while (running) {
                byte[] recvBuf = new byte[1024];
                DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
                this.unicastSocket.receive(packet);

                ByteArrayInputStream byteStream = new ByteArrayInputStream(recvBuf);
                ObjectInputStream is = new ObjectInputStream(new BufferedInputStream(byteStream));
                Message msg = (Message) is.readObject();
                is.close();

                String message = msg.content;

                // Add the sender to the multicast group if not already present
                InetSocketAddress senderAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());

                // Check if the sender is yourself
                if (senderAddress.getAddress().getHostAddress().equals(this.ip) && senderAddress.getPort() == this.port) {
                    continue; // Skip processing the message from yourself
                }

                synchronized (multicastGroup) {
                    if (!multicastGroup.contains(senderAddress)) {
                        multicastGroup.add(senderAddress);
                    }
                }

                if (message.startsWith("ID:")) {
                    int receivedId = Integer.parseInt(message.substring("ID:".length()));
                    this.clientId = Math.max(this.clientId, receivedId + 1);
                    this.activeClocks[receivedId] = true;
                    this.activeClocks[this.clientId] = true;

                } else {
                    synchronized (buffer) {
                        if (!buffer.contains(msg)) {
                            buffer.add(msg);
                            // Deliver the message to the client
                            client.deliver(msg.content);
                        }
                    }

                    // Updates the VC
                    this.updatesVectorClock(msg.getSenderId(), msg.getVectorClock());

                    //checks if any message can be removed
                    this.checksBuffer();
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            if(running){
                e.printStackTrace();
            }
        }
    }

    private void receiveGroupMessages() {
        try {
            while (running) {
                byte[] recvBuf = new byte[1024];
                DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
                this.groupSocket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());

                String[] parts = message.split(":");
                String operation = parts[0];
                String senderIp = parts[1];
                int senderPort = Integer.parseInt(parts[2]);
                //ignores if you are the sender
                if (senderIp == this.ip && senderPort == this.port){
                    return;
                }

                if (operation.equals("NewClient")) {
                    InetSocketAddress newClient = new InetSocketAddress(senderIp, senderPort);
                    synchronized (this.multicastGroup) {
                        if (!this.multicastGroup.contains(newClient)) {
                            this.multicastGroup.add(newClient);
                        }
                    }
                    Message messageId = new Message("ID:" + this.clientId, getPersonalVectorClock(), this.clientId);
                    sendUnicastMessage(newClient, messageId);
                } 
                else if (operation.equals("Exit")) {
                    InetSocketAddress leavingClient = new InetSocketAddress(senderIp, senderPort);
                    int exitId = Integer.parseInt(parts[3]);
                    activeClocks[exitId] = false;
                    synchronized (this.multicastGroup) {
                        this.multicastGroup.remove(leavingClient);
                    }
                }
            }
        } catch (IOException e) {
            if(running){
                e.printStackTrace();
            }
        }
    }

    public void msend(String msg){
        //updates clock
        incrementsVectorClock();

        Message message = new Message(msg, getPersonalVectorClock(), this.clientId);

        //puts in buffer
        synchronized (this.buffer) {
            buffer.add(message);
        }

        Scanner scanner = new Scanner(System.in);

        //sends via multicast
        for (InetSocketAddress member : multicastGroup) {
            System.out.println("Send message to " + member + "? (y/n)");
            String response = scanner.nextLine().trim().toLowerCase();
    
            if (response.equals("y")) {
                sendUnicastMessage(member, message);
            }
        }
    }

    public List<Message> getBuffer(){
        return this.buffer;
    }

    public int[][] getVectorClock(){
        return this.vectorClock;
    }

    public int[] getPersonalVectorClock(){
        int[] vector = new int[maxSize];
        for(int i = 0; i < maxSize; i++){
            vector[i] = vectorClock[clientId][i];
        }
        return vector;
    }

    private void incrementsVectorClock(){
        synchronized(this.vectorClock){
            this.vectorClock[this.clientId][this.clientId]++;
        }
    }

    private void updatesVectorClock(int senderId, int[] senderVectorClock){
        
        synchronized(this.vectorClock){
            for(int i = 0; i < maxSize; i++){
                
                this.vectorClock[senderId][i] = Math.max(this.vectorClock[senderId][i], senderVectorClock[i]);
            }
            this.vectorClock[this.clientId][senderId]++;
        }
        
    }

    public int getClientId(){
        return this.clientId;
    }

    public void printVectorClock(int [][] matrix){
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                System.out.print(matrix[i][j] + " ");
            }
            System.out.println();
        }
    }

    private void checksBuffer(){
        synchronized (this.buffer) {
            Iterator<Message> iterator = buffer.iterator();
            while (iterator.hasNext()) {
                Message msg = iterator.next();
                boolean canBeDiscarded = true;
                for (int i = 0; i < maxSize; i++) {
                    if (activeClocks[i] && vectorClock[i][msg.getSenderId()] < msg.getVectorClock()[msg.getSenderId()]) {
                        canBeDiscarded = false;
                        break;
                    }
                }
                if (canBeDiscarded) {
                    System.out.println("\nDiscarding message\n");
                    iterator.remove();
                }
            }
        }
    }

    public void exitGroup() {
        try {
            sendGroupMessage("Exit:" + this.ip + ":" + this.port + ":" + this.clientId);
            // Leave the multicast group
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
            this.groupSocket.leaveGroup(new InetSocketAddress(this.group, groupPort), networkInterface);

            // Close the sockets
            this.running = false;
            this.unicastSocket.close();
            this.groupSocket.close();

            System.out.println("Successfully left the multicast group.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
