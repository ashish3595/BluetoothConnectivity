package com.example.ashish.bluetoothapp;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;


public class BluetoothActivity extends ActionBarActivity {


    private final static UUID uuid = UUID.fromString("fc5ffc49-00e3-4c8b-9cf1-6b72aad1001a");

    // Create a BroadcastReceiver for ACTION_FOUND
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // Whenever a remote Bluetooth device is found
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Add the name and address to an array adapter to show in a ListView
                adapter.add(bluetoothDevice.getName() + "\n"
                        + bluetoothDevice.getAddress());
            }
        }
    };


    private BluetoothAdapter bluetoothAdapter;

    private final Handler mHandler;
    private ToggleButton toggleButton;
    private ListView listview;
    private ArrayAdapter adapter;
    private ConnectingThread connectingThread;
    private ManageThread manageThread;
    private static final int ENABLE_BT_REQUEST_CODE = 1;
    private static final int DISCOVERABLE_BT_REQUEST_CODE = 2;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    private static final int DISCOVERABLE_DURATION = 300;

    public BluetoothActivity(Context context, Handler mHandler) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mHandler = mHandler;
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        toggleButton = (ToggleButton) findViewById(R.id.toggleButton);

        listview = (ListView) findViewById(R.id.listView);

        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                // ListView Clicked item value
                String  itemValue = (String) listview.getItemAtPosition(position);

                String MAC = itemValue.substring(itemValue.length() - 17);

                BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(MAC);

                // Initiate a connection request in a separate thread
                ConnectingThread t = new ConnectingThread(bluetoothDevice);
                t.start();
            }
        });

        adapter = new ArrayAdapter
                (this,android.R.layout.simple_list_item_1);
        listview.setAdapter(adapter);

        //bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void onToggleClicked(View view) {

        adapter.clear();

        ToggleButton toggleButton = (ToggleButton) view;

        if (bluetoothAdapter == null) {
            // Device does not support Bluetooth
            Toast.makeText(getApplicationContext(), "Oop! Your device does not support Bluetooth",
                    Toast.LENGTH_SHORT).show();
            toggleButton.setChecked(false);
        } else {

            if (toggleButton.isChecked()){ // to turn on bluetooth
                if (!bluetoothAdapter.isEnabled()) {
                    // A dialog will appear requesting user permission to enable Bluetooth
                    Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBluetoothIntent, ENABLE_BT_REQUEST_CODE);
                } else {
                    Toast.makeText(getApplicationContext(), "Your device has already been enabled." +
                                    "\n" + "Scanning for remote Bluetooth devices...",
                            Toast.LENGTH_SHORT).show();
                    // To discover remote Bluetooth devices
                    discoverDevices();
                    // Make local device discoverable by other devices
                    makeDiscoverable();
                }
            } else { // Turn off bluetooth

                bluetoothAdapter.disable();
                adapter.clear();
                Toast.makeText(getApplicationContext(), "Your device is now disabled.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == ENABLE_BT_REQUEST_CODE) {

            // Bluetooth successfully enabled!
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(getApplicationContext(), "Ha! Bluetooth is now enabled." +
                                "\n" + "Scanning for remote Bluetooth devices...",
                        Toast.LENGTH_SHORT).show();

                // Make local device discoverable by other devices
                makeDiscoverable();

                // To discover remote Bluetooth devices
                discoverDevices();

                // Start a thread to create a  server socket to listen
                // for connection request
                ListeningThread t = new ListeningThread();
                t.start();

            } else { // RESULT_CANCELED as user refused or failed to enable Bluetooth
                Toast.makeText(getApplicationContext(), "Bluetooth is not enabled.",
                        Toast.LENGTH_SHORT).show();

                // Turn off togglebutton
                toggleButton.setChecked(false);
            }
        } else if (requestCode == DISCOVERABLE_BT_REQUEST_CODE){

            if (resultCode == DISCOVERABLE_DURATION){
                Toast.makeText(getApplicationContext(), "Your device is now discoverable by other devices for " +
                                DISCOVERABLE_DURATION + " seconds",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "Fail to enable discoverability on your device.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    protected void discoverDevices(){
        // To scan for remote Bluetooth devices
        if (bluetoothAdapter.startDiscovery()) {
            Toast.makeText(getApplicationContext(), "Discovering other bluetooth devices...",
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getApplicationContext(), "Discovery failed to start.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    protected void makeDiscoverable(){
        // Make local device discoverable
        Intent discoverableIntent = new
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION);
        startActivityForResult(discoverableIntent, DISCOVERABLE_BT_REQUEST_CODE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register the BroadcastReceiver for ACTION_FOUND
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(broadcastReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        this.unregisterReceiver(broadcastReceiver);
    }


    public void back(View view)
    {
        finish();
    }

    public void write(byte[] out) {
        // Create temporary object
        ManageThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            //if (mState != STATE_CONNECTED) return;
            r = manageThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {


        // Start the thread to manage the connection and perform transmissions
        manageThread = new ManageThread(socket);
        manageThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString("getData", device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }




    private class ListeningThread extends Thread {
        private final BluetoothServerSocket bluetoothServerSocket;


        public ListeningThread() {
            BluetoothServerSocket temp = null;
            try {
                temp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(getString(R.string.app_name), uuid);

            } catch (IOException e) {
                e.printStackTrace();
            }
            bluetoothServerSocket = temp;
        }

        public void run() {
            BluetoothSocket bluetoothSocket;
            // This will block while listening until a BluetoothSocket is returned
            // or an exception occurs
            while (true) {
                try {
                    bluetoothSocket = bluetoothServerSocket.accept();
                } catch (IOException e) {
                    break;
                }
                // If a connection is accepted
                if (bluetoothSocket != null) {

                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(getApplicationContext(), "A connection has been accepted.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });

                    // Code to manage the connection in a separate thread
                   /*
                       manageBluetoothConnection(bluetoothSocket);
                   */

                    connected(bluetoothSocket, bluetoothSocket.getRemoteDevice());


                    try {
                        bluetoothServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }

        // Cancel the listening socket and terminate the thread
        public void cancel() {
            try {
                bluetoothServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private class ConnectingThread extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final BluetoothDevice bluetoothDevice;

        public ConnectingThread(BluetoothDevice device) {

            BluetoothSocket temp = null;
            bluetoothDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                temp = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) {
                e.printStackTrace();
            }
            bluetoothSocket = temp;
        }

        public void run() {
            // Cancel discovery as it will slow down the connection
            bluetoothAdapter.cancelDiscovery();

            try {
                // This will block until it succeeds in connecting to the device
                // through the bluetoothSocket or throws an exception
                bluetoothSocket.connect();
            } catch (IOException connectException) {
                connectException.printStackTrace();
                try {
                    bluetoothSocket.close();
                } catch (IOException closeException) {
                    closeException.printStackTrace();
                }
            }


            // Code to manage the connection in a separate thread
            /*
               manageBluetoothConnection(bluetoothSocket);
            */

            connected(bluetoothSocket, bluetoothDevice);

        }

        // Cancel an open connection and terminate the thread
        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private class ManageThread extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ManageThread(BluetoothSocket socket) {
            //Log.d(TAG, "create ConnectedThread: " + socketType);
            bluetoothSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                //Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            //Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);

                    // Send the obtained bytes to the UI Activity
                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    //Log.e(TAG, "disconnected", e);
                    //connectionLost();
                    // Start the service over to restart listening mode
                    //BluetoothActivity.this.start();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget();
            } catch (IOException e) {
                //Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                //Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
