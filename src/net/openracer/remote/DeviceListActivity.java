//
//   Copyright 2012 Dave Bacon
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
//

package net.openracer.remote;

import java.util.Set;

import android.Manifest.permission;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

public class DeviceListActivity extends Activity {

    private static final String LOGTAG = "openracer-devlist";

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.i(LOGTAG, "on-create");
        
        setContentView(R.layout.activity_device_list);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        
        setResult(Activity.RESULT_CANCELED);
        
        Button scanButton = (Button) findViewById(R.id.button1);
        scanButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {

				if (hasBtPerms()) {
					Log.e(LOGTAG, "pushed");
					v.setEnabled(false);
					discoveredDevices.clear();
					doDiscovery();
				}
			}
		});
        
        
        pairedDevices = new ArrayAdapter<String>(this, R.layout.btdevice_list_item);    
        discoveredDevices = new ArrayAdapter<String>(this, R.layout.btdevice_list_item);    
        
        ListView discoveredListView = (ListView) findViewById(R.id.listView2);
        discoveredListView.setAdapter(discoveredDevices);
        discoveredListView.setOnItemClickListener(deviceClickListener);
        
        ListView pairedListView = (ListView) findViewById(R.id.listView1);
        pairedListView.setAdapter(pairedDevices);
        pairedListView.setOnItemClickListener(deviceClickListener);
        
        registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        registerReceiver(receiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
        
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
        	Log.e(LOGTAG, "No bluetooth adapter");
        }
        
        if (!hasBtPerms()) {
        	Button button = (Button) findViewById(R.id.button1);
			button.setText("No Bluetooth permissions");
			button.setEnabled(false);
        }
        
        if (btAdapter != null) {
        	if (!btAdapter.isEnabled()) {
        		Button button = (Button) findViewById(R.id.button1);
        		button.setText("Bluetooth not enabled");
        		button.setEnabled(false);
        	}
        	Set<BluetoothDevice> sysPairedDevices = btAdapter.getBondedDevices();
        	if (sysPairedDevices.isEmpty()) {
        		pairedDevices.add("No paired devices.");
        	} else {
        		for (BluetoothDevice pDev: sysPairedDevices) {
        			pairedDevices.add(pDev.getName() + "\n" + pDev.getAddress());
        		}
        	}
        } else {
        	Button button = (Button) findViewById(R.id.button1);
			button.setText("No Bluetooth device");
			button.setEnabled(false);
        }
        
    }
    
    private OnItemClickListener deviceClickListener = new OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
			btAdapter.cancelDiscovery();
			
			TextView item = (TextView) v;
			
			String clickedText = item.getText().toString();
			
			int separatorIndex = clickedText.indexOf('\n');
			
			if (separatorIndex >= 0) {
				String name = clickedText.substring(0, separatorIndex);
				String address = clickedText.substring(separatorIndex + 1);
				
				if (address.length() == 17) {
					Log.i(LOGTAG, "clicked: '" + name + "/" + address + "'");
					
					Intent intent = new Intent();
					intent.putExtra("selected_device_address", address);
					intent.putExtra("selected_device_name", name);
					setResult(Activity.RESULT_OK, intent);
					finish();
				}
			}
		}
	};
    
    private ArrayAdapter<String> pairedDevices;
    private ArrayAdapter<String> discoveredDevices;
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_device_list, menu);
        return true;
    }
    
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			
			Log.i(LOGTAG, "rx'd broadcast - " + action);
			
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
					discoveredDevices.add(device.getName() + "\n" + device.getAddress());
				}
			} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
				setProgressBarIndeterminateVisibility(false);
				setTitle("Select device");
				if (discoveredDevices.isEmpty()) {
					discoveredDevices.add("No discovered devices.");
				}
				
				Button discoverButton = (Button) findViewById(R.id.button1);
				discoverButton.setEnabled(true);
			}
		}
    };

    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	
    	if (btAdapter != null) {
    		btAdapter.cancelDiscovery();
    	}
    	
    	this.unregisterReceiver(receiver);
    };

    private BluetoothAdapter btAdapter;
    
    private boolean hasBtPerms() {
    	if (PackageManager.PERMISSION_GRANTED != checkCallingOrSelfPermission(permission.BLUETOOTH)) {
    		Log.e(LOGTAG, "No bluetooth permission");
    		return false;
    	}
    	return true;
    }
    
    private void doDiscovery() {

    	setProgressBarIndeterminate(true);
    	setTitle("Scanning ...");
    	Log.e(LOGTAG, "do-discovery");
    	
    	
    	if (!btAdapter.isEnabled()) {
    		Log.e(LOGTAG, "Bluetooth is not enabled");
    		
//    		Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//    		startActivityForResult(intent, 1);
    		return;
    	}
    	
    	if (btAdapter.isDiscovering()) {
    		if (!btAdapter.cancelDiscovery()) {
    			Log.e(LOGTAG, "Could not cancel in-progress discovery");
    			return;
    		}
    	}
    	
    	Log.e(LOGTAG, "start-discovery");
    	btAdapter.startDiscovery();
    }
}
