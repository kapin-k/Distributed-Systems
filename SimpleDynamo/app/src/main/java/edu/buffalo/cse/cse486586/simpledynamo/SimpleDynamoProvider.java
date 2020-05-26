package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDynamoProvider extends ContentProvider {

    static final int SERVER_PORT = 10000;
    public static String PA4Uri = "edu.buffalo.cse.cse486586.simpledynamo.provider";
    List<String> portList = new ArrayList<String>(Arrays.asList("11108", "11112", "11116", "11120", "11124"));
    static String myID; // 555*
    static String myPort; // 111*
    static String myHash;
    List<Node> nodeList = new ArrayList<Node>();
    List<File> fileList = new ArrayList<File>();
    List<File> failedFileList = new ArrayList<File>();
    public String queryString = "";
    public String failedString = "";

    public static Uri PA4Uri() {
        Uri.Builder builder = new Uri.Builder();
        builder.authority(PA4Uri);
        builder.scheme("content");
        Uri uri = builder.build();
        return uri;
    }

    public void updateLocalValues(ArrayList<String> localKeys) {
        try {
            MatrixCursor updateCursor = new MatrixCursor(new String[]{"key", "value"});
            for (int i = 0; i < nodeList.size(); i++) {
                Log.e("updateLocalValues", "Hitting Port: " + nodeList.get(i).getPort());
                Socket updateSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(nodeList.get(i).getPort()));
                PrintWriter outStream = new PrintWriter(updateSocket.getOutputStream(), true);
                String msgToSend = "Query" + "-" + "*" + "-" + "updateCheck";
                outStream.println(msgToSend);
                BufferedReader inStream = new BufferedReader(new InputStreamReader(updateSocket.getInputStream()));
                String received = inStream.readLine();
                if (!received.equals("")) {
                    String[] splitFile = received.split(",");
                    Log.e("updateLocalValues", "UPDATE CHECK - Number of key-value pairs returned for '*': " + splitFile.length);
                    for (String eachFile : splitFile) {
                        if (!eachFile.equals("")) {
                            String[] fileValue = eachFile.split(":");
                            if (!fileValue[1].equals("")) {
                                updateCursor.addRow(new String[]{fileValue[0], fileValue[1]});
                            }
                        }
                    }
                }
            }
            for (String lKey : localKeys) {
                while (updateCursor.moveToNext()) {
                    String key = updateCursor.getString(updateCursor.getColumnIndex("key"));
                    String value = updateCursor.getString(updateCursor.getColumnIndex("value"));
                    if (lKey.equals(key)) {
                        java.io.File toDelete = getContext().getFileStreamPath(key);
                        toDelete.delete();
                        Log.e("updateLocalValues", "Deleted File from Local Storage with Key: " + key);
                        fileInsert(new File(key, value, genHash(key)));
                        Log.e("updateLocalValues", "Inserted Updated Value- Key: " + key + " Value: " + value);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("updateLocalValues", "zzzzzzzzzzzzzzzzzzz");
        }
    }

    public void sendUsingClientTask(String msgToSend) { // Randomly unable to access new ClientTask within ServerTask
        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgToSend);
    }

    public void fileInsert(File fileNode) {
        FileOutputStream fileOutputStream;
        try {
            fileOutputStream = getContext().openFileOutput(fileNode.getKey(), Context.MODE_PRIVATE);
            fileOutputStream.write(fileNode.getValue().getBytes());
            fileOutputStream.close();
        } catch (Exception e) {
            Log.e("fileInsert", "Failed to write file");
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Log.e("delete", "Delete Query Received: " + selection);
        ArrayList<String> localKeys_Delete = new ArrayList<String>();
        java.io.File[] local_files = getContext().getFilesDir().listFiles();
        for (java.io.File fileItem : local_files) {
            localKeys_Delete.add(fileItem.getName());
        }
        // Deleting local/global files
        if (selection.equals("@") || selection.equals("*")) {
            Log.e("delete-*", "Deleting at Port: " + myID);
            for (String keyToDelete : localKeys_Delete) {
                java.io.File toDelete = getContext().getFileStreamPath(keyToDelete);
                toDelete.delete();
            }
            Log.e("delete-*@", "All files in my local storage has been deleted");
        }
        // Deleting specific key-value pair
        else {
            Log.e("delete-*", "Deleting at Port: " + myID);
            for (String keyToDelete : localKeys_Delete) {
                if (keyToDelete.equals(selection)) {
                    java.io.File toDelete = getContext().getFileStreamPath(keyToDelete);
                    toDelete.delete();
                    Log.e("delete-key", "File deleted with Key: " + keyToDelete);
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
        String key = (String) values.get("key");
        String value = (String) values.get("value");
        try {
            File newFile = new File(key, value, genHash(key));
            Boolean endRegion = true;
            int insertAt = 0;
            int insert_1 = 0;
            int insert_2 = 0;
            for (int i = 0; i < nodeList.size(); i++) {
                if (newFile.getKeyHash().compareTo(nodeList.get(i).getHash()) < 0) {
                    insertAt = i;
                    endRegion = false;
                    break;
                }
            }
            if (endRegion == true) {
                Log.e("insert", "End Region Value is detected");
                insertAt = 0;
            }
            switch (insertAt) {
                case 0:
                    insert_1 = 1;
                    insert_2 = 2;
                    break;
                case 1:
                    insert_1 = 2;
                    insert_2 = 3;
                    break;
                case 2:
                    insert_1 = 3;
                    insert_2 = 4;
                    break;
                case 3:
                    insert_1 = 4;
                    insert_2 = 0;
                    break;
                case 4:
                    insert_1 = 0;
                    insert_2 = 1;
                    break;
            }
            List<Integer> replicatedNodeIndex = new ArrayList<Integer>();
            replicatedNodeIndex.add(insertAt);
            replicatedNodeIndex.add(insert_1);
            replicatedNodeIndex.add(insert_2);
            Log.e("insert", "Ports to be inserted at: " + nodeList.get(insertAt).getPort() + " " + nodeList.get(insert_1).getPort() + " " + nodeList.get(insert_2).getPort());
            for (int index : replicatedNodeIndex) {
                if (nodeList.get(index).getPort().equals(myPort)) {
                    Log.e("insert", "Inserted File (Local-At) : Key- " + newFile.getKey() + " Value- " + newFile.getValue());
                    fileInsert(newFile);
                    fileList.add(newFile);
                }
                Log.e("insert", "File Forwarded : Key- " + newFile.getKey() + " Value- " + newFile.getValue() + " To Port: " + nodeList.get(insertAt).getPort());
                String newClientTask = "Insert" + "-" + newFile.toString() + "-" + nodeList.get(index).getPort();
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, newClientTask);
            }
//            if (nodeList.get(insertAt).getPort().equals(myPort)) {
//                Log.e("insert", "Inserted File (Local-At) : Key- " + newFile.getKey() + " Value- " + newFile.getValue());
//                fileInsert(newFile);
//                fileList.add(newFile);
//                Log.e("insert", "File Forwarded : Key- " + newFile.getKey() + " Value- " + newFile.getValue() + " To Port: " + nodeList.get(insertAt).getPort());
//                String newClientTask = "Insert" + "-" + newFile.toString() + "-" + nodeList.get(insertAt).getPort();
//                sendUsingClientTask(newClientTask);
//            }
//            else {
//                Log.e("insert-else", "Insert Location At Local Failed!");
//                Log.e("insert-else", "File Forwarded : Key- " + newFile.getKey() + " Value- " + newFile.getValue() + " To Port: " + nodeList.get(insertAt).getPort());
//                String newClientTask = "Insert" + "-" + newFile.toString() + "-" + nodeList.get(insertAt).getPort();
//                sendUsingClientTask(newClientTask);
//            }
//            if (nodeList.get(insert_1).getPort().equals(myPort)) {
//                Log.e("insert", "Inserted File (Local-At) : Key- " + newFile.getKey() + " Value- " + newFile.getValue());
//                fileInsert(newFile);
//                fileList.add(newFile);
//                Log.e("insert", "File Forwarded : Key- " + newFile.getKey() + " Value- " + newFile.getValue() + " To Port: " + nodeList.get(insert_1).getPort());
//                String newClientTask = "Insert" + "-" + newFile.toString() + "-" + nodeList.get(insert_1).getPort();
//                sendUsingClientTask(newClientTask);
//            }
//            else {
//                Log.e("insert-else", "Insert Location At Local Failed!");
//                Log.e("insert-else", "File Forwarded : Key- " + newFile.getKey() + " Value- " + newFile.getValue() + " To Port: " + nodeList.get(insert_1).getPort());
//                String newClientTask = "Insert" + "-" + newFile.toString() + "-" + nodeList.get(insert_1).getPort();
//                sendUsingClientTask(newClientTask);
//            }
//            if (nodeList.get(insert_2).getPort().equals(myPort)) {
//                Log.e("insert", "Inserted File (Local-At) : Key- " + newFile.getKey() + " Value- " + newFile.getValue());
//                fileInsert(newFile);
//                fileList.add(newFile);
//                Log.e("insert", "File Forwarded : Key- " + newFile.getKey() + " Value- " + newFile.getValue() + " To Port: " + nodeList.get(insert_2).getPort());
//                String newClientTask = "Insert" + "-" + newFile.toString() + "-" + nodeList.get(insert_2).getPort();
//                sendUsingClientTask(newClientTask);
//            }
//            else {
//                Log.e("insert-else", "Insert Location At Local Failed!");
//                Log.e("insert-else", "File Forwarded : Key- " + newFile.getKey() + " Value- " + newFile.getValue() + " To Port: " + nodeList.get(insert_2).getPort());
//                String newClientTask = "Insert" + "-" + newFile.toString() + "-" + nodeList.get(insert_2).getPort();
//                sendUsingClientTask(newClientTask);
//            }
        } catch (NoSuchAlgorithmException nse) {
            Log.e("insert", "Key can't be hashed!");
        } catch (Exception e) {
            Log.e("insert", "Exception!");
        }
        return null;
    }

    @Override
    public boolean onCreate() {
        try {
            TelephonyManager tel = (TelephonyManager) this.getContext().getSystemService(Context.TELEPHONY_SERVICE);
            myID = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
            myPort = String.valueOf((Integer.parseInt(myID) * 2));
            myHash = genHash(myID);
            Node myNode = new Node(myID, myPort, myHash);
            Log.e("onCreate", "myID " + myID);
            Log.e("onCreate", "myPort " + myPort);
            Log.e("onCreate", "myNode " + myNode.toString());
            for (int i = 0; i < portList.size(); i++) {
                String new_id = Integer.toString(Integer.parseInt(portList.get(i)) / 2);
                String new_port = portList.get(i);
                Node newNode = new Node(new_id, new_port, genHash(new_id));
                nodeList.add(newNode);
                Log.e("onCreate", "Node added to nodeList: " + newNode.toString());
            }
            Collections.sort(nodeList, Node.nodeHashComparator);
            Log.e("onCreate", "nodeList after sort: ");
            for (Node nodeIn : nodeList) {
                Log.e("onCreate", nodeIn.getID());
            }
            try {
                ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
                new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
            } catch (Exception e) {
                Log.e("onCreate-ServSock", "Can't open ServerSocket!");
            }
            Log.e("onCreate", "Pinging other nodes asking for missed keys");
            for (int i = 0; i < portList.size(); i++) {
                if (!portList.get(i).equals(myPort)) {
                    String msgToSend = "Failed_Keys" + "-" + myPort + "-" + portList.get(i);
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgToSend);
                }
            }
        } catch (NoSuchAlgorithmException ne) {
            ne.printStackTrace();
            Log.e("onCreate", "No such algorithm exception caught!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("onCreate", "Exception!");
        }
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {

        MatrixCursor mc = new MatrixCursor(new String[]{"key", "value"});
        Log.e("query", "Querying for: " + selection);

        if (selection.equals("@")) { // For local files
            ArrayList<String> localKeys_Query = new ArrayList<String>();
            java.io.File[] localFiles = getContext().getFilesDir().listFiles();
            for (java.io.File localFile : localFiles) {
                localKeys_Query.add(localFile.getName());
            }
            Log.e("query-@", "Number of local files: " + localKeys_Query.size());
            for (String key : localKeys_Query) {
                String value;
                try {
                    FileInputStream fs = getContext().openFileInput(key);
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fs));
                    value = bufferedReader.readLine();
                    bufferedReader.close();
                    Log.e("query-@", "Key-Value Returned: Key- " + key + " Value- " + value);
                    if (!value.equals("")) {
                        mc.addRow(new String[]{key, value});
                    }
                } catch (FileNotFoundException fe) {
                    Log.e("query-@", "File Not Found Exception {query}");
                } catch (IOException e) {
                    Log.e("query-@", "IO Exception {query}");
                }
            }

        }


        if (selection.equals("*")) { // For global search
            Log.e("query-*", "Sending message to other nodes for '*' query");
            queryString = "";
            try {
                // Appending my local files to received query return
                ArrayList<String> localKeys_Query = new ArrayList<String>();
                java.io.File[] local_files = getContext().getFilesDir().listFiles();
                if (local_files.length != 0) {
                    for (java.io.File fileItem : local_files) {
                        localKeys_Query.add(fileItem.getName());
                    }
                }
                for (String key : localKeys_Query) {
                    String value;
                    FileInputStream fs = getContext().openFileInput(key);
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fs));
                    value = bufferedReader.readLine();
                    bufferedReader.close();
                    queryString = queryString.concat(key).concat(":").concat(value).concat(",");
                }
                if (!queryString.equals("")) {
                    String[] splitFile = queryString.split(",");
                    Log.e("query-*", "Number of key-value pairs returned for '*': " + splitFile.length);
                    for (String eachFile : splitFile) {
                        if (!eachFile.equals("")) {
                            String[] fileValue = eachFile.split(":");
                            mc.addRow(new String[]{fileValue[0], fileValue[1]});
                        }
                    }
                }
                queryString = "";
                for (int j = 0; j < nodeList.size(); j++) {
                    try {
                        if (!nodeList.get(j).getPort().equals(myPort)) {
                            Log.e("query-*", "Sending * to: " + nodeList.get(j).getPort());
                            Socket forwardSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(nodeList.get(j).getPort()));
                            PrintWriter forwardOut = new PrintWriter(forwardSocket.getOutputStream(), true);
                            String msgToSend = "Query" + "-" + "*";
                            forwardOut.println(msgToSend);
                            BufferedReader forwardIn = new BufferedReader(new InputStreamReader(forwardSocket.getInputStream()));
                            String receivedQueryReturn = forwardIn.readLine();
                            forwardIn.close();
                            Log.e("query-*", "Reply for query '*' from successor: " + receivedQueryReturn);
                            queryString = queryString.concat(receivedQueryReturn);
                            forwardSocket.close();
                            if (!queryString.equals("")) {
                                String[] splitFile = queryString.split(",");
                                Log.e("query-*", "Number of key-value pairs returned for '*': " + splitFile.length);
                                for (String eachFile : splitFile) {
                                    if (!eachFile.equals("")) {
                                        String[] fileValue = eachFile.split(":");
                                        mc.addRow(new String[]{fileValue[0], fileValue[1]});
                                    }
                                }
                            }
                        }
                    } catch (Exception ex) {
                        Log.e("query-*", "Special Catch");
                        continue;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (!selection.equals("*") && !selection.equals("@")) {
            int queryAt = 99;// For a particular key selection
            Log.e("query-key", "SEARCHING FOR KEY: " + selection + " in ID: " + myID);
            ArrayList<String> localKeys = new ArrayList<String>();
            java.io.File[] localFiles = getContext().getFilesDir().listFiles();
            for (java.io.File fileItem : localFiles) {
                localKeys.add(fileItem.getName());
            }

            if (localKeys.contains(selection)) { // If the selection key resides in my own fileStorage
                Log.e("query-key", "Key found at local storage");
                String value;
                try {
                    FileInputStream fs = getContext().openFileInput(selection);
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fs));
                    value = bufferedReader.readLine();
                    bufferedReader.close();
                    Log.e("query-key", "Key-Value Returned: Key- " + selection + " Value- " + value);
                    if (!value.equals("")) {
                        mc.addRow(new String[]{selection, value});
                    }
                } catch (FileNotFoundException fe) {
                    Log.e("query-key", "File Not Found Exception");
                } catch (IOException e) {
                    Log.e("query-key", "IO Exception");
                }
            } else { // If the key doesn't exist in my file storage
                try {
                    String keyHash = genHash(selection);
                    Boolean endRegion = true;
                    int pID;
                    for (int i = 0; i < nodeList.size(); i++) {
                        if (i == 0) {
                            pID = nodeList.size() - 1;
                        } else {
                            pID = i - 1;
                        }
                        if (keyHash.compareTo(nodeList.get(i).getHash()) < 0) {
//                        if(keyHash.compareTo(nodeList.get(i).getHash()) <= 0 && keyHash.compareTo(nodeList.get(pID).getHash()) > 0){
                            queryAt = i;
                            endRegion = false;
                            break;
                        }
                    }
                    if (endRegion == true) {
                        Log.e("query", "End Region Value is detected");
                        queryAt = 0;
                    }
                    Log.e("query-key", "Checking for the key-value at " + nodeList.get(queryAt).getID());
                    Socket forwardSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(nodeList.get(queryAt).getPort()));
                    PrintWriter forwardOut = new PrintWriter(forwardSocket.getOutputStream(), true);
                    forwardOut.println("Query" + "-" + selection);
                    BufferedReader forwardIn = new BufferedReader(new InputStreamReader(forwardSocket.getInputStream()));
                    String receivedQueryReturn = forwardIn.readLine();
                    forwardIn.close();
                    forwardSocket.close();
                    Log.e("query-key", "Received Query Value: " + receivedQueryReturn);
                    if (receivedQueryReturn.length() != 0) {
                        mc.addRow(new String[]{selection, receivedQueryReturn});
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    int query_1 = queryAt + 1;
                    if (queryAt == 4) {
                        query_1 = 0;
                    }
                    try {
                        Log.e("query-key", "Checking for the key-value at " + nodeList.get(query_1).getID());
                        Socket forwardSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(nodeList.get(query_1).getPort()));
                        PrintWriter forwardOut = new PrintWriter(forwardSocket.getOutputStream(), true);
                        forwardOut.println("Query" + "-" + selection);
                        BufferedReader forwardIn = new BufferedReader(new InputStreamReader(forwardSocket.getInputStream()));
                        String receivedQueryReturn = forwardIn.readLine();
                        Log.e("query-key", "Received Query Value: " + receivedQueryReturn);
                        if (receivedQueryReturn.length() != 0) {
                            mc.addRow(new String[]{selection, receivedQueryReturn});
                        }
                    } catch (Exception abc) {
                        abc.printStackTrace();
                        Log.e("query-key", "THIS CANT HAPPEN EVER!");
                    }
                }
            }
        }
        return mc;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

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
            try {
                while (true) {
                    Socket socket = serverSocket.accept();
//                    PrintWriter outStream = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader inStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String input = inStream.readLine();
                    String[] input_split;
                    Log.e("ServerTask", "MESSAGE RECEIVED ON SERVER: " + input);
                    if (!input.equals(null)) {
                        input_split = input.split("-");

                        // Insert File
                        if (input_split[0].equals("Insert")) {
                            File receivedFile = new File(input_split[1], input_split[2], input_split[3]);
                            fileInsert(receivedFile);
                            fileList.add(receivedFile);
                            Log.e("ServerTask-insert", "File inserted: " + receivedFile.toString());
                            PrintWriter outStream = new PrintWriter(socket.getOutputStream(), true);
                            outStream.println("ACK");
//                            socket.close();
                        }

                        //Query
                        if (input_split[0].equals("Query")) {
                            queryString = "";
                            ArrayList<String> localKeys_Query = new ArrayList<String>();
                            java.io.File[] local_files = getContext().getFilesDir().listFiles();
                            for (java.io.File fileItem : local_files) {
                                localKeys_Query.add(fileItem.getName());
                            }

                            Log.e("ServerTask-query", "Size of local fileList: " + localKeys_Query.size());
                            if (input_split[1].equals("*")) {
                                for (String key : localKeys_Query) {
                                    String value = null;
                                    FileInputStream fs = getContext().openFileInput(key);
                                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fs));
                                    value = bufferedReader.readLine();
                                    bufferedReader.close();
                                    queryString = queryString.concat(key).concat(":").concat(value).concat(",");
                                }
                                PrintWriter outStream = new PrintWriter(socket.getOutputStream(), true);
                                outStream.println(queryString);
//                                socket.close();
                            } else {
                                Log.e("ServTask-queryKey", "input_split[1]: " + input_split[1]);
                                Cursor cursor = getContext().getContentResolver().query(PA4Uri(), null, input_split[1], null, null);
                                queryString = "";
                                while (cursor.moveToNext()) {
//                                    String valueforKey = cursor.getString(cursor.getColumnIndex("value"));
//                                    if (cursor.getString(cursor.getColumnIndex("key").equals(input_split[1])) {
                                    queryString = cursor.getString(cursor.getColumnIndex("value"));
//                                    }
                                }
                                Log.e("ServTask-queryKey", queryString);
                                PrintWriter outStream = new PrintWriter(socket.getOutputStream(), true);
                                outStream.println(queryString);
//                                socket.close();
                            }
                        }

                        // Delete
//                        if (input_split[0].equals("Delete")) {
//                            if (input_split[1].equals("@")) {
//                                ArrayList<String> localKeys_Delete = new ArrayList<String>();
//                                java.io.File[] localFiles = getContext().getFilesDir().listFiles();
//                                for (java.io.File localFile : localFiles) {
//                                    localKeys_Delete.add(localFile.getName());
//                                }
//                                fileList.clear();
//                                for (String key : localKeys_Delete) {
//                                    try {
//                                        java.io.File toDelete = getContext().getFileStreamPath(key);
//                                        toDelete.delete();
//                                        Log.e("delete-@", "Deleted File from Local Storage with Key: " + key);
//                                    } catch (NullPointerException ne) {
//                                        Log.e("delete-@", "File cannot be located");
//                                    }
//                                }
//                                socket.close();
//                            } else {
//                                ArrayList<String> localKeys_Delete = new ArrayList<String>();
//                                java.io.File[] localFiles = getContext().getFilesDir().listFiles();
//                                for (java.io.File localFile : localFiles) {
//                                    localKeys_Delete.add(localFile.getName());
//                                }
//                                if (localKeys_Delete.contains(input_split[1])) { // If the selection key resides in my own fileStorage
//                                    try {
//                                        java.io.File toDelete = getContext().getFileStreamPath(input_split[1]);
//                                        toDelete.delete();
//                                        socket.close();
//                                        Log.e("delete-key", "Deleted File from Local Storage with Key: " + input_split[1]);
//                                    } catch (NullPointerException ne) {
//                                        Log.e("delete-key", "File cannot be located");
//                                    }
//                                } else {
//                                    socket.close();
//                                }
//                            }
//                        }

                        // Failed Key Return
                        if (input_split[0].equals("Failed_Keys")) {
                            Log.e("ServerTask-FailedKeys", "Returning my failedKeyList to: " + input_split[1]);
                            failedString = "";
                            for (int i = 0; i < failedFileList.size(); i++) {
                                String key = failedFileList.get(i).getKey();
                                String value = failedFileList.get(i).getValue();
                                failedString = failedString.concat(key).concat(":").concat(value).concat(",");
                            }
                            failedFileList.clear();
                            if (failedString != "") {
                                String msgToSend = "Returning_FailedKeys" + "-" + input_split[1] + "-" + input_split[2] + "-" + failedString;
                                sendUsingClientTask(msgToSend);
//                                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgToSend);
                            } else {
                                Log.e("ServerTask-FailedKeys", "No failed keys to return");
                                String msgToSend = "Returning_FailedKeys" + "-" + input_split[1] + "-" + input_split[2] + "-" + "noFailedKeys";
                                sendUsingClientTask(msgToSend);
//                                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgToSend);
                            }
                        }

                        // Getting back failed keys (Check for correctness)
                        if (input_split[0].equals("Returning_FailedKeys")) {
                            ArrayList<String> localKeys_Failed = new ArrayList<String>();
                            java.io.File[] local_files = getContext().getFilesDir().listFiles();
                            for (java.io.File fileItem : local_files) {
                                localKeys_Failed.add(fileItem.getName());
                            }
                            Log.e("ServerTask-gotFailed", "Getting failedKeyList from: " + input_split[2]);
                            if (!input_split[3].equals("noFailedKeys")) {
                                String[] splitFile = input_split[3].split(",");
                                Log.e("ServerTask-gotFailed", "Number of failed key-value pairs received: " + splitFile.length);
                                for (String eachFile : splitFile) {
                                    if (!eachFile.equals("")) {
                                        String[] fileValue = eachFile.split(":");
                                        String fileKey = fileValue[0];
                                        String fileKeyValue = fileValue[1];
                                        String keyHashed = genHash(fileKey);
                                        if (fileKeyValue.length() != 0) {
                                            if (localKeys_Failed.contains(fileKey)) {
                                                java.io.File toDelete = getContext().getFileStreamPath(input_split[1]);
                                            }
                                            File failedFile = new File(fileKey, fileKeyValue, keyHashed);
                                            fileInsert(failedFile);
                                            fileList.add(failedFile);
                                        }
                                    }
                                }
                            } else {
                                Log.e("ServerTask-gotFailed", "No failed keys received");
                            }
                        }
                    }
                }
            } catch (NoSuchAlgorithmException nse) {
                nse.printStackTrace();
                Log.e("ServerTask", "NoSuchAlgorithmException!");
            } catch (UnknownHostException uhe) {
                uhe.printStackTrace();
                Log.e("ServerTask", "UnknownHostException!");
            } catch (NullPointerException npe) {
                npe.printStackTrace();
                Log.e("ServerTask", "NullPointer!");
            } catch (IOException ioe) {
                ioe.printStackTrace();
                Log.e("ServerTask", "IOException!");
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("ServerTask", "Exception");
            }
            return null;
        }
    }

    // ClientTask starts here
    private class ClientTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... nodeString) {
            String fromClientTask = nodeString[0];
            Log.e("ClientTask-value", "fromClientTask: " + fromClientTask);
            String[] file_str = fromClientTask.split("-");
            Socket clientSocket;
            try {

                // File Insert (Forwarding for replication)
                if (file_str[0].equals("Insert")) {
                    clientSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(file_str[4]));
                    clientSocket.setSoTimeout(100);
                    PrintWriter outStream;
                    outStream = new PrintWriter(clientSocket.getOutputStream(), true);
                    outStream.println(fromClientTask);
                    BufferedReader inStream = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    String receivedString = inStream.readLine();
                    if (receivedString.equals("ACK")) {
                        Log.e("ClientTask-insert", "Received ACK from" + file_str[4]);
                    } else {
                        Log.e("ClientTask-insert", "Check for null value returns!");
                    }
                }

                // Asking ports for keys which it has missed receiving
                if (file_str[0].equals("Failed_Keys")) {
                    Log.e("ClientTask-Failed_Keys", "Asking missed keys to Port: " + file_str[2] + " from Port: " + file_str[1]);
                    clientSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(file_str[2]));
                    clientSocket.setSoTimeout(100);
                    PrintWriter outStream;
                    outStream = new PrintWriter(clientSocket.getOutputStream(), true);
                    outStream.println(fromClientTask);
                }

                // Receiving failed files
                if (file_str[0].equals("Returning_FailedKeys")) {
                    Log.e("ClientTask-Failed_Keys", "Asking missed keys to Port: " + file_str[2] + " from Port: " + file_str[1]);
                    clientSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(file_str[1]));
                    clientSocket.setSoTimeout(100);
                    PrintWriter outStream;
                    outStream = new PrintWriter(clientSocket.getOutputStream(), true);
                    outStream.println(fromClientTask);
                }

            } catch (Exception e) {
                e.printStackTrace();
                Log.e("Exception-ClientTask", "EXCEPTION AT CLIENTTASK!");
                String failedMessage = nodeString[0];
                Log.e("ClientTask-Exception", "Failed Message: " + failedMessage);
                String[] failed_str = failedMessage.split("-");
                //Failed during insert operation
                if (failed_str[0].equals("Insert")) {
                    Log.e("ClientTask-Exception", "Crash occurred during forwarding insert operation to port:" + failed_str[4]);
                    File failedFile = new File(failed_str[1], failed_str[2], failed_str[3]);
                    Log.e("ClientTask-Exception", "Failed File: " + failed_str[1] + " - " + failed_str[2]);
                    failedFileList.add(failedFile);
                }
                Log.e("ClientTask-Exception", "Number of failed files: " + failedFileList.size());
            }
            return null;
        }
    }
}