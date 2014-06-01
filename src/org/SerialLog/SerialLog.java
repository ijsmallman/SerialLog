/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * Modified by Joe Smallman April 2012 to make appropriate for serial
 * communication with Arduino device
 *  
 */

package org.SerialLog;

import org.SerialLog.R;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

public class SerialLog extends Activity {
    
	// Debugging
    private static final String TAG = "SerialLogActivity";
    private static final boolean D = true;
    
    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 3;
	
    private BTcom btCom;
    private BluetoothAdapter mBluetoothAdapter;
    
    private ArrayAdapter<String> mConversationArrayAdapter;
 	
    // Layout Views
    private ListView mConversationView;
    private EditText mOutEditText;
    private ActionBar mActionBar;
    
    // Name of the connected device
    private String mConnectedDeviceName = null;
    
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.e(TAG, "+++ ON CREATE +++");
        
        setContentView(R.layout.main);
        findViewById(R.id.button_send).setOnClickListener(clickListener);
        mOutEditText=(EditText)findViewById(R.id.edittext_out);
        mOutEditText.setOnClickListener(clickListener);
        mOutEditText.setOnEditorActionListener(new DoneOnEditorActionListener());
        mActionBar = getActionBar();

        // get Bluetooth Adapter
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if(mBluetoothAdapter == null) {
			// Device does not support Bluetooth
			Toast.makeText(this, R.string.bt_not_available, Toast.LENGTH_LONG).show();
			finish();
			return;
		}
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity, menu);
        return true;
    }
    
    @Override
    public void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");

        // If BT is not on, request that it be enabled.
        // setupCom() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, setup the chat session
        } else {
            if (btCom == null) setupCom();
        }
    }
    
    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "+ ON RESUME +");

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (btCom != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (btCom.getState() == BTcom.STATE_NONE) {
              // Start the Bluetooth com services
              btCom.start();
            }
        }
    }
    
    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if (btCom != null) btCom.stop();
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }
    
	public void setupCom(){
		
		// Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        mConversationView = (ListView) findViewById(R.id.list_log);
        mConversationView.setAdapter(mConversationArrayAdapter);
        
		btCom = new BTcom(this, mHandler);
		return;
	}
	
	private final void setStatus(int resId) {
        mActionBar.setSubtitle(resId);
    }
	
 // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case BTcom.MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BTcom.STATE_CONNECTED:
                    setStatus(R.string.connected);
                    mConversationArrayAdapter.clear();
                    break;
                case BTcom.STATE_CONNECTING:
                    setStatus(R.string.connecting);
                    break;
                case BTcom.STATE_LISTEN:
                case BTcom.STATE_NONE:
                    setStatus(R.string.not_connected);
                    break;
                }
                break;
            case BTcom.MESSAGE_WRITE:
            	String writeBuf = (String) msg.obj;
				mConversationArrayAdapter.add("Out:  " + writeBuf.replace("\n", ""));
                break;
            case BTcom.MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                mConversationArrayAdapter.add(mConnectedDeviceName+":  " + readMessage);
                break;
            case BTcom.MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(BTcom.DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case BTcom.MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(BTcom.TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            }
            return;
        }
    };
    
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch(requestCode){
		case REQUEST_ENABLE_BT:
			if (resultCode == RESULT_OK) {
				Toast.makeText(this, R.string.bt_enabled, Toast.LENGTH_SHORT).show();
				setupCom();
				break;
			}
			else {
				// User did not enable Bluetooth or an error occurred
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
                break;
			}
		case REQUEST_CONNECT_DEVICE:
			if (resultCode == RESULT_OK){
				connectDevice(data, true);
			}
			break;
		}
		return;
	}
	
	private void connectDevice(Intent data, boolean secure) {
		// MAC address
		String address = data.getExtras()
			.getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
		// BluetoothDevice object
		BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		// Attempt to connect to the device
        btCom.connect(device, secure);
        return;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent serverIntent = null;
	    // Handle item selection
	    switch (item.getItemId()) {
	    	case R.id.scan_connect:
	    		if (btCom != null){
	    			btCom.stop();
	    		}
	    		//Launch the DeviceListActivity to see the paired devices,
	    		//scan for devices and connect to chosen device
	    		serverIntent = new Intent(this, DeviceListActivity.class);
	    		startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
	    		return true;
	    	case R.id.disconnect:
	    		if (btCom != null){
	    			btCom.stop();
	    		}
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}
	
	private void sendMessage(){
		// Check that we're actually connected before trying anything
        if (btCom.getState() != BTcom.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }
		EditText et = (EditText)this.findViewById(R.id.edittext_out);
		String msg = et.getText().toString();
		msg += "\n";
		
		btCom.write(msg);
		return;
	}
	
	private View.OnClickListener clickListener = new View.OnClickListener() {
	
		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.button_send:
				sendMessage();
				break;
			case R.id.edittext_out:
				mOutEditText.setText("");
				break;
			}
			return;
		}
	};
	
	class DoneOnEditorActionListener implements OnEditorActionListener {
	    @Override
	    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
	        if (actionId == EditorInfo.IME_ACTION_DONE) {
	            InputMethodManager imm = (InputMethodManager)v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
	            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
	            sendMessage();
	            return true;	
	        }
	        return false;
	    }
	}
	
}