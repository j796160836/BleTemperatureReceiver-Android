/*
 * Copyright (C) 2013 The Android Open Source Project
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
 */

package com.johnny.bletemperaturereceiver;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeviceListActivity extends AppCompatActivity {
	private static final String TAG = DeviceListActivity.class.getSimpleName();

	private BluetoothAdapter mBluetoothAdapter;

	private TextView labelEmptyMessage;
	private Button buttonCancel;

	List<BluetoothDevice> mDeviceList;
	private DeviceAdapter mDeviceAdapter;
	Map<String, Integer> mDevRssiValues;
	private static final long SCAN_PERIOD = 10000; //10 seconds
	private Handler mHandler;
	private boolean mScanning;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate");
		setContentView(R.layout.activity_device_list);

		getSupportActionBar().setTitle(R.string.select_device);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		labelEmptyMessage = (TextView) findViewById(R.id.label_empty_message);
		buttonCancel = (Button) findViewById(R.id.button_cancel);

		mHandler = new Handler();
		// Use this check to determine whether BLE is supported on the device.  Then you can
		// selectively disable BLE-related features.
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
			finish();
		}

		// Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
		// BluetoothAdapter through BluetoothManager.
		final BluetoothManager bluetoothManager =
				(BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();

		// Checks if Bluetooth is supported on the device.
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
			finish();
			return;
		}
		populateList();
		buttonCancel.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mScanning == false) {
					scanLeDevice(true);
				} else {
					finish();
				}
			}
		});
	}

	private void populateList() {
	    /* Initialize device list container */
		Log.d(TAG, "populateList");
		mDeviceList = new ArrayList<>();
		mDeviceAdapter = new DeviceAdapter(this, mDeviceList);
		mDevRssiValues = new HashMap<>();

		ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
		newDevicesListView.setAdapter(mDeviceAdapter);
		newDevicesListView.setOnItemClickListener(mDeviceClickListener);

		scanLeDevice(true);

	}

	private void scanLeDevice(final boolean enable) {
		if (enable) {
			// Stops scanning after a pre-defined scan period.
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					mScanning = false;
					mBluetoothAdapter.stopLeScan(mLeScanCallback);
					buttonCancel.setText(R.string.scan);
				}
			}, SCAN_PERIOD);

			mScanning = true;
			mBluetoothAdapter.startLeScan(mLeScanCallback);
			buttonCancel.setText(R.string.cancel);
		} else {
			mScanning = false;
			mBluetoothAdapter.stopLeScan(mLeScanCallback);
			buttonCancel.setText(R.string.scan);
		}

	}

	private BluetoothAdapter.LeScanCallback mLeScanCallback =
			new BluetoothAdapter.LeScanCallback() {

				@Override
				public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {

							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									addDevice(device, rssi);
								}
							});

						}
					});
				}
			};

	private void addDevice(BluetoothDevice device, int rssi) {
		boolean deviceFound = false;

		for (BluetoothDevice listDev : mDeviceList) {
			if (listDev.getAddress().equals(device.getAddress())) {
				deviceFound = true;
				break;
			}
		}

		mDevRssiValues.put(device.getAddress(), rssi);
		if (!deviceFound) {
			mDeviceList.add(device);
			labelEmptyMessage.setVisibility(View.GONE);

			mDeviceAdapter.notifyDataSetChanged();
		}
	}

	@Override
	public void onStart() {
		super.onStart();

		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
	}

	@Override
	public void onStop() {
		super.onStop();
		mBluetoothAdapter.stopLeScan(mLeScanCallback);

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mBluetoothAdapter.stopLeScan(mLeScanCallback);

	}

	private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			mBluetoothAdapter.stopLeScan(mLeScanCallback);

			Bundle b = new Bundle();
			b.putString(BluetoothDevice.EXTRA_DEVICE, mDeviceList.get(position).getAddress());

			Intent result = new Intent();
			result.putExtras(b);
			setResult(Activity.RESULT_OK, result);
			finish();
		}
	};

	protected void onPause() {
		super.onPause();
		scanLeDevice(false);
	}

	class DeviceAdapter extends BaseAdapter {
		Context context;
		List<BluetoothDevice> devices;
		LayoutInflater inflater;

		public DeviceAdapter(Context context, List<BluetoothDevice> devices) {
			this.context = context;
			inflater = LayoutInflater.from(context);
			this.devices = devices;
		}

		@Override
		public int getCount() {
			return devices.size();
		}

		@Override
		public Object getItem(int position) {
			return devices.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewGroup vg;

			if (convertView != null) {
				vg = (ViewGroup) convertView;
			} else {
				vg = (ViewGroup) inflater.inflate(R.layout.device_element, null);
			}

			BluetoothDevice device = devices.get(position);
			final TextView labelAdd = ((TextView) vg.findViewById(R.id.label_address));
			final TextView labelName = ((TextView) vg.findViewById(R.id.label_name));
			final TextView labelPaired = (TextView) vg.findViewById(R.id.label_paired);
			final TextView labelRssi = (TextView) vg.findViewById(R.id.label_rssi);

			labelRssi.setVisibility(View.VISIBLE);
			byte rssival = (byte) mDevRssiValues.get(device.getAddress()).intValue();
			if (rssival != 0) {
				labelRssi.setText(getString(R.string.rssi_value) + String.valueOf(rssival));
			}

			String deviceName = device.getName();
			if (TextUtils.isEmpty(deviceName)) {
				deviceName = getString(R.string.no_name_device);
			}
			labelName.setText(deviceName);
			labelAdd.setText(device.getAddress());
			if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
				Log.i(TAG, "device::" + device.getName());
				labelPaired.setVisibility(View.VISIBLE);
				labelPaired.setText(R.string.paired);
			} else {
				labelPaired.setVisibility(View.GONE);
				labelRssi.setVisibility(View.VISIBLE);
			}
			return vg;
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == android.R.id.home) {
			onBackPressed();
		}
		return super.onOptionsItemSelected(item);
	}
}
