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

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

/**
 * Manages a single {@link BluetoothSocket} by creating and connecting it, reading the input stream,
 * providing methods to send data, a method to stop the connection, and notification of connection end.
 * 
 * <p>
 * TODO: This object should be saved over destroy/create, and to do so, it should not
 * maintain a reference to mainActivity.
 * </p>
 * 
 * @author dave
 *
 */
public class BluetoothConnectionManager extends Thread {

	private static final String LOGTAG = "openracer-connectionmanager";

	public static interface Listener {

		void onMessage(String string);

		void onConnected(BluetoothConnectionManager bluetoothConnectionManager);

		void onDisconnected(String exitReason);
	}
	
	private final UUID uuid;
	private final Listener listener; // TODO: caller should be able to unregister for handling their own destroy/create
	private final BluetoothDevice device;
	private final Object socketLock = new Object();
	private BluetoothSocket socket = null;
	
	public BluetoothConnectionManager(BluetoothDevice device, UUID uuid, Listener listener) {
		this.device = device;
		this.uuid = uuid;
		this.listener = listener;
	}
	
	public void write(int oneByte) throws IOException {
		synchronized (socketLock) {
			socket.getOutputStream().write(oneByte);
		}
	}
	
	public void write(String data) throws IOException {
		synchronized(socketLock) {
			socket.getOutputStream().write(data.getBytes());
		}
	}
	
	public void disconnect() {
		synchronized (socketLock) {
			try {
				socket.close();
			} catch (IOException e) {
				Log.w(LOGTAG, "Exception during socket close during disconnect: " + e.toString(), e);
			}
		}
	}

	private void onMessage(final String string) {
		Log.i(LOGTAG, "rx'd message: '" + string.replace("\n", "\\n").replace("\r", "\\r") + "'");
		listener.onMessage(string);
	}
	
	@Override
	public void run() {
		
		String exitReason = "Could not initialize";

		try {
			
			try {
				Log.i(LOGTAG, "creating RFCOMM socket...");
				BluetoothSocket socket = device.createRfcommSocketToServiceRecord(uuid);
				
				Log.i(LOGTAG, "connecting...");
				socket.connect();
				
				synchronized (socketLock) {
					this.socket = socket;
				}
				
				Log.i(LOGTAG, "got bt socket: " + socket.toString());
				
				listener.onConnected(this);
				
				InputStream is = socket.getInputStream();
				
				StringBuilder b = new StringBuilder();
				byte[] buf = new byte[1024];
				while (true) {
					int nread = is.read(buf, 0, 1024);
					b.append(new String(buf, 0, nread));
					
					String p = b.toString();
					int sepIndex;
					while ((sepIndex = p.indexOf('\n')) >= 0) {
						onMessage(p.substring(0, sepIndex));
						p = p.substring(sepIndex + 1);
					}
					b = new StringBuilder(p);
				}
			} catch (IOException e) {
				// this may be due to normally requested disconnect, as it reaches us as an IOException...
				exitReason = "bluetooth connection ended: " + e.toString();
				Log.w(LOGTAG, "bluetooth connection ended: " + e.toString());
				
				synchronized (socketLock) {
					if (socket != null && socket.isConnected()) {
						try {
							socket.close();
						} catch (IOException e1) {
							Log.w(LOGTAG, "Exception while closing bt socket after failed connect: " + e1.getMessage(), e);
						}
					}
				}
			}
			
		} finally {
			listener.onDisconnected(exitReason);
		}
	}
}