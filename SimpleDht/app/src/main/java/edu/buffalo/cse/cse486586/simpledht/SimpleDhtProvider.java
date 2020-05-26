package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDhtProvider extends ContentProvider {

    static final String TAG = SimpleDhtProvider.class.getSimpleName();
    static final int SERVER_PORT = 10000;
    static final int INITIAL_PORT = 11108;
    static Boolean onlyNode = false;
    static String myID; // 555*
    static String myPort; // 111*
    static String sID = null; // Successor Port Number
    static String pID = null; // Predecessor Port Number
    public static final String[] nodeType = {"Join", "Insert", "SucUpdate", "PreUpdate", "Query", "Delete"};
    ArrayList<Node> nodeRing = new ArrayList<Node>();
    ArrayList<Node> dataNodes = new ArrayList<Node>();
    public String queryString;

    public void fileInsert(Node fileNode) {
        FileOutputStream fileOutputStream;
        try {
            fileOutputStream = getContext().openFileOutput(fileNode.getKey(), Context.MODE_PRIVATE);
            fileOutputStream.write(fileNode.getValue().getBytes());
            fileOutputStream.close();
        } catch (Exception e) {
            Log.e(TAG, "Failed to write file {fileInsert}");
        }
    }

    public Boolean checkBeforeInsert(Node node) {
        try {
            String keyHashed = genHash(node.getKey());
            if (keyHashed.compareTo(genHash(myID)) <= 0 && keyHashed.compareTo(genHash(pID)) > 0) {
                return true;
            }
            if ((genHash(myID).compareTo(genHash(pID)) < 0) && (keyHashed.compareTo(genHash(myID)) <= 0 || keyHashed.compareTo(genHash(pID)) > 0)) {
                return true;
            } else {
                return false;
            }
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Hashing problem {checkBeforeInsert}");
            return false;
        }
    }

    public void sendToPredecessor(Node receivedNode) {
        Log.e(TAG, "Message sent to predecessor node: " + receivedNode.getPid() + " to update it's successor to " + receivedNode.getID());
        try {
            Socket sucSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), (Integer.parseInt(pID) * 2));
            PrintWriter successorOut = new PrintWriter(sucSocket.getOutputStream(), true);
            Node msgSuc = new Node(receivedNode);
            msgSuc.setType(nodeType[2]);
            successorOut.println(msgSuc.toString());
            BufferedReader successorIn = new BufferedReader(new InputStreamReader(sucSocket.getInputStream()));
            String updateCheck = successorIn.readLine();
            if (updateCheck.equals("Updated")) {
                Log.e(TAG, "Successor updated");
                sucSocket.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Socket Exception {sendSuccessorUpdate}");
        }
    }

    public void sendToSuccessor(Node receivedNode) {
        Log.e(TAG, "Message sent to successor node: " + receivedNode.getSid() + "  to update it's predecessor to " + receivedNode.getID());
        try {
            Socket preSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), (Integer.parseInt(sID) * 2));
            PrintWriter predecessorOut = new PrintWriter(preSocket.getOutputStream(), true);
            Node msgPre = new Node(receivedNode);
            msgPre.setType(nodeType[3]);
            predecessorOut.println(msgPre.toString());
            BufferedReader predecessorIn = new BufferedReader(new InputStreamReader(preSocket.getInputStream()));
            String updateCheck = predecessorIn.readLine();
            if (updateCheck.equals("Updated")) {
                Log.e(TAG, "Predecessor updated");
                preSocket.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Socket Exception {sendToSuccessor}");
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub

        //Deleting files from all AVDs
        if (selection.equals("*")) {
            if (onlyNode == true || myID.equals(pID) || myID.equals(sID)) {
                selection = "@";
            } else {
                ArrayList<String> localKeys = new ArrayList<String>();
                for (Node dataNode : dataNodes) {
                    String nodeKey = dataNode.getKey();
                    localKeys.add(nodeKey);
                    dataNodes.remove(dataNode);
                    break;
                }
                for (String key : localKeys) {
                    try {
                        File toDelete = getContext().getFileStreamPath(key);
                        toDelete.delete();
                        Log.e(TAG, "Deleted File from Local Storage with Key: " + key);
                    } catch (NullPointerException ne) {
                        Log.e(TAG, "File cannot be located");
                    }
                }
                Node newDelete = new Node(myID, myPort, sID, pID, false, nodeType[5], selection, null, null);
                int forwardPort = Integer.parseInt(newDelete.getSid()) * 2;
                try {
                    Socket forwardSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), forwardPort);
                    PrintWriter forwardOut = new PrintWriter(forwardSocket.getOutputStream(), true);
                    forwardOut.println(newDelete.toString());
                    BufferedReader forwardIn = new BufferedReader(new InputStreamReader(forwardSocket.getInputStream()));
                    String receivedDeleteACK = forwardIn.readLine();
                    if (receivedDeleteACK.equals("Deleted File")) {
                        Log.e(TAG, "All files deleted from ID: " + sID);
                    }
                    forwardSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Error retrieving query results from other device {query-else}");
                }
            }
        }

        // Deleting files from local storage
        if (selection.equals("@")) {
            ArrayList<String> localKeys = new ArrayList<String>();
            for (Node dataNode : dataNodes) {
                String nodeKey = dataNode.getKey();
                localKeys.add(nodeKey);
                dataNodes.remove(dataNode);
                break;
            }
            for (String key : localKeys) {
                try {
                    File toDelete = getContext().getFileStreamPath(key);
                    toDelete.delete();
                    Log.e(TAG, "Deleted File from Local Storage with Key: " + key);
                } catch (NullPointerException ne) {
                    Log.e(TAG, "File cannot be located");
                }
            }
        }

        // Deleting for a particular key selection
