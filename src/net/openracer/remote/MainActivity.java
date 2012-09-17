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

import java.util.UUID;

import net.openracer.remote.JoypadView.Listener;
import android.Manifest.permission;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {

	private static final String LOGTAG = "openracer-main";

	public static class PrefKey {
		public static final String LAST_DEVICE_ADDR = "last-device";
		public static final String LAST_DEVICE_NAME = "last-device-name";
	}
	
	public static class PrefDefault {
		public static final String LAST_DEVICE_ADDR = null;
		public static final String LAST_DEVICE_NAME = "No Device";
	}
	
	private String selectedAddr = null;
	private String selectedName = null;
	private final BroadcastReceiver receiver = new BluetoothUuidReceiver();
	private BluetoothConnectionManager connecting = null;
	private BluetoothConnectionManager btConn = null;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_main);
		
		SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
		selectedAddr = prefs.getString(PrefKey.LAST_DEVICE_ADDR, PrefDefault.LAST_DEVICE_ADDR);
		selectedName = prefs.getString(PrefKey.LAST_DEVICE_NAME, PrefDefault.LAST_DEVICE_NAME);
		
		Vibrator vibratorService = (Vibrator) getSystemService(VIBRATOR_SERVICE);
		if (vibratorService.hasVibrator() && (PackageManager.PERMISSION_GRANTED == checkCallingOrSelfPermission(permission.VIBRATE))) {
			vibratorService.vibrate(20);
		}
		
		setUiConnected(false);
		
		registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_UUID));
		
		getConnectionButton().setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onConnectionButtonClick();
			}
		});

		getJoypadLeft().setListener(new Listener() {
			private int oldValue = 0;
			
			@Override
			public void onInactive(float x, float y, float pressure) {
				if (isConnected()) {
					int newValue = 0;
					onSeek2Change_Dagu(newValue);
					oldValue = newValue;
				}
			}
			
			@Override
			public void onActive(float x, float y, float pressure) {
				if (isConnected()) {
					int newValue = (int)((1-y)*511) - 256;
					if (oldValue != newValue) {
						onSeek2Change_Dagu(newValue);
					}
					oldValue = newValue;
				}
			}
		});
		
		getJoypadRight().setListener(new Listener() {
			
			private int oldValue = 0;
			
			@Override
			public void onInactive(float x, float y, float pressure) {
				if (isConnected()) {
					int newValue = 0;
					onSeek1Change_Dagu(newValue);
					oldValue = newValue;
				}
			}
			
			@Override
			public void onActive(float x, float y, float pressure) {
				if (isConnected()) {
					int newValue = (int)(x*511) - 256;
					if (oldValue != newValue) {
						onSeek1Change_Dagu(newValue);
					}
					oldValue = newValue;
				}
			}
		});
		
		getStopButton().setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (isConnected()) {
					onSeek1Change_Dagu(0);
					onSeek2Change_Dagu(0);
				}
			}
		});

