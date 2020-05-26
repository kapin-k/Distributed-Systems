package edu.buffalo.cse.cse486586.simpledht;

import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Formatter;

public class Node { //NODE CAN ALSO BE A DATA
    String id;
    String port;
    String sid;
    String pid;
    Boolean joinStatus;
    String type;
    String key;
    String value;
    String sendToNode;

    // constructors
    public Node(String portID, String portNum, String sid, String pid, Boolean joinStatus, String type,String key, String value, String sendToNode) {
        this.id = portID;
        this.port = portNum;
        this.sid = sid;
        this.pid = pid;
        this.joinStatus = joinStatus;
        this.type = type;
        this.key = key;
        this.value = value;
        this.sendToNode = sendToNode;
    }
    public Node(Node copy) {
        this.id = copy.id;
        this.port = copy.port;
        this.sid = copy.sid;
        this.pid = copy.pid;
        this.joinStatus = copy.joinStatus;
        this.type = copy.type;
        this.key = copy.key;
        this.value = copy.value;
        this.sendToNode = copy.sendToNode;
    }
    public Node(String[] nodeString){
        this.id = nodeString[0];
        this.port = nodeString[1];
        this.sid = nodeString[2];
        this.pid = nodeString[3];
        this.joinStatus = Boolean.parseBoolean(nodeString[4]);
        this.type = nodeString[5];
        this.key = nodeString[6];
        this.value = nodeString[7];
        this.sendToNode = nodeString[8];
    }

    @Override
    public String toString() {
        String nodeString = this.id + "-" + this.port + "-" + this.sid + "-" + this.pid + "-" + this.joinStatus + "-" + this.type + "-" + this.key + "-" + this.value + "-" + this.sendToNode;
        return nodeString;
    }
    public void print(){
        String nodeString = "Node ID: " + this.id + "\n" + "Node Port: " + this.port + "\n" + "Successor ID: " + this.sid + "\n" + "Predecessor ID: " + this.pid + "\n" + "Node Join Status: " + this.joinStatus + "\n" + "Node Type: " + this.type + "\n" + "Node key: " + this.key + "\n" + "Node Value: " + this.value + "\n" + "Node to forward data: " + this.sendToNode;;
        Log.e("Node Information", "\n" + nodeString);
    }

    // Hash of my ID
    public String myHash() throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(this.id.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }
    //Hash of successorID
    public String sHash() throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(this.sid.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }
    //Hash of predecessorID
    public String pHash() throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(this.pid.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    // get functions
    public String getID(){
        return this.id;
    }
    public String getPort(){
        return this.port;
    }
    public String getSid(){
        return this.sid;
    }
    public String getPid(){
        return this.pid;
    }
    public Boolean getJoinStatus(){
        return this.joinStatus;
    }
    public String getType(){
        return this.type;
    }
    public String getKey(){
        return this.key;
    }
    public String getValue(){
        return this.value;
    }
    public String getSendToNode(){
        return this.sendToNode;
    }
    // set functions
    public void setID(String x){
        this.id = (String) x;
    }
    public void setPortNum(String x){
        this.port = (String) x;
    }
    public void setSid(String x){
        this.sid = (String) x;
    }
    public void setPid(String x){ this.pid = (String) x; }
    public void setJoinStatus(String x){
        this.joinStatus = Boolean.parseBoolean(x);
    }
    public void setType(String x){
        this.type = (String) x;
    }
    public void setKey(String x){
        this.key = (String) x;
    }
    public void setValue(String x){
        this.value = (String) x;
    }
    public void setSendToNode(String x){
        this.sendToNode = (String) x;
    }

    public static Comparator<Node> nodeHashComparator = new Comparator<Node>() {
        @Override
        public int compare(Node lhs, Node rhs) {
            try {
                return lhs.myHash().compareTo(rhs.myHash());
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                return 0;
            }
        }
    };
}