//        if(dataNodes.isEmpty()){
//            Log.e(TAG,"No files in storage");
//        }
        else if (!selection.equals("*") && !selection.equals("@")) { // For a particular key selection
            Log.e(TAG, "DELETING KEY: " + selection + " in ID: " + myID);
            ArrayList<String> localKeys = new ArrayList<String>();
            for (Node dataNode : dataNodes) {
                String nodeKey = dataNode.getKey();
                localKeys.add(nodeKey);
            }
            if (onlyNode == true || localKeys.contains(selection)) { // If the selection key resides in my own fileStorage
                try {
                    File toDelete = getContext().getFileStreamPath(selection);
                    toDelete.delete();
                    Log.e(TAG, "Deleted File from Local Storage with Key: " + selection);
                } catch (NullPointerException ne) {
                    Log.e(TAG, "File cannot be located");
                }
            } else {
                Node newDelete = new Node(myID, myPort, sID, pID, false, nodeType[5], selection, null, null);
                int forwardPort = Integer.parseInt(newDelete.getSid()) * 2;
                try {
                    Socket forwardSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), forwardPort);
                    PrintWriter forwardOut = new PrintWriter(forwardSocket.getOutputStream(), true);
                    forwardOut.println(newDelete.toString());
                    BufferedReader forwardIn = new BufferedReader(new InputStreamReader(forwardSocket.getInputStream()));
                    String receivedDeleteACK = forwardIn.readLine();
                    Log.e(TAG, "Did I receive confirmation from my successor?: "+ receivedDeleteACK);
                    if (receivedDeleteACK.equals("Deleted File")) {
                        Log.e(TAG, "File deleted from corresponding ID");
                    }
                    forwardSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Error retrieving query results from other device {query-else}");
                }
            }
        }

        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub
        String key = (String) values.get("key");
        String value = (String) values.get("value");
        String keyHashed;
        String myIDHashed = null;

        try {
            keyHashed = genHash(key);
            Node myNode;
            for (Node node : nodeRing) {
                if (myID.equals(node.getID())) {
                    myNode = new Node(node);
                    myIDHashed = myNode.myHash();
                    break;
                }
            }

            Node newFile = new Node(myID, myPort, sID, pID, null, nodeType[1], key, value, null);

            if (onlyNode == true || myID.equals(pID) || myID.equals(sID)) { // Single node scenario {|| myPort.equals(Integer.toString(INITIAL_PORT))}
                Log.e(TAG, "Inserted Value (Local) : Key- " + newFile.getKey() + " Value- " + newFile.getValue());
                fileInsert(newFile);
                dataNodes.add(newFile);
            } else { // Multiple node scenario
                if (keyHashed.compareTo(myIDHashed) <= 0 && keyHashed.compareTo(genHash(pID)) > 0) {
                    Log.e(TAG, "Inserted File (Local) : Key- " + newFile.getKey() + " Value- " + newFile.getValue());
                    fileInsert(newFile);
                    dataNodes.add(newFile);
                } else if ((myIDHashed.compareTo(genHash(pID)) < 0) && (keyHashed.compareTo(myIDHashed) <= 0 || keyHashed.compareTo(genHash(pID)) > 0)) {
                    Log.e(TAG, "Inserted File (END REGION) : Key- " + newFile.getKey() + " Value- " + newFile.getValue());
                    fileInsert(newFile);
                    dataNodes.add(newFile);
                } else {
                    Log.e(TAG, "Forwarded File: Key- " + newFile.getKey() + " Value- " + newFile.getValue() + " To Successor- " + sID);
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, newFile.toString());
                }
            }
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Key can't be hashed {insert}");
        }
        return uri;
    }

    @Override
    public boolean onCreate() {
        // TODO Auto-generated method stub
        TelephonyManager tel = (TelephonyManager) this.getContext().getSystemService(Context.TELEPHONY_SERVICE);
        myID = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(myID) * 2));
        Log.e(TAG, "myID " + myID);
        Log.e(TAG, "myPort " + myPort);
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Error in creating ServerSocket {onCreate}");
        }
        Node newNode;
        if (myPort.equals(Integer.toString(INITIAL_PORT))) {
            Log.e(TAG, "Adding INITIAL node to nodeRing: " + myID);
            sID = myID;
            pID = myID;
            newNode = new Node(myID, myPort, sID, pID, true, nodeType[0], null, null, null);
            nodeRing.add(newNode);
            Collections.sort(nodeRing, Node.nodeHashComparator);
        } else {
            newNode = new Node(myID, myPort, sID, pID, false, nodeType[0], null, null, null);
            String myNode_str = newNode.toString();
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, myNode_str);
        }
        return false;
    }


    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        MatrixCursor mc = new MatrixCursor(new String[]{"key", "value"});
        Log.e(TAG, "Querying for: " + selection);
        Log.e(TAG, "Size of DataNodes: "+ dataNodes.size());
        if (selection.equals("*")) {
            if (onlyNode == true || myID.equals(pID) || myID.equals(sID)) {
                selection = "@";
                Log.e(TAG, "MODE CHANGED TO SINGLE NODE SCENARIO");
            } else {
                Node newQuery = new Node(myID, myPort, sID, pID, false, nodeType[4], selection, null, null);
                Log.e(TAG, "Sending message to successor for '*' query");
                int forwardPort = Integer.parseInt(newQuery.getSid()) * 2;
                try {
                    // Sending * query to successor node
                    Socket forwardSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), forwardPort);
                    PrintWriter forwardOut = new PrintWriter(forwardSocket.getOutputStream(), true);
                    forwardOut.println(newQuery.toString());
                    BufferedReader forwardIn = new BufferedReader(new InputStreamReader(forwardSocket.getInputStream()));
                    String receivedQueryReturn = forwardIn.readLine();
                    Log.e(TAG, "Reply for query '*' from successor: " + receivedQueryReturn);
                    queryString = receivedQueryReturn;
                    forwardSocket.close();
                    // Appending my local files to received query return
                    ArrayList<String> localKeys = new ArrayList<String>();
                    for (Node dataNode : dataNodes) {
                        String nodeKey = dataNode.getKey();
                        localKeys.add(nodeKey);
                    }
                    for (String key : localKeys) {
                        String value = null;
                        FileInputStream fs = getContext().openFileInput(key);
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fs));
                        value = bufferedReader.readLine();
                        bufferedReader.close();
                        queryString = queryString.concat(key).concat(":").concat(value).concat(",");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Log.e(TAG, "Size of queryString (After receiving from all AVD's:" + queryString);
                if(!queryString.equals("")) {
                    String[] splitFile = queryString.split(",");
                    Log.e(TAG, "Number of key-value pairs returned for '*': " + splitFile.length);
                    if (!queryString.equals("")) {
                        for (String eachFile : splitFile) {
                            if (!eachFile.equals("")) {
                                String[] fileValue = eachFile.split(":");
                                if (!fileValue[1].equals(null)) {
                                    mc.addRow(new String[]{fileValue[0], fileValue[1]});
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selection.equals("@")) { // For local files
            for (Node dataNode : dataNodes) {
                String nodeKey = dataNode.getKey();
                String value = "";
                try {
                    FileInputStream fs = getContext().openFileInput(nodeKey);
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fs));
                    value = bufferedReader.readLine();
                    bufferedReader.close();
                } catch (FileNotFoundException fe) {
                    Log.e(TAG, "File Not Found Exception {query}");
                } catch (IOException e) {
                    Log.e(TAG, "IO Exception {query}");
                }
                Log.e(TAG, "Value Returned: " + value);
                if (!value.equals("")) {
                    mc.addRow(new String[]{nodeKey, value});
                }
            }
        }

        if (!selection.equals("*") && !selection.equals("@")) { // For a particular key selection
            Log.e(TAG, "SEARCHING FOR KEY: " + selection + " in ID: " + myID);
            ArrayList<String> localKeys = new ArrayList<String>();
            for (Node dataNode : dataNodes) {
                String nodeKey = dataNode.getKey();
                localKeys.add(nodeKey);
            }
//            Log.e("TAG", "Printing localFiles Keys: ");
//            for (String key : localKeys) {
//                Log.e("TAG", "\n" + key);
//            }
            if (onlyNode == true || localKeys.contains(selection)) { // If the selection key resides in my own fileStorage
                String value = "";
                try {
                    FileInputStream fs = getContext().openFileInput(selection);
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fs));
                    value = bufferedReader.readLine();
                    bufferedReader.close();
                } catch (FileNotFoundException fe) {
                    Log.e(TAG, "File Not Found Exception {query}");
                } catch (IOException e) {
                    Log.e(TAG, "IO Exception {query}");
                }
                Log.e(TAG, "Value Returned: " + value);
                if (!value.equals("")) {
                    mc.addRow(new String[]{selection, value});
                }
            } else { // If the key doesn't exist in my file storage
                Node newQuery = new Node(myID, myPort, sID, pID, false, nodeType[4], selection, null, null);
                int forwardPort = Integer.parseInt(newQuery.getSid()) * 2;
                try {
                    Socket forwardSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), forwardPort);
                    PrintWriter forwardOut = new PrintWriter(forwardSocket.getOutputStream(), true);
                    forwardOut.println(newQuery.toString());
                    BufferedReader forwardIn = new BufferedReader(new InputStreamReader(forwardSocket.getInputStream()));
                    String receivedQueryReturn = forwardIn.readLine();
                    String[] receivedQueryReturn_Split = receivedQueryReturn.split("-");
                    Node receivedQueryReturn_Node = new Node(receivedQueryReturn_Split);
                    queryString = receivedQueryReturn_Node.getValue();
                    forwardSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Error retrieving query results from other device {query-else}");
                }
                if (!queryString.equals(null)) {
                    mc.addRow(new String[]{newQuery.getKey(), queryString});
                }
            }
        }
        // TODO Auto-generated method stub
        return mc;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    // Hash generation
    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }


    // ServerTask starts here
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            while (true) {
                try {
                    Socket socket = serverSocket.accept();
                    PrintWriter outStream = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader inStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String input = inStream.readLine();
                    Log.e(TAG, "MESSAGE RECEIVED ON SERVER: " + input);
                    String[] receivedString = input.split("-");
                    Node receivedNode = new Node(receivedString);

                    if (receivedNode.getType().equals(nodeType[0])) { // Join
//                        Log.e(TAG, "New Node Joining:");
//                        receivedNode.print();
                        String receivedIDHash = receivedNode.myHash();
                        int indexOfReceived = 0;
                        nodeRing.add(receivedNode);
                        Collections.sort(nodeRing, Node.nodeHashComparator);
                        for (Node nodeCheck : nodeRing) {
                            if (receivedIDHash.equals(nodeCheck.myHash())) {
                                break;
                            }
                            indexOfReceived++;
                        }
                        int sIndex = (indexOfReceived == (nodeRing.size() - 1)) ? 0 : indexOfReceived + 1;
                        int pIndex = (indexOfReceived == 0) ? (nodeRing.size() - 1) : indexOfReceived - 1;
                        receivedNode.setSid(nodeRing.get(sIndex).getID());
                        receivedNode.setPid(nodeRing.get(pIndex).getID());
                        receivedNode.setJoinStatus("true");
                        outStream.println(receivedNode.toString());
                        socket.close();
                        Log.e(TAG, "NodeID: " + receivedNode.getID() + " has been added to nodeRing");
                    } else if (receivedNode.getType().equals(nodeType[2])) { // SucUpdate
                        sID = receivedNode.getID();
                        outStream.println("Updated");
                        Log.e(TAG, "sID after SucUpdate " + sID);
                    } else if (receivedNode.getType().equals(nodeType[3])) { // PreUpdate
                        pID = receivedNode.getID();
                        outStream.println("Updated");
                        Log.e(TAG, "pID after PreUpdate " + pID);
                    } else if (receivedNode.getType().equals(nodeType[1])) { // File Insert
                        Log.e(TAG, "Received file from ID: " + receivedNode.getID() + " Key: " + receivedNode.getKey() + " Value: " + receivedNode.getValue());
                        Log.e(TAG, "Checking insert conditions");
                        outStream.println("Received");
                        if (checkBeforeInsert(receivedNode) == true) {
                            fileInsert(receivedNode);
                            String fromID = receivedNode.getID();
                            receivedNode.setID(myID);
                            receivedNode.setPortNum(myPort);
                            receivedNode.setSid(sID);
                            receivedNode.setPid(pID);
                            dataNodes.add(receivedNode);
                            Log.e(TAG, "File Inserted! From: " + fromID + " Key: " + receivedNode.getKey() + " Value: " + receivedNode.getValue());
                        } else {
//                            receivedNode.setID(myID);
//                            receivedNode.setPortNum(myPort);
                            receivedNode.setSid(sID);
                            receivedNode.setPid(pID);
                            Log.e(TAG, "Forwarded File again: Key- " + receivedNode.getKey() + " Value- " + receivedNode.getValue() + " To Successor- " + sID);
                            int forwardPort = Integer.parseInt(receivedNode.getSid()) * 2;
                            Socket forwardSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), forwardPort);
                            PrintWriter forwardOut = new PrintWriter(forwardSocket.getOutputStream(), true);
                            forwardOut.println(receivedNode.toString());
                            BufferedReader forwardIn = new BufferedReader(new InputStreamReader(forwardSocket.getInputStream()));
                            String receivedInsert = forwardIn.readLine();
                            if (receivedInsert.equals("Received")) {
                                forwardSocket.close();
                            }
                        }
                    } else if (receivedNode.getType().equals(nodeType[4])) { // Query
                        ArrayList<String> localKeys_Query = new ArrayList<String>();
                        for (Node dataNode : dataNodes) {
                            String nodeKey = dataNode.getKey();
                            localKeys_Query.add(nodeKey);
                        }
                        Log.e(TAG, "Size of dataNodes: " + dataNodes.size());
                        String receivedQueryReturn = "";
                        if (localKeys_Query.contains(receivedNode.getKey())) {
                            Log.e(TAG, "Queried key found at local storage and value is returned to predecessor");
                            String value = null;
                            FileInputStream fs = getContext().openFileInput(receivedNode.getKey());
                            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fs));
                            value = bufferedReader.readLine();
                            bufferedReader.close();
                            receivedNode.setValue(value);
                            receivedNode.setSid(sID);
                            receivedNode.setPid(pID);
                            outStream.println(receivedNode.toString());
                        } else if (receivedNode.getKey().equals("*")) {
                            if (!receivedNode.getID().equals(sID)) {
                                Socket forwardSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(sID) * 2);
                                PrintWriter forwardOut = new PrintWriter(forwardSocket.getOutputStream(), true);
                                forwardOut.println(receivedNode.toString());
                                BufferedReader forwardIn = new BufferedReader(new InputStreamReader(forwardSocket.getInputStream()));
                                receivedQueryReturn = forwardIn.readLine();
                                forwardSocket.close();
                            }
                            for (String key : localKeys_Query) {
//                                String value = null;
                                FileInputStream fs = getContext().openFileInput(key);
                                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fs));
                                String value = bufferedReader.readLine();
                                bufferedReader.close();
                                receivedQueryReturn = receivedQueryReturn.concat(key).concat(":").concat(value).concat(",");
                                Log.e(TAG, "THIS MESSAGE SHOULD NOT APPEAR IF SIZE OF DATANODES IS 0");
                            }
                            Log.e(TAG, "Query for '*' received! Returning local files to predecessor and forwarding '*' query to successor");
                            outStream.println(receivedQueryReturn);
                        } else {
                            Socket forwardSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(sID) * 2);
                            PrintWriter forwardOut = new PrintWriter(forwardSocket.getOutputStream(), true);
                            forwardOut.println(receivedNode.toString());
                            BufferedReader forwardIn = new BufferedReader(new InputStreamReader(forwardSocket.getInputStream()));
                            String queryValueFromSuccessor = forwardIn.readLine();
                            forwardSocket.close();
                            Log.e(TAG, "Queried key not found in local file! Forwarding query to successor");
                            outStream.println(queryValueFromSuccessor);
                        }
                    } else if (receivedNode.getType().equals(nodeType[5])) { // Delete
                        ArrayList<String> localKeys = new ArrayList<String>();
                        for (Node dataNode : dataNodes) {
                            String nodeKey = dataNode.getKey();
                            localKeys.add(nodeKey);
                        }
                        if (localKeys.contains(receivedNode.getKey())) {
                            Log.e(TAG,"File found in local storage - Server");
                            String keyToDelete = receivedNode.getKey();
                            File toDelete = getContext().getFileStreamPath(keyToDelete);
                            toDelete.delete();
                            Log.e(TAG,"File delete performed - Server");
//                            if(toDelete.delete()) {
                            for (Node dataNode : dataNodes) {
                                Log.e(TAG, "Inside for-loop to remove dataNode");
                                String nodeKey = dataNode.getKey();
                                if (nodeKey.equals(keyToDelete)) {
                                    dataNodes.remove(dataNode);
                                    Log.e(TAG, "Removing file from dataNodes");
                                    break;
                                }
                            }
//                        }
                            Log.e(TAG, "Deleted File from Local Storage with Key: " + keyToDelete);
                            outStream.println("Deleted File");
                        } else if (receivedNode.getKey().equals("*")) {
                            if (!receivedNode.getID().equals(sID)) {
                                int forwardPort = Integer.parseInt(sID) * 2;
                                Socket forwardSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), forwardPort);
                                PrintWriter forwardOut = new PrintWriter(forwardSocket.getOutputStream(), true);
                                forwardOut.println(receivedNode.toString());
                                BufferedReader forwardIn = new BufferedReader(new InputStreamReader(forwardSocket.getInputStream()));
                                String receivedDeleteACK = forwardIn.readLine();
                                if (receivedDeleteACK.equals("Deleted File")) {
                                    Log.e(TAG, "File deleted from corresponding ID");
                                }
                                forwardSocket.close();
                            }
                            for (Node dataNode : dataNodes) {
                                String nodeKey = dataNode.getKey();
                                localKeys.add(nodeKey);
                                dataNodes.remove(dataNode);
                                break;
                            }
                            for (String key : localKeys) {
                                File toDelete = getContext().getFileStreamPath(key);
                                toDelete.delete();
                                Log.e(TAG, "Deleted File from Local Storage with Key: " + key);
                            }
                            Log.e(TAG, "Delete '*' received! Deleting all local files and forwarding delete '*' to successor");
                            outStream.println("Deleted File");
                        } else {
                            if (!receivedNode.getID().equals(sID)) {
                                int forwardPort = Integer.parseInt(sID) * 2;
                                Socket forwardSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), forwardPort);
                                PrintWriter forwardOut = new PrintWriter(forwardSocket.getOutputStream(), true);
                                forwardOut.println(receivedNode.toString());
                                BufferedReader forwardIn = new BufferedReader(new InputStreamReader(forwardSocket.getInputStream()));
                                String receivedDeleteACK = forwardIn.readLine();
                                if (receivedDeleteACK.equals("Deleted File")) {
                                    Log.e(TAG, "File deleted from corresponding ID");
                                }
                                forwardSocket.close();
                                Log.e(TAG, "Key to be deleted not found in local file storage! Forwarding query to successor");
                            }
                            outStream.println("Deleted File");
                        }
                    }

