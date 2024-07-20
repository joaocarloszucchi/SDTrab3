import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

import StableMulticast.*;

public class Client implements IStableMulticast {
    private String ip;
    private Integer port;
    private StableMulticast stableMulticast;
    private ArrayList<String> chatMessages;

    public Client(String ip, Integer port) throws IOException {
        this.ip = ip;
        this.port = port;

        this.stableMulticast = new StableMulticast(ip, port, this);
        this.chatMessages = new ArrayList<>();
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Type a message or 'exit' to leave:");

        boolean keep = true;
        while (keep) {
            System.out.print("\nClient id = " + stableMulticast.getClientId());
            System.out.print("\nOptions:\n1 Send multicast message\n2 See buffer\n3 Show vector clock\n4 Exit\n");
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
                    printVectorClock(this.stableMulticast.getVectorClock());
                    break;
                case "4":
                    return;
            }
        }
    }

    private void printVectorClock(int [][] vectorClock){
        for (int i = 0; i < vectorClock.length; i++) {
            for (int j = 0; j < vectorClock[i].length; j++) {
                System.out.print(vectorClock[i][j] + " ");
            }
            System.out.println();
        }
    }

    @Override
    public void deliver(String msg) {
        if (!msg.startsWith("ID:")) {
            chatMessages.add(msg);
            System.out.println("\nNew Message: " + msg);
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 2) {
            System.out.println("You need to pass both the IP and the port");
            return;
        }

        Client client = new Client(args[0], Integer.parseInt(args[1]));

        client.run();
    }
}
