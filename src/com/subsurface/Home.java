package com.subsurface;

import java.util.ArrayList;
import java.util.HashMap;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.subsurface.ui.DevicelistAdapter;

public class Home extends SherlockListActivity implements OnItemClickListener {

	private UsbManager usbManager;
	private HashMap<String, UsbDevice> devicemap;
	private ArrayList<UsbDevice> allUsbDevices;
	private DevicelistAdapter adapter;
	private ListView listView;
	private PendingIntent permissionIntent;
	private UsbPermissionReceiver permissionReceiver;
	private static final String TAG = "UseUsbEnum";
	private static final String ACTION_USB_PERMISSION = "org.subsurface.USB_PERMISSION";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(R.string.title_usb_list);

		usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		allUsbDevices = new ArrayList<UsbDevice>();
		setListAdapter(refreshListAdapter());

		listView = getListView();
		listView.setOnItemClickListener(this);

		permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
				ACTION_USB_PERMISSION), 0);
		permissionReceiver = new UsbPermissionReceiver(usbManager);
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		registerReceiver(permissionReceiver, filter);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		UsbDevice device = (UsbDevice) parent.getAdapter().getItem(position);
		usbManager.requestPermission(device, permissionIntent);
	}

	@Override
	protected void onResume() {
		setListAdapter(refreshListAdapter());
		super.onResume();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getSupportMenuInflater().inflate(R.menu.usb_import, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_usbimport_refresh:
			setListAdapter(refreshListAdapter());
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private DevicelistAdapter refreshListAdapter() {
		devicemap = usbManager.getDeviceList();
		allUsbDevices.clear();
		allUsbDevices.addAll(devicemap.values());
		adapter = new DevicelistAdapter(this, allUsbDevices);
		Log.v(TAG, "No. of USB devices : " + allUsbDevices.size());
		return adapter;
	}

	@Override
	protected void onDestroy() {
		unregisterReceiver(permissionReceiver);
		super.onDestroy();
	}

}
