package StableMulticast;

import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public String content;
    public int[][] vectorClock;
    public int senderId;

    public Message(String content, int[][] vectorClock) {
        this.content = content;
        this.vectorClock = vectorClock;
    }

    public int getSenderId(){
        return senderId;
    }

    public int[][] getVectorClock(){
        return vectorClock;
    }
}