//		getSeekBar().setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
//			@Override public void onStopTrackingTouch(SeekBar seekBar) { }
//			@Override public void onStartTrackingTouch(SeekBar seekBar) { }
//			
//			@Override
//			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//				if (isConnected()) {
//					onSeek1Change(progress - 255);
//				}
//			}
//		});
//		
//		getSeekBar2().setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
//			@Override public void onStopTrackingTouch(SeekBar seekBar) { }
//			@Override public void onStartTrackingTouch(SeekBar seekBar) { }
//			
//			@Override
//			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//				if (isConnected()) {
//					onSeek2Change(progress - 255);
//				}
//			}
//		});
//		
//		// this is a composite one - just automates manipulation of 2 other controls
//		getStopButton().setOnClickListener(new OnClickListener() {
//			@Override
//			public void onClick(View v) {
//				getSeekBar().setProgress(0+255);
//				getSeekBar2().setProgress(0+255);
//			}
//		});
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(android.view.MenuItem item) {
		if (R.id.menu_settings == item.getItemId()) {
			Intent intent = new Intent(this, DeviceListActivity.class);
			startActivityForResult(intent, 42);
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(receiver);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		if (requestCode == 42) {
			if (resultCode == RESULT_OK) {
				
				selectedAddr = data.getExtras().getString("selected_device_address");
				selectedName = data.getExtras().getString("selected_device_name");
				
				getPreferences(Context.MODE_PRIVATE).edit()
					.putString(PrefKey.LAST_DEVICE_ADDR, selectedAddr)
					.putString(PrefKey.LAST_DEVICE_NAME, selectedName)
					.commit();
				
				Log.i(LOGTAG, "device-list activity returned - " + selectedAddr);
			} else if (resultCode == RESULT_CANCELED) {
				Log.i(LOGTAG, "device-list activity canceled");
			}
			setUiConnected(isConnected());
		}
	}
	
	protected void onConnectionButtonClick() {
		if (isConnected()) {
			disconnect();
		} else {
			connect();
			
			if (isConnected()) {
				writeInitialStateCommands();
			}
		}
		setUiConnected(isConnected());
	}

	
	// --------------------------------------------------------------------
	// robot-style control support (normal differential or "tank" style)
	
	protected void onSeek2Change_robot(int value) {
		try {
			btConn.write("h" + value + "\n");
		} catch (Exception e) {
			Log.w(LOGTAG, "h command failed: " + e.toString());
		}
	}

	protected void onSeek1Change_robot(int value) {
		try {
			btConn.write("g" + value + "\n");
		} catch (Exception e) {
			Log.w(LOGTAG, "g command failed: " + e.toString());
		}
	}

	// End robot-style control support
	// --------------------------------------------------------------------

	
	// --------------------------------------------------------------------
	// Dagu-style control support
	
	public static enum Steer {
		Left,
		Straight,
		Right,
	}
	
	private Steer steer = Steer.Straight;
	private int seek2value = 0;
	
	// for Dagu, this is drive motor
	protected void onSeek2Change_Dagu(int value) {
		seek2value = value;
		sendDaguCommandByte(recalcDaguCommandByte());
	}
	
	protected void onSeek1Change_Dagu(int value) {
		if (value < -100) steer = Steer.Left;
		else if (value > 100) steer = Steer.Right;
		else steer = Steer.Straight;
		
		sendDaguCommandByte(recalcDaguCommandByte());
	}
	
	private int recalcDaguCommandByte() {
		boolean forward = true;
		int daguSpeed = seek2value;
		if (daguSpeed < 0) {
			daguSpeed = -daguSpeed;
			forward = false;
		}
		
		if (daguSpeed > 255) daguSpeed = 255;
		daguSpeed = daguSpeed >> 4;
		
		int daguSteer = 0;
		if (daguSpeed > 0) {
			if (forward) {
				switch (steer) {
				case Straight: daguSteer = 1; break;
				case Left: daguSteer = 5; break;
				case Right: daguSteer = 6; break;
				}
			} else {
				switch (steer) {
				case Straight: daguSteer = 2; break;
				case Left: daguSteer = 7; break;
				case Right: daguSteer = 8; break;
				}
			}
		} else {
			switch (steer) {
			case Straight: daguSteer = 0; break;
			case Left: daguSteer = 3; break;
			case Right: daguSteer = 4; break;
			}
		}
		
		return (daguSteer << 4) | (daguSpeed & 0x0f);
	}

	private void sendDaguCommandByte(int controlByte) {
		
		//int h = (controlByte & 0xf0) >> 4;
		//int l = (controlByte & 0x0f) >> 0;
		//Log.i(LOGTAG, "Dagu control byte: high = " + h + " low = " + l);
		
		try {
			btConn.write(controlByte);
		} catch (Exception e) {
			Log.w(LOGTAG, "dagu command-byte failed: " + e.toString());
		}
	}

	// end Dagu-style control
	// --------------------------------------------------------------------
	
	
	protected void onBluetoothConnectionConnected(BluetoothConnectionManager btConn) {
		this.connecting = null;
		this.btConn = btConn;
		setUiConnected(isConnected());
		displayToast("Connected");
	}

	protected void onBluetoothConnectionDisconnected(String exitReason) {
		btConn = null;
		setUiConnected(isConnected());
		displayToast(exitReason);
	}
	
	protected void onBluetoothConnectionMessage(String data) {
		displayToast("RX'd: " + data);
	}

	private void displayToast(String message) {
		Toast toast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.TOP, 0, 10);
		toast.show();
	}

	private void setUiConnected(boolean connected) {
//		getSeekBar().setEnabled(connected);
//		getSeekBar2().setEnabled(connected);
		getJoypadRight().setEnabled(connected);
		getJoypadLeft().setEnabled(connected);
		getStopButton().setEnabled(connected);
		
		getConnectionButton().setEnabled(selectedAddr != null && connecting == null);
		getConnectionButton().setText(connected ? "Disconnect" : ("Connect to " + selectedName));
		
		if (!connected) {
//			getSeekBar().setProgress(0+255);
//			getSeekBar2().setProgress(0+255);
		}
	}
	
	private void writeInitialStateCommands() {
		try {
			btConn.write("g\np\ni\nd\np200\n");
		} catch (Exception e) {
			Log.w(LOGTAG, "Could not write initial commands: " + e.toString());
		}
	}
	
	Button getStopButton() {
		return (Button) findViewById(R.id.stopButton);
	}
	
	Button getConnectionButton() {
		return (Button) findViewById(R.id.disconnectButton);
	}
	
