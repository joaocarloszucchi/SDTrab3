import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

import StableMulticast.*;

public class Client implements IStableMulticast {
    private StableMulticast stableMulticast;
    private ArrayList<String> chatMessages;

    // Constructor initializes the client with IP, port, and sets up multicast
    public Client(String ip, Integer port) throws IOException {
        this.stableMulticast = new StableMulticast(ip, port, this);
        this.chatMessages = new ArrayList<>();
    }

    // Runs the client, allowing the user to send messages and perform other actions
    public void run() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Type a message or 'exit' to leave:");

        boolean keep = true;
        while (keep) {
            System.out.print("\nClient id = " + stableMulticast.getClientId());
            System.out.print("\nOptions:\n1 Send multicast message\n2 See buffer\n3 Show vector clock\n4 See Active Vector\n5 Exit\n");
            String input = scanner.nextLine();
            
            switch(input){
                case "1":
                    System.out.print("Type: ");
                    String msg = scanner.nextLine();
                    this.stableMulticast.msend(msg);
                    break;
                case "2":
                    System.out.print("Showing buffer:\n");
                    for (Message message : this.stableMulticast.getBuffer()) {
                        System.out.println(message.content);
                    }
                    break;
                case "3":
                    System.out.print("Showing Vector Clock:\n");
                    stableMulticast.printVectorClock(stableMulticast.getVectorClock());
                    break;
                case "4":
                    stableMulticast.printActiveVector();
                    break;
                case "5":
                    System.out.print("Exiting group:\n");
                    stableMulticast.exitGroup();
                    keep = false;
                    break;
            }
        }
        scanner.close();
        return;
    }

    // Delivers a received message to the client
    @Override
    public void deliver(String msg) {
        if (!msg.startsWith("ID:")) {
            chatMessages.add(msg);
            System.out.println("\nNew Message: " + msg);
        }
    }

    // Main method to start the client with provided IP and port
    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 2) {
            System.out.println("You need to pass both the IP and the port");
            return;
        }

        Client client = new Client(args[0], Integer.parseInt(args[1]));
        client.run();
    }
}
