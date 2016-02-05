package com.johnny.bletemperaturereceiver;

import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import java.text.DateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BLETemperatureService extends Service {
	private final static String TAG = BLETemperatureService.class.getSimpleName();

	public static final int NOTIFICATION_ID = 10;

	private BluetoothManager mBluetoothManager;
	private BluetoothAdapter mBluetoothAdapter;

	private String mBluetoothDeviceAddress;
	private BluetoothGatt mBluetoothGatt;

	public static class ConnectState {
		public static final int Disconnected = 0;
		public static final int Connecting = 1;
		public static final int Connected = 2;
		public static final int ConnectedRunning = 3;
	}

	private int mConnectionState = ConnectState.Disconnected;

	public final static String ACTION_CLOSE = "blereceiver.ACTION_CLOSE";

	public final static String ACTION_GATT_CONNECTED = "blereceiver.ACTION_GATT_CONNECTED";
	public final static String ACTION_GATT_DISCONNECTED = "blereceiver.ACTION_GATT_DISCONNECTED";
	public final static String ACTION_GATT_SERVICES_DISCOVERED = "blereceiver.ACTION_GATT_SERVICES_DISCOVERED";

	public final static String ACTION_TEMPERATURERE_UPDATE = "blereceiver.ACTION_TEMPERATURERE_UPDATE";

	public final static String ACTION_MESSAGE_SERVICE_ONLINE = "blereceiver.ACTION_MESSAGE_SERVICE_ONLINE";

	public final static String EXTRA_TEMPERATURERE_DATA = "blereceiver.EXTRA_TEMPERATURERE_DATA";

	public final static String NOT_SUPPORT_TEMPERATURE_SERVICE = "blereceiver.NOT_SUPPORT_TEMPERATURE_SERVICE";

	public static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

	public static final UUID SERVICE_TEMPERATURE_UUID = UUID.fromString("00001809-0000-1000-8000-00805F9B34FB");
	public static final UUID CHAR_TEMPERATURE_UUID = UUID.fromString("00002A1C-0000-1000-8000-00805F9B34FB");

	// http://stackoverflow.com/questions/17910322/android-ble-api-gatt-notification-not-received

	protected String mConnectedDeviceName = null;

	public int getConnectionState() {
		return mConnectionState;
	}

	public String getBluetoothDeviceAddress() {
		return mBluetoothDeviceAddress;
	}

	// Implements callback methods for GATT events that the app cares about.  For example,
	// connection change and services discovered.
	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			String intentAction;

			if (newState == BluetoothProfile.STATE_CONNECTED) {
				intentAction = ACTION_GATT_CONNECTED;
				mConnectionState = ConnectState.Connected;

				BluetoothDevice mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mBluetoothDeviceAddress);
				mConnectedDeviceName = mDevice.getName();

				broadcastUpdate(intentAction);

				// TODO: Bug fix
				if (mBluetoothGatt == null) {
					mBluetoothGatt = gatt;
				}

				Log.i(TAG, "Connected to GATT server.");
				// Attempts to discover services after successful connection.
				Log.i(TAG, "Attempting to start service discovery:" +
						mBluetoothGatt.discoverServices());

				Log.v(TAG, "ACTION_START_SERVER");

				Intent selfIntent = new Intent(BLETemperatureService.this, BLETemperatureService.class);
				startService(selfIntent);

				startNotificationForeground(0);

			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				intentAction = ACTION_GATT_DISCONNECTED;
				mConnectionState = ConnectState.Disconnected;
				Log.i(TAG, "Disconnected from GATT server.");
				broadcastUpdate(intentAction);

				close();
				stopNotificationForeground();
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Log.w(TAG, "mBluetoothGatt = " + gatt);

				broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);

				enableTXNotification();
			} else {
				Log.w(TAG, "onServicesDiscovered received: " + status);
			}
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,
		                                 BluetoothGattCharacteristic characteristic,
		                                 int status) {
			Log.v(TAG, "onCharacteristicRead");
			if (status == BluetoothGatt.GATT_SUCCESS) {
				broadcastDataUpdate(characteristic);
			}

		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,
		                                    BluetoothGattCharacteristic characteristic) {
			Log.v(TAG, "onCharacteristicChanged");
			broadcastDataUpdate(characteristic);
		}

		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Log.d(TAG, "Callback: Wrote GATT Characteristic successfully.  Characteristic: " + characteristic.getUuid());
			} else {
				Log.d(TAG, "Callback: Error writing GATT Characteristic: " + status + "  Characteristic: " + characteristic.getUuid());
			}
		}

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Log.d(TAG, "Callback: Wrote GATT Descriptor successfully.  Characteristic: " + descriptor.getCharacteristic().getUuid());
				if (CHAR_TEMPERATURE_UUID.equals(descriptor.getCharacteristic().getUuid())) {
					broadcastActionOnline();
				}
			} else {
				Log.d(TAG, "Callback: Error writing GATT Descriptor: " + status + "  Characteristic: " + descriptor.getCharacteristic()
						.getUuid());
				disconnect();
			}
		}
	};

	private void broadcastActionOnline() {
		mConnectionState = ConnectState.ConnectedRunning;
		final Intent intent = new Intent(ACTION_MESSAGE_SERVICE_ONLINE);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	private void broadcastUpdate(final String action) {
		final Intent intent = new Intent(action);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	private void broadcastDataUpdate(
			final BluetoothGattCharacteristic characteristic) {
		final UUID uuid = characteristic.getUuid();
		Log.d(TAG, String.format("Received TX: %s", HexUtils.displayHex(characteristic.getValue())));

		try {
			String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
			if (BLETemperatureService.CHAR_TEMPERATURE_UUID.equals(uuid)) {
				double value = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT,
															/* offset */ 1);
				startNotificationForeground(value);

				final Intent intent = new Intent(ACTION_TEMPERATURERE_UPDATE);
				intent.putExtra("UUID", uuid);
				intent.putExtra(EXTRA_TEMPERATURERE_DATA, value);
				LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
			} else {
				Log.v(TAG, "[" + currentDateTimeString + "] UUID: " + uuid.toString());
			}
		} catch (Exception e) {
			Log.e(TAG, e.toString());
		}
	}

	/**
	 * Initializes a reference to the local Bluetooth adapter.
	 *
	 * @return Return true if the initialization is successful.
	 */
	public boolean initialize() {
		// For API level 18 and above, get a reference to BluetoothAdapter through
		// BluetoothManager.
		if (mBluetoothManager == null) {
			mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
			if (mBluetoothManager == null) {
				Log.e(TAG, "Unable to initialize BluetoothManager.");
				return false;
			}
		}

		mBluetoothAdapter = mBluetoothManager.getAdapter();
		if (mBluetoothAdapter == null) {
			Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
			return false;
		}

		return true;
	}

	/**
	 * Connects to the GATT server hosted on the Bluetooth LE device.
	 *
	 * @param address The device address of the destination device.
	 * @return Return true if the connection is initiated successfully. The connection result
	 * is reported asynchronously through the
	 * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 * callback.
	 */
	public boolean connect(final String address) {
		if (mBluetoothAdapter == null || address == null) {
			Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
			return false;
		}

		// Previously connected device.  Try to reconnect.
		if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
				&& mBluetoothGatt != null) {
			Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
			if (mBluetoothGatt.connect()) {
				mConnectionState = ConnectState.Connecting;
				return true;
			} else {
				return false;
			}
		}

		final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		if (device == null) {
			Log.w(TAG, "Device not found.  Unable to connect.");
			return false;
		}
		// We want to directly connect to the device, so we are setting the autoConnect
		// parameter to false.
		mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
		Log.d(TAG, "Trying to create a new connection.");
		mBluetoothDeviceAddress = address;
		mConnectionState = ConnectState.Connecting;
		return true;
	}

	/**
	 * Disconnects an existing connection or cancel a pending connection. The disconnection result
	 * is reported asynchronously through the
	 * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 * callback.
	 */
	public void disconnect() {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		mBluetoothGatt.disconnect();
	}

	/**
	 * After using a given BLE device, the app must call this method to ensure resources are
	 * released properly.
	 */
	public void close() {
		if (mBluetoothGatt == null) {
			return;
		}
		Log.w(TAG, "mBluetoothGatt closed");
		mBluetoothDeviceAddress = null;
		mBluetoothGatt.close();
		mBluetoothGatt = null;
	}

	/**
	 * Enable TXNotification
	 *
	 * @return
	 */
	public void enableTXNotification() {

		BluetoothGattService temperatureService = mBluetoothGatt.getService(SERVICE_TEMPERATURE_UUID);
		if (temperatureService == null) {
			Log.e(TAG, "temperatureService service not found!");
			broadcastUpdate(NOT_SUPPORT_TEMPERATURE_SERVICE);
			disconnect();
			return;
		}
		final BluetoothGattCharacteristic temperatureServiceCharacteristic = temperatureService.getCharacteristic(CHAR_TEMPERATURE_UUID);
		if (temperatureServiceCharacteristic == null) {
			Log.e(TAG, "temperatureServiceCharacteristic not found!");
			broadcastUpdate(NOT_SUPPORT_TEMPERATURE_SERVICE);
			disconnect();
			return;
		}
		registerUpdateForCharacteristic(temperatureServiceCharacteristic, true);
	}

	private void registerUpdateForCharacteristic(BluetoothGattCharacteristic valChar, boolean enable) {
		mBluetoothGatt.setCharacteristicNotification(valChar, enable);
		final BluetoothGattDescriptor descriptor = valChar.getDescriptor(CCCD);
		descriptor.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor
				.DISABLE_NOTIFICATION_VALUE);
		mBluetoothGatt.writeDescriptor(descriptor);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		initialize();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			String action = intent.getAction();
			if (action != null) {
				if (action.equals(ACTION_CLOSE)) {
					disconnect();
					stopSelf();
				}
			}
		}
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		try {
			close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			stopNotificationForeground();
		} catch (Exception e) {

		}
		super.onDestroy();
	}

	public void startNotificationForeground(double temperatureValue) {
		// Notification
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);
		mBuilder.setSmallIcon(R.drawable.ic_notification);

		if (!TextUtils.isEmpty(mConnectedDeviceName)) {
			mBuilder.setContentTitle(mConnectedDeviceName);
		} else {
			mBuilder.setContentTitle(getString(R.string.app_name) + getString(R.string.connected));
		}
		mBuilder.setContentText(getString(R.string.notification_temperature) + " " +
				String.format(getString(R.string.temperature_template), temperatureValue));

		Intent intent = new Intent(this, MainActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(BLETemperatureService.this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		mBuilder.setContentIntent(pendingIntent);

		Intent iClose = new Intent(this, BLETemperatureService.class);
		iClose.setAction(ACTION_CLOSE);
		PendingIntent piClose = PendingIntent.getService(this, 0,
				iClose, PendingIntent.FLAG_UPDATE_CURRENT);
		mBuilder.addAction(R.drawable.ic_close, this.getResources().getString(R.string.disconnect), piClose);

		mBuilder.setOngoing(true);

		startForeground(NOTIFICATION_ID, mBuilder.build());
	}

	public void stopNotificationForeground() {
		try {
			stopForeground(true);
		} catch (Exception e) {

		}
	}

	// === Binder ===

	public class LocalBinder extends Binder {
		public BLETemperatureService getService() {
			return BLETemperatureService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		return super.onUnbind(intent);
	}

	private final IBinder mBinder = new LocalBinder();

	// === Binder ===

}
