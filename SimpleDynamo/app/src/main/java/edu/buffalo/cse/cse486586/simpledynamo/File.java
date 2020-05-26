package edu.buffalo.cse.cse486586.simpledynamo;

import android.util.Log;

public class File {
    String key;
    String value;
    String keyHash;

    //Constructor
    public File(String key, String value, String hash){
        this.key = key;
        this.value = value;
        this.keyHash = hash;
    }
    public File(File copy) {
        this.key = copy.key;
        this.value = copy.value;
        this.keyHash = copy.keyHash;
    }
    public File(String[] fileString){
        this.key = fileString[0];
        this.value = fileString[1];
        this.keyHash = fileString[2];
    }

    @Override
    public String toString() {
        String fileString = this.key + "-" + this.value + "-" + this.keyHash;
        return fileString;
    }
    public void display(){
        String fileString = "File Key: " + this.key + "\n" + "Value: " + this.value + "\n" + "Key Hash: " + this.keyHash;
        Log.e("File Info: ", "\n" + fileString);
    }

    //Get functions
    public String getKey(){
        return this.key;
    }
    public String getValue(){
        return this.value;
    }
    public String getKeyHash(){
        return this.keyHash;
    }
    // Set functions
    public void setKey(String x){
        this.key = (String) x;
    }
    public void setValue(String x){
        this.value = (String) x;
    }
    public void setKeyHash(String x){
        this.keyHash = (String) x;
    }
}
