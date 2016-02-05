package com.johnny.bletemperaturereceiver;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
	private static final String TAG = MainActivity.class.getSimpleName();

	private static final int REQUEST_SELECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;

	public static class BleConnectionStatus {
		private static final int CONNECTED = 20;
		private static final int DISCONNECTED = 21;
	}

	private int mState = BleConnectionStatus.DISCONNECTED;
	private BluetoothDevice mDevice = null;
	private BluetoothAdapter mBtAdapter;
	private TextView labelDeviceName;
	private TextView labelTemperature;
	private Button buttonConnect;

	private BLETemperatureService mService = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mBtAdapter = BluetoothAdapter.getDefaultAdapter();

		if (mBtAdapter == null) {
			Toast.makeText(this, R.string.bluetooth_is_not_available, Toast.LENGTH_LONG).show();
			finish();
			return;
		}

		labelTemperature = (TextView) findViewById(R.id.label_temperature);
		labelDeviceName = (TextView) findViewById(R.id.label_device_name);

		buttonConnect = (Button) findViewById(R.id.btn_connect);
		buttonConnect.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (maybeRequestPermission()) {
					return;
				}

				if (!mBtAdapter.isEnabled()) {
					Log.i(TAG, "onClick - BT not enabled yet");
					Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
					startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
				} else {
					if (mState == BleConnectionStatus.DISCONNECTED) {

						//Connect button pressed, open DeviceListActivity class, with popup windows that scan for devices
						intentSearchDevices();
					} else {
						//Disconnect button pressed
						if (mDevice != null) {
							mService.disconnect();
						}
					}
				}
			}
		});

		buttonConnect.setEnabled(false);
		serviceInit();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		try {
			LocalBroadcastManager.getInstance(this).unregisterReceiver(uartStatusChangeReceiver);
		} catch (Exception ignore) {
			Log.e(TAG, ignore.toString());
		}
		unbindService(mServiceConnection);
		mService = null;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {

			case REQUEST_SELECT_DEVICE:
				//When the DeviceListActivity return, with the selected device address
				if (resultCode == Activity.RESULT_OK && data != null) {
					String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
					mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);

					String deviceName = "";
					if (mDevice != null) {
						Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
						deviceName = mDevice.getName();
					}

					labelDeviceName.setText(String.format(getString(R.string.device_connecting), deviceName));
					if (mService != null) {
						mService.connect(deviceAddress);
					}

				}
				break;
			case REQUEST_ENABLE_BT:
				// When the request to enable Bluetooth returns
				if (resultCode == Activity.RESULT_OK) {
					Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();
				} else {
					// User did not enable Bluetooth or an error occurred
					Log.d(TAG, "BT not enabled");
					Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
					finish();
				}
				break;
			default:
				Log.e(TAG, "wrong request code");
				break;
		}
	}

	private final ServiceConnection mServiceConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder rawBinder) {
			mService = ((BLETemperatureService.LocalBinder) rawBinder).getService();
			if (!mService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				finish();
			}
			if (mService.getConnectionState() == BLETemperatureService.ConnectState.Connected) {
				if (mDevice == null) {
					mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mService.getBluetoothDeviceAddress());
				}
				mState = BleConnectionStatus.CONNECTED;
			} else {
				mState = BleConnectionStatus.DISCONNECTED;
			}
			updateConnectionState();

			buttonConnect.setEnabled(true);
		}

		public void onServiceDisconnected(ComponentName classname) {
			mService = null;
		}
	};

	private final BroadcastReceiver uartStatusChangeReceiver = new BroadcastReceiver() {

		public void onReceive(Context context, final Intent intent) {
			String action = intent.getAction();
			if (action.equals(BLETemperatureService.ACTION_GATT_CONNECTED)) {
				runOnUiThread(new Runnable() {
					public void run() {
						mState = BleConnectionStatus.CONNECTED;
						updateConnectionState();
					}
				});
			} else if (action.equals(BLETemperatureService.ACTION_GATT_DISCONNECTED)) {
				runOnUiThread(new Runnable() {
					public void run() {
						mState = BleConnectionStatus.DISCONNECTED;
						updateConnectionState();
						if (mService != null) {
							mService.close();
						}
					}
				});
			} else if (action.equals(BLETemperatureService.NOT_SUPPORT_TEMPERATURE_SERVICE)) {
				Toast.makeText(MainActivity.this, R.string.temperature_service_not_found, Toast.LENGTH_SHORT).show();
			} else if (action.equals(BLETemperatureService.ACTION_TEMPERATURERE_UPDATE)) {
				runOnUiThread(new Runnable() {
					public void run() {
						double temperature = intent.getDoubleExtra(BLETemperatureService.EXTRA_TEMPERATURERE_DATA, 0);
						labelTemperature.setText(String.format(getString(R.string.temperature_template), temperature));
					}
				});
			}

		}
	};

	private void intentSearchDevices() {
		Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
		startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
	}

	private void updateConnectionState() {
		if (mState == BleConnectionStatus.CONNECTED) {
			buttonConnect.setText(R.string.disconnect);
			String deviceName = "";
			if (mDevice != null) {
				deviceName = mDevice.getName();
			}
			labelDeviceName.setText(String.format(getString(R.string.device_connected), deviceName));
		} else {
			buttonConnect.setText(R.string.connect);
			labelDeviceName.setText(R.string.not_connected);
			labelTemperature.setText(R.string.temperature_unknown);
		}
	}

	private void serviceInit() {
		Intent bindIntent = new Intent(this, BLETemperatureService.class);
		bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

		LocalBroadcastManager.getInstance(this).registerReceiver(uartStatusChangeReceiver, makeGattUpdateIntentFilter());
	}

	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BLETemperatureService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(BLETemperatureService.ACTION_GATT_DISCONNECTED);
		intentFilter.addAction(BLETemperatureService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(BLETemperatureService.ACTION_TEMPERATURERE_UPDATE);
		intentFilter.addAction(BLETemperatureService.NOT_SUPPORT_TEMPERATURE_SERVICE);
		return intentFilter;
	}

	// Permission request listener method

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
	                                       @NonNull int[] grantResults) {
		if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			intentSearchDevices();
		} else {
			Toast.makeText(getApplicationContext(), R.string.permission_denied,
					Toast.LENGTH_LONG).show();
			finish();
		}
	}

	// Permission management methods

	/**
	 * Checks whether it is necessary to ask for permission to access coarse location. If necessary, it also
	 * requests permission.
	 *
	 * @return true if a permission request is made. False if it is not necessary.
	 */
	@TargetApi(23)
	private boolean maybeRequestPermission() {
		if (requiresPermission()) {
			requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
			return true;
		} else {
			return false;
		}
	}

	@TargetApi(23)
	private boolean requiresPermission() {
		return Build.VERSION.SDK_INT >= 23
				&& checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
				!= PackageManager.PERMISSION_GRANTED;
	}
}