//                    Displaying Node Ring
//                    Log.e(TAG, "This is the nodeRing: ");
//                    int j = 0;
//                    for (Node node : nodeRing) {
//                        Log.e(TAG, j + "  " + node.toString());
//                        j++;
//                    }

                } catch (UnknownHostException e) {
                    e.printStackTrace();
                    Log.e(TAG, "UnknownHostException {ServerTask}");
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "IOException {ServerTask}");
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
//            return null;
        }
    }

    //ClientTask starts here
    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... nodeString) {
            String fromClientTask = nodeString[0];
            String[] node_str = fromClientTask.split("-");
            Node newNode = new Node(node_str);
            Socket clientSocket = null;

            // Node Join
            if (newNode.getType().equals(nodeType[0]) && onlyNode == false) {
                try {
                    clientSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), INITIAL_PORT);
                    PrintWriter outStream = new PrintWriter(clientSocket.getOutputStream(), true);
                    outStream.println(newNode.toString());
                    BufferedReader inStream = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    String receivedString = inStream.readLine();
                    clientSocket.close();
                    if (receivedString.equals(null)) {
                        throw new Exception("Socket didn't respond! {client}");
                    }
                    String[] receivedSplit = receivedString.split("-");
                    Node receivedNode = new Node(receivedSplit);
                    Log.e(TAG, "Received Node from INITIAL_PORT: ");
                    receivedNode.print();
                    sID = receivedNode.getSid();
                    pID = receivedNode.getPid();
                    Log.e(TAG, "sID after Receiving from INITIAL " + sID);
                    Log.e(TAG, "pID after Receiving from INITIAL " + pID);
                    nodeRing.add(receivedNode);
                    sendToPredecessor(receivedNode);
                    sendToSuccessor(receivedNode);

                } catch (Exception e) {
                    Log.e(TAG, "Single Node Scenario");
                    onlyNode = true;
                }
                if (onlyNode == true) {
                    nodeRing.add(newNode);
                    Collections.sort(nodeRing, Node.nodeHashComparator);
                    Log.e(TAG, "Printing nodeRing (onlyMode==true)");
                    for (int i = 0; i < nodeRing.size(); i++) {
                        Log.e(TAG, i + " " + nodeRing.get(i).getID());
                    }
                }
            }

            // File Insert
            if (newNode.getType().equals(nodeType[1])) {
                int forwardPort = Integer.parseInt(newNode.getSid()) * 2;
                try {
                    Socket forwardSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), forwardPort);
                    PrintWriter forwardOut = new PrintWriter(forwardSocket.getOutputStream(), true);
                    forwardOut.println(newNode.toString());
                    BufferedReader forwardIn = new BufferedReader(new InputStreamReader(forwardSocket.getInputStream()));
                    String receivedInsert = forwardIn.readLine();
                    if (receivedInsert.equals("Received")) {
                        forwardSocket.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return null;
        }

    }

}