//	SeekBar getSeekBar() {
//		return (SeekBar) findViewById(R.id.seekBar1);
//	}
//	
//	SeekBar getSeekBar2() {
//		return (SeekBar) findViewById(R.id.seekBar2);
//	}
	
	JoypadView getJoypadLeft() {
		return (JoypadView) findViewById(R.id.joypadLeft);
	}

	JoypadView getJoypadRight() {
		return (JoypadView) findViewById(R.id.joypadRight);
	}

	private void connect() {
		if (selectedAddr == null) {
			Log.w(LOGTAG, "attempted connect() with no selected address");
			return;
		}
		
		BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
		if (bt == null) {
			Log.w(LOGTAG, "attempted connect() with no Bluetooth hardware");
			return;
		}
		
		BluetoothDevice btd = bt.getRemoteDevice(selectedAddr);
		
		ParcelUuid[] parcelUuids = btd.getUuids();
		if (parcelUuids == null || parcelUuids.length <= 0) {
			Log.i(LOGTAG, "No UUIDs returned from device, fetching with SDP");
			if (!btd.fetchUuidsWithSdp()) {
				Log.w(LOGTAG, "Could not query for UUIDs");
			}
		} else {
			for (ParcelUuid uuid: parcelUuids) {
				Log.i(LOGTAG, "cached-uuid: " + uuid.toString());
			}
			Log.i(LOGTAG, "initiating connection");
			ParcelUuid parcelUuid = (ParcelUuid) parcelUuids[0];
			connect(btd, parcelUuid.getUuid());
		}
	}
	
	private void connect(BluetoothDevice device, UUID uuid) {
		connecting = new BluetoothConnectionManager(device, uuid, new BluetoothConnectionEventRouter(this));
		connecting.start();
	}
	
	private void disconnect() {
		try {
			
			// this should be done by the remote device! not us!
			btConn.write("g0\n" + "p0\n" + "i0\n" + "d0\n" + "p1000\n");
			
			btConn.disconnect();
		} catch (Exception e) {
			Log.w(LOGTAG, "error closing socket during disconnect: " + e.toString());
		}
	}
	
	private boolean isConnected() {
		return btConn != null;
	}

	public static class BluetoothUuidReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (BluetoothDevice.ACTION_UUID.equals(intent.getAction())) {
				Log.i(LOGTAG, "Got UUIDs from SDP: ");
				BluetoothDevice device = (BluetoothDevice) intent.getExtras().get(BluetoothDevice.EXTRA_DEVICE);
				Parcelable[] parcelUuids = (Parcelable[]) intent.getExtras().get(BluetoothDevice.EXTRA_UUID);
				for (Parcelable parcelUuid: parcelUuids) {
					Log.i(LOGTAG, "  " + device + "/" + parcelUuid.toString());
				}
			}
		}
	}

	public static class BluetoothConnectionEventRouter implements BluetoothConnectionManager.Listener {
		private final MainActivity ui;

		public BluetoothConnectionEventRouter(MainActivity ui) {
			this.ui = ui;
		}
		
		@Override
		public void onConnected(final BluetoothConnectionManager bluetoothConnectionManager) {
			ui.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					ui.onBluetoothConnectionConnected(bluetoothConnectionManager);
				}
			});
		}

		@Override
		public void onDisconnected(final String exitReason) {
			ui.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					ui.onBluetoothConnectionDisconnected(exitReason);
				}
			});
		}

		@Override
		public void onMessage(final String string) {
			ui.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					ui.onBluetoothConnectionMessage(string);
				}
			});
		}
	}

}
