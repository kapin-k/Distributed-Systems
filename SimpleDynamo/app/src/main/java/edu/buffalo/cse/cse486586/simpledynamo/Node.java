package edu.buffalo.cse.cse486586.simpledynamo;

import android.util.Log;
import java.util.Comparator;

public class Node {
    String id;
    String port;
    String hash;

    //Constructor
    public Node(String id, String port, String hash){
        this.id = id;
        this.port = port;
        this.hash = hash;
    }
    public Node(Node copy) {
        this.id = copy.id;
        this.port = copy.port;
        this.hash = copy.hash;
    }
    public Node(String[] nodeString){
        this.id = nodeString[0];
        this.port = nodeString[1];
        this.hash = nodeString[2];
    }

    @Override
    public String toString() {
        String nodeString = this.id + "-" + this.port + "-" + this.hash;
        return nodeString;
    }
    public void display(){
        String nodeString = "Node ID: " + this.id + "\n" + "Node Port: " + this.port + "\n" + "ID Hash: " + this.hash;
        Log.e("Node Info: ", "\n" + nodeString);
    }

    //Get functions
    public String getID(){
        return this.id;
    }
    public String getPort(){
        return this.port;
    }
    public String getHash(){
        return this.hash;
    }
    // Set functions
    public void setID(String x){
        this.id = (String) x;
    }
    public void setPortNum(String x){
        this.port = (String) x;
    }
    public void setSid(String x){
        this.hash = (String) x;
    }

    // Node Hash Comparator
    public static Comparator<Node> nodeHashComparator = new Comparator<Node>() {
        @Override
        public int compare(Node lhs, Node rhs) {
                return lhs.hash.compareTo(rhs.hash);
            }
    };
}
