package edu.buffalo.cse.cse486586.groupmessenger1;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
//    int counter = 0;


//    FROM SIMPLE MESSENGER
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final int SERVER_PORT = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        final EditText et = (EditText) findViewById(R.id.editText1);


//        PA1 CODE FOR SERVER SOCKET
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        try{
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
                String msg = et.getText().toString() + "\n";
                et.setText("");
//                tv.append("\t" + msg + "\n");
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
            }
        });
    }


//        PA1 SERVER TASK
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
        class ServerTask extends AsyncTask<ServerSocket, String, Void> {
            int counter =0;
            @Override
            protected Void doInBackground(ServerSocket... sockets) {
                while(true) {
                    ServerSocket serverSocket = sockets[0];
                    Socket cliSocket = null;
                    try {
                        cliSocket = serverSocket.accept(); //THREAD
                        try {
                            InputStreamReader inputTextStream = new InputStreamReader(cliSocket.getInputStream());
                            BufferedReader in = new BufferedReader(inputTextStream);
                            String inputLine = in.readLine();
                            PrintWriter out = new PrintWriter(cliSocket.getOutputStream(), true);
                            out.println(inputLine);
//                        if (inputLine != null)
                            Log.i("MessageText", inputLine.toString());
                            publishProgress(inputLine);
                        } catch (IOException e) {
                            Log.e(TAG, "Server Socket Error");
                        }
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            /*
            CITATION: Conceptual :- Oracle- https://www.oracle.com/webfolder/technetwork/tutorials/obe/java/SocketProgramming/SocketProgram.html
                      Learning video :- https://www.youtube.com/watch?v=bWKbdPAovFA&list=PLoW9ZoLJX39Xcdaa4Dn5WLREHblolbji4
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
//            return null;
            }


//            PUBLISH MSG TO ALL DEVICES AND POST MSG INTO CONTENT PROVIDER
            protected void onProgressUpdate(String...strings) {
                String strToPublish = strings[0].trim();
                TextView tv = (TextView) findViewById(R.id.textView1);
                Log.e("asda",strToPublish);
                tv.append("\n");
                tv.append(strToPublish);

                // Uri Build as in OnPtest Function
                Uri.Builder uriBuilder = new Uri.Builder();
                uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger1.provider");
                uriBuilder.scheme("content");
                Uri providerUri = uriBuilder.build();

                // Code template taken from PA2 Doc.
                ContentValues keyValueToInsert = new ContentValues();
                keyValueToInsert.put("key", Integer.toString(counter));
                keyValueToInsert.put("value", strToPublish);
                Uri newUri = getContentResolver().insert(providerUri, keyValueToInsert);

                counter = counter +1;
                return;
            }
        }


//        PA1 CLIENT TASK
        /***
         * ClientTask is an AsyncTask that should send a string over the network.
         * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
         * an enter key press event.
         *
         * @author stevko
         *
         */
        class ClientTask extends AsyncTask<String, Void, Void> {

            @Override
            protected Void doInBackground(String... msgs) {
                try {
                    int[] remotePorts = new int[] {11108, 11112, 11116, 11120, 11124};
                    for (int port = 0; port < 5; port++) {
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), remotePorts[port]);
                        String msgToSend = msgs[0];
                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                        out.println(msgToSend);
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        String ensureTCP = in.readLine();
                        socket.close();
                    }
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException");
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
