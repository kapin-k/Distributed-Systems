package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 */
public class GroupMessengerActivity extends Activity {


//    MESSAGE FORMAT
//        ON SEND BUTTON: msg ### msg_type ### sequence_counter ### sent_to_socket ### source_port_number
//        PROPOSAL FROM S to C: msg ### msg_type ### sequence_counter ### current_port_number ### total_counter ### source_port_number
//        AGREEMENT FROM C TO S: msg ### msg_type ### agreed_sequence_counter ### agreed_port_number ### total_counter ### source_port_number

    public static final String TAG = GroupMessengerActivity.class.getName();
    public static final String[] messageType = {"New", "Proposal", "Agreement"};

    public static final int SERVER_PORT = 10000;

    public static PriorityQueue<String> ReadytoDeliver = new PriorityQueue<String>(50, new Comparator<String>() {
        public int compare(String lhs, String rhs) {
            String[] l_split = lhs.split("###");
            String[] r_split = rhs.split("###");
            Float l_id = Float.parseFloat(l_split[2] + "." + l_split[3]);
            Float r_id = Float.parseFloat(r_split[2] + "." + r_split[3]);
            if (l_id < r_id) return -1;
            else if (r_id < l_id) return 1;
            else {
                Log.e(TAG, "Error in Priority Queue");
                return 0;
            }
        }
    });
    public static PriorityQueue<String> ReadytoDeliver_cpy = new PriorityQueue<String>(50, new Comparator<String>() {
        public int compare(String lhs, String rhs) {
            String[] l_split = lhs.split("###");
            String[] r_split = rhs.split("###");
            Float l_id = Float.parseFloat(l_split[2] + "." + l_split[3]);
            Float r_id = Float.parseFloat(r_split[2] + "." + r_split[3]);
            if (l_id < r_id) return -1;
            else if (r_id < l_id) return 1;
            else {
                Log.e(TAG, "Error in Priority Queue");
                return 0;
            }
        }
    });
    public static ArrayList<Float> p_list = new ArrayList<Float>();
    public static ArrayList<Integer> p_count = new ArrayList<Integer>();
    public static ArrayList<String> m_list = new ArrayList<String>();
    public static int[] remotePorts = new int[]{11108, 11112, 11116, 11120, 11124};
    public static int s_counter = 0;
    int key_count = 0;
    public static int currentPort;
    public static int crashed_socket = 0;
    public static boolean crash = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        final EditText et = (EditText) findViewById(R.id.editText1);


        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        currentPort = Integer.parseInt(myPort);
        Log.e("MY PORT NUMBER", Integer.toString(currentPort));
        Log.e("MY PORT NUMBER - myPort", myPort);

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }


        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        final TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
//        tv.setText(et.getText().toString());
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */


