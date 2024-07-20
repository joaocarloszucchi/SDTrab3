import java.io.IOException;
import java.util.ArrayList;
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
        System.out.println("Client running");
    }

    @Override
    public void deliver(String msg) {
        chatMessages.add(msg);
        System.out.println("New Message: " + msg);
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
