package StableMulticast;

import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public String content;
    public int[] vectorClock;
    public int senderId;

    // Constructor to initialize a message with content, vector clock, and sender ID
    public Message(String content, int[] vectorClock, int senderId) {
        this.content = content;
        this.vectorClock = vectorClock;
        this.senderId = senderId;
    }

    // Returns the sender ID of the message
    public int getSenderId(){
        return senderId;
    }

    // Returns the vector clock of the message
    public int[] getVectorClock(){
        return vectorClock;
    }
}