//        SEND BUTTON ACTION LISTENER TO RESET EDIT TEXT BOX AND CALL CLIENT
        Button send = (Button) findViewById(R.id.button4);
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = et.getText().toString();
                String new_msg = msg + "###" + messageType[0] + "###" + s_counter + "###" + myPort;
                et.setText("");
                Log.e(TAG, new_msg);
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, new_msg, myPort);
            }
        });
    }


    /***
     * ServerTask is an AsyncTask that should handle incoming messages. It is created by
     * ServerTask.executeOnExecutor() call in SimpleMessengerActivity.
     *
     * Please make sure you understand how AsyncTask works by reading
     * http://developer.android.com/reference/android/os/AsyncTask.html
     *
     * @author stevko
     *
     */
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        int total_counter = 0;
        int max_seq = -99;
        int redirect_port;

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger2.provider");
            uriBuilder.scheme("content");
            Uri providerUri = uriBuilder.build();

            ServerSocket serverSocket = sockets[0];
            String proposal_msg;

            while (true) {
                Socket clientSocket;
                try {
                    clientSocket = serverSocket.accept();
                    InputStreamReader inputTextStream = new InputStreamReader(clientSocket.getInputStream());
                    BufferedReader in = new BufferedReader(inputTextStream);
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                    String inputLine = in.readLine();
                    Log.i("S: Message From Client", inputLine);
                    String[] full_msg = inputLine.split("###");

                    // -----Client Unable to Generate Max Sequence Number-----
                    if (full_msg[0].equals("NoMaxSequenceFound..Error")) {
                        Log.e("MAJOR ERROR", "Check client code");
                    }

                    // -----Client Sends Error Message by Handling Exception-----
                    if (full_msg[0].equals("CrashReport")) {
                        out.println("Crash Received");

                        Iterator iterator  = ReadytoDeliver.iterator();
                        while(iterator.hasNext()){
                            String message = (String)iterator.next();
                            String[] message_split = message.split("###");
                            if(message_split[5].equals(full_msg[1])) {
                                ReadytoDeliver.remove(message);
                            }
                        }

                        if (ReadytoDeliver.peek() != null) {
                            String head = ReadytoDeliver.peek();
                            String[] head_split = head.split("###");

                            while (head_split[1].equals(messageType[2])) {
                                String temp = ReadytoDeliver.poll();
                                String[] temp_split = temp.split("###");
                                Log.e("S: Delivered", temp);
                                ContentValues keyValueToInsert = new ContentValues();
//                            keyValueToInsert.put("key", temp_split[4]);
                                keyValueToInsert.put("key", key_count);
                                keyValueToInsert.put("value", temp_split[0]);
//                            total_counter++;
                                key_count++;
                                Uri newUri = getContentResolver().insert(providerUri, keyValueToInsert);
                                publishProgress(temp_split[0]);

                                if (ReadytoDeliver.peek() != null) {
                                    head_split = ReadytoDeliver.peek().split("###");
                                } else break;
                            }
                        }
                    }

                    // -----New Message-----
                    if (full_msg[1].equals(messageType[0])) {
                        proposal_msg = full_msg[0] + "###" + messageType[1] + "###" + s_counter + "###" + full_msg[3] + "###" + total_counter + "###" + full_msg[4];
                        out.println(proposal_msg);
                        Log.i("S: Server Proposal", proposal_msg);
                        s_counter++;
                        total_counter++;
                        ReadytoDeliver_cpy.add(proposal_msg);
                        ReadytoDeliver.add(proposal_msg);
                        if(crashed_socket!=0){
                            Iterator iterator  = ReadytoDeliver.iterator();
                            while(iterator.hasNext()){
                                String message = (String)iterator.next();
                                String[] message_split = message.split("###");
                                if(message_split[5].equals(Integer.toString(crashed_socket))) {
                                    ReadytoDeliver.remove(message);
                                }
                            }
                        }
                    }

                    // -----Agreement Received-----
                    if (full_msg[1].equals(messageType[2])) {
                        out.println("Acknowledge");
                        if(crashed_socket!=0){
                            Iterator iterator  = ReadytoDeliver.iterator();
                            while(iterator.hasNext()){
                                String message = (String)iterator.next();
                                String[] message_split = message.split("###");
                                if(message_split[5].equals(Integer.toString(crashed_socket))) {
                                    ReadytoDeliver.remove(message);
                                }
                            }
                        }
                        max_seq = Integer.parseInt(full_msg[2]);
                        if (max_seq >= s_counter) {
                            s_counter = max_seq + 1;
                        }
                        String ag_id_str = full_msg[2] + "." + full_msg[3];
                        Float ag_id = Float.parseFloat(ag_id_str);

                        Iterator it = ReadytoDeliver.iterator();
                        Iterator it2 = ReadytoDeliver.iterator();

                        while (it.hasNext()) {
                            String queueValue = (String) it.next();
                            String[] splitValue = queueValue.split("###");
                            String split_id_str = splitValue[2] + "." + splitValue[3];
                            Float split_id = Float.parseFloat(split_id_str);
                            if (splitValue[0].equals(full_msg[0]) && splitValue[1].equals(messageType[1])) {
                                if (ag_id >= split_id) {
//                                    Log.i("S: Removing Value", queueValue);
//                                    Log.i("S: Adding", inputLine);
                                    ReadytoDeliver_cpy.remove(queueValue);
                                    ReadytoDeliver.remove(queueValue);
                                    ReadytoDeliver_cpy.add(inputLine);
                                    ReadytoDeliver.add(inputLine);
                                }
                            }
                        }

                        // -----Displaying queue content-----
                        if (!ReadytoDeliver.isEmpty()) {
                            Log.e("S:Queue_Content", "QUEUE HEAD");
                            int head = 0;
                            while (it2.hasNext()) {
                                String queueValue2 = (String) it2.next();
                                Log.e(TAG, head + " " + queueValue2);
                                head++;
                            }
                        }

                        // -----Delivery and Writing to Content Provider-----
                        if (ReadytoDeliver.peek() != null) {
                            String head = ReadytoDeliver.peek();
                            String[] head_split = head.split("###");

                            while (head_split[1].equals(messageType[2])) {
                                String temp = ReadytoDeliver.poll();
                                String[] temp_split = temp.split("###");
                                Log.e("S: Delivered", temp);
                                ContentValues keyValueToInsert = new ContentValues();
//                            keyValueToInsert.put("key", temp_split[4]);
                                keyValueToInsert.put("key", key_count);
                                keyValueToInsert.put("value", temp_split[0]);
//                            total_counter++;
                                key_count++;
                                Uri newUri = getContentResolver().insert(providerUri, keyValueToInsert);
                                publishProgress(temp_split[0]);

                                if (ReadytoDeliver.peek() != null) {
                                    head_split = ReadytoDeliver.peek().split("###");
                                } else break;
                            }
                            //Check Block
//                            if (crash) {
//                                Log.e(TAG, "Check Block");
//                                if (ReadytoDeliver.peek() != null && !ReadytoDeliver.isEmpty()) {
//                                    String head_check = ReadytoDeliver.peek();
//                                    String[] head_split_check = head_check.split("###");
//                                    if (head_split_check[1].equals(messageType[1])) {
////                                        if (crashed_socket == 11108) {
////                                            redirect_port = 11120;
////                                        }
////                                        if (crashed_socket == 11112) {
////                                            redirect_port = 11124;
////                                        }
////                                        if (crashed_socket == 11120) {
////                                            redirect_port = 11108;
////                                        }
////                                        if (crashed_socket == 11124) {
////                                            redirect_port = 11112;
////                                        }
//                                        if (head_split_check[5].equals(Integer.toString(redirect_port)) || head_split_check[5].equals(Integer.toString(crashed_socket))) {
//                                            ReadytoDeliver.remove(head_check);
//                                        }
//                                    }
//                                }
//                            }
                        }
                    }

                } catch (SocketTimeoutException ste) {
                    ste.printStackTrace();
                    Log.e(TAG, "SerEx: Socket Timeout Exception has been triggered");
                } catch (NullPointerException ne) {
                    ne.printStackTrace();
                    Log.e(TAG, "SerEx: NullPointer Exception Caught in Server");
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                    Log.e(TAG, "SerEx:Server Socket IOError");
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "SerEx:Server Socket Error");
                }
            }
            /*
            CITATION: Conceptual :- Oracle- https://www.oracle.com/webfolder/technetwork/tutorials/obe/java/SocketProgramming/SocketProgram.html
                      Learning video :- https://www.youtube.com/watch?v=bWKbdPAovFA&list=PLoW9ZoLJX39Xcdaa4Dn5WLREHblolbji4
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
            */
        }


        //            PUBLISH MSG TO ALL DEVICES
        protected void onProgressUpdate(String... strings) {
            String strToPublish = strings[0].trim();
            TextView tv = (TextView) findViewById(R.id.textView1);
            tv.append("\n");
            tv.append(strToPublish);
        }
    }


    /***
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
     * an enter key press event.
     *
     * @author stevko
     *
     */
    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            String Agreement = "NoMaxSequenceFound..Error" + "###" + currentPort;
            int port;
            String c_total_counter = "", c_source = "", c_msg = "";
            int m_index = 0;

            // -----Checking Port Entries-----
            for (port = 0; port < 5; port++) {
                Log.e("REMOTE PORT ARRAY", Integer.toString(remotePorts[port]));
            }

            Log.e("CRASH STATUS", crash + "");
            for (port = 0; port < 5; port++) {
                Log.e("FOR COUNT", Integer.toString(remotePorts[port]));
                if (remotePorts[port] != 99) {
                    Socket socket;
                    try {
                        socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), remotePorts[port]);
                        socket.setSoTimeout(3000);
                        PrintWriter outStream = new PrintWriter(socket.getOutputStream(), true);

                        String receivedOnClick = msgs[0];
                        String[] full_msg = receivedOnClick.split("###");

                        String msgToSend = full_msg[0] + "###" + full_msg[1] + "###" + full_msg[2] + "###" + remotePorts[port] + "###" + full_msg[3];

                        Log.e("Client: Sent to Socket:", Integer.toString(remotePorts[port]));
                        Log.i("Client: Message", full_msg[0]);
                        Log.i("Client: Message Type", full_msg[1]);
                        Log.i("Client: Sequence Number", full_msg[2]);
                        Log.i("Client: From Port", full_msg[3]);
                        outStream.println(msgToSend); // Format: msg ### msg_type ### sequence_counter ### sent to socket ### source_port_number

                        String id_str = full_msg[2] + "." + full_msg[3];
                        float id = Float.parseFloat(id_str);
                        if (!m_list.contains(full_msg[0])) {
                            m_list.add(full_msg[0]);
                            p_list.add(m_list.indexOf(full_msg[0]), id);
                            p_count.add(m_list.indexOf(full_msg[0]), -1);
                        }

                        String[] received_proposal;
                        BufferedReader inStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        received_proposal = (inStream.readLine()).split("###");
                        String msg = received_proposal[0];
                        String msg_type = received_proposal[1];
                        String s_id = received_proposal[2];
                        String p_id = received_proposal[3];
                        String proposed_id_str = s_id + "." + p_id;
                        float proposed_id = Float.parseFloat(proposed_id_str);
                        m_index = m_list.indexOf(msg);

                        Log.e("Server: Proposal for", msg);
                        Log.i("Server: Proposal type", msg_type);
                        Log.i("Server: Proposal seq_id", s_id);
                        Log.i("Server: Proposed socket", p_id);

                        if ((m_list.contains(msg)) && (p_count.get(m_index) <= remotePorts.length)) {
                            p_count.add(m_index, (p_count.get(m_index) + 1));
                            if (proposed_id >= p_list.get(m_index)) {
                                p_list.remove(m_index);
                                p_list.add(m_index, proposed_id);
                                c_msg = msg;
                                c_total_counter = received_proposal[4];
                                c_source = received_proposal[5];
                            }
                        }

                        //-----Display Message, Process, Process ID ArrayList-----
//                        if (m_list.size() == p_list.size()) {
//                            for (int i = 0; i < m_list.size(); i++) {
//                                Log.i("M_List", m_list.get(i));
//                            }
//                            for (int i = 0; i < m_list.size(); i++) {
//                                Log.i("P_List", Float.toString(p_list.get(i)));
//                            }
//                            for (int i = 0; i < m_list.size(); i++) {
//                                Log.i("M_List", Integer.toString(p_count.get(i)));
//                            }
//                        } else {
//                            Log.e("Size_MisMatch Size(M_L)", Integer.toString(m_list.size()));
//                            Log.e("Size_MisMatch Size(P_L)", Integer.toString(p_list.size()));
//                        }

                    } catch (Exception e) {
                        if (e.getCause() instanceof SocketException) {
                            Log.e(TAG, "ClientEx: SocketException");
                            crash = true;
                            //Removing Crashed Port
                            int[] remotePorts_cpy = remotePorts.clone();
                            crashed_socket = remotePorts_cpy[port];
                            for (int i = 0; i < remotePorts.length; i++) {
                                if (crashed_socket == remotePorts[i]) {
                                    remotePorts[i] = 99;
                                }
                            }
                            //Displaying remotePorts
                            for (int value : remotePorts) {
                                Log.e("RP_VALUE CHANGE", Integer.toString(value));
                            }
                            //Removing Values from Priority Queue
//                            for (String queueValue : ReadytoDeliver) {
//                                String[] splitValue = queueValue.split("###");
//                                Log.e("Crashed Socket", Integer.toString(crashed_socket));
//                                if ((splitValue[1].equals(messageType[1])) && splitValue[5].equals(Integer.toString(crashed_socket))) {
//                                    ReadytoDeliver.remove(queueValue);
//                                    Log.i("REMOVING CRASHED MSG", queueValue);
//                                }
//                            }
                        } else if (e.getCause() instanceof UnknownHostException) {
                            Log.e(TAG, "ClientEx: UnknownHostException");
                            crash = true;
                            //Removing Crashed Port
                            int[] remotePorts_cpy = remotePorts.clone();
                            crashed_socket = remotePorts_cpy[port];
                            for (int i = 0; i < remotePorts.length; i++) {
                                if (crashed_socket == remotePorts[i]) {
                                    remotePorts[i] = 99;
                                }
                            }
                            //Removing Values from Priority Queue
//                            for (String queueValue : ReadytoDeliver) {
//                                String[] splitValue = queueValue.split("###");
//                                Log.e("Crashed Socket", Integer.toString(crashed_socket));
//                                if ((splitValue[1].equals(messageType[1])) && splitValue[5].equals(Integer.toString(crashed_socket))) {
//                                    ReadytoDeliver.remove(queueValue);
//                                    Log.i("REMOVING CRASHED MSG", queueValue);
//                                }
//                            }

                        } else if (e.getCause() instanceof SocketTimeoutException) {
                            Log.e(TAG, "ClientEx: SocketTimeoutException");
                            crash = true;
                            //Removing Crashed Port
                            int[] remotePorts_cpy = remotePorts.clone();
                            crashed_socket = remotePorts_cpy[port];
                            for (int i = 0; i < remotePorts.length; i++) {
                                if (crashed_socket == remotePorts[i]) {
                                    remotePorts[i] = 99;
                                }
                            }
                            //Displaying remotePorts
                            for (int value : remotePorts) {
                                Log.e("RP_VALUE CHANGE", Integer.toString(value));
                            }
                            //Removing Values from Priority Queue
//                            for (String queueValue : ReadytoDeliver) {
//                                String[] splitValue = queueValue.split("###");
//                                Log.e("Crashed Socket", Integer.toString(crashed_socket));
//                                if ((splitValue[1].equals(messageType[1])) && splitValue[5].equals(Integer.toString(crashed_socket))) {
//                                    ReadytoDeliver.remove(queueValue);
//                                    Log.i("REMOVING CRASHED MSG", queueValue);
//                                }
//                            }
                        } else if (e.getCause() instanceof IOException) {
                            Log.e(TAG, "ClientEx: IOException");
                            crash = true;
                            //Removing Crashed Port
                            int[] remotePorts_cpy = remotePorts.clone();
                            crashed_socket = remotePorts_cpy[port];
                            for (int i = 0; i < remotePorts.length; i++) {
                                if (crashed_socket == remotePorts[i]) {
                                    remotePorts[i] = 99;
                                }
                            }
                            //Displaying remotePorts
                            for (int value : remotePorts) {
                                Log.e("RP_VALUE CHANGE", Integer.toString(value));
                            }
                            //Removing Values from Priority Queue
//                            for (String queueValue : ReadytoDeliver) {
//                                String[] splitValue = queueValue.split("###");
//                                Log.e("Crashed Socket", Integer.toString(crashed_socket));
//                                if ((splitValue[1].equals(messageType[1])) && splitValue[5].equals(Integer.toString(crashed_socket))) {
//                                    ReadytoDeliver.remove(queueValue);
//                                    Log.i("REMOVING CRASHED MSG", queueValue);
//                                }
//                            }
                        } else {
                            Log.e(TAG, "CLIENT EXCEPTION");
                            e.printStackTrace();
                            crash = true;
                            //Removing Crashed Port
                            int[] remotePorts_cpy = remotePorts.clone();
                            crashed_socket = remotePorts_cpy[port];
                            for (int i = 0; i < remotePorts.length; i++) {
                                if (crashed_socket == remotePorts[i]) {
                                    remotePorts[i] = 99;
                                }
                            }
                            Log.e("Working", "Changed ports");
//                            Broadcasting Crashed Socket
                            for (int remotePort : remotePorts) {
                                if (remotePort != 99) {
                                    try {
                                        Socket socket_crash = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), remotePort);
                                        PrintWriter outStream_crash = new PrintWriter(socket_crash.getOutputStream(), true);
                                        String crash_report = "CrashReport" + "###" + crashed_socket + "###" + currentPort;
                                        outStream_crash.println(crash_report);
                                        BufferedReader inStream_crash = new BufferedReader(new InputStreamReader(socket_crash.getInputStream()));
//                                        if (inStream_crash.readLine() != null) {
                                        String crash_ack = inStream_crash.readLine();
//                                            socket_crash.close();
//                                        }
                                    } catch (Exception ce) {
                                        ce.printStackTrace();
                                        Log.e("CrashReport Fail", "Error sending crash report");
                                    }
                                }
                            }
                            //Displaying remotePorts
                            for (int value : remotePorts) {
                                Log.e("RP_VALUE CHANGE", Integer.toString(value));
                            }
                            //Removing Values from Priority Queue
                            Iterator iterator  = ReadytoDeliver.iterator();
                            while(iterator.hasNext()){
                                String message = (String)iterator.next();
                                String[] message_split = message.split("###");
                                if(message_split[5].equals(Integer.toString(crashed_socket))){
                                    ReadytoDeliver.remove(message);
                                }
                            }
                        }
                    }

                }
            }

            if (m_list.contains(c_msg) && p_count.get(m_index) == 4 && crash == false) {
                Log.e("Check1", "I'm here");
                String max_id = String.valueOf(p_list.get(m_index));
                String[] agree_id = max_id.split("\\.");
                Agreement = c_msg + "###" + messageType[2] + "###" + agree_id[0] + "###" + agree_id[1] + "###" + c_total_counter + "###" + c_source;
                m_list.remove(m_index);
                p_list.remove(m_index);
                p_count.remove(m_index);
                for (port = 0; port < remotePorts.length; port++) {
                    try {
                        if (remotePorts[port] != 99) {
                            Socket socket_agree = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), remotePorts[port]);
//                    socket_agree.setSoTimeout(3000);
                            PrintWriter outStream_agree = new PrintWriter(socket_agree.getOutputStream(), true);
                            Log.e("Agreement from C to S", Agreement);
                            outStream_agree.println(Agreement);
                            BufferedReader inStream_agree = new BufferedReader(new InputStreamReader(socket_agree.getInputStream()));
                            String acknowledge = inStream_agree.readLine();
                        }
                    } catch (Exception e) {
                        Log.e("INSIDE AGREEMENT CATCH", e.getMessage());
                    }
                }
            }
            if(m_list.contains(c_msg) && p_count.get(m_index) == 3 && crash == true) {
                String max_id = String.valueOf(p_list.get(m_index));
                String[] agree_id = max_id.split("\\.");
                Agreement = c_msg + "###" + messageType[2] + "###" + agree_id[0] + "###" + agree_id[1] + "###" + c_total_counter + "###" + c_source;
                m_list.remove(m_index);
                p_list.remove(m_index);
                p_count.remove(m_index);
                for (port = 0; port < remotePorts.length; port++) {
                    try {
                        if (remotePorts[port] != 99) {
                            Socket socket_agree = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), remotePorts[port]);
//                    socket_agree.setSoTimeout(3000);
                            PrintWriter outStream_agree = new PrintWriter(socket_agree.getOutputStream(), true);
                            Log.e("Agreement from C to S", Agreement);
                            outStream_agree.println(Agreement);
                            BufferedReader inStream_agree = new BufferedReader(new InputStreamReader(socket_agree.getInputStream()));
                            String acknowledge = inStream_agree.readLine();
                        }
                    } catch (Exception e) {
                        Log.e("INSIDE AGREEMENT CATCH", e.getMessage());
                    }
                }
            }

            return null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}