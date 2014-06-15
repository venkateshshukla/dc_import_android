package com.subsurface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.Toast;

public class UsbPermissionReceiver extends BroadcastReceiver {
	private static final String TAG = "UsbPermissionReceiver";
	private static final String ACTION_USB_PERMISSION = "org.subsurface.USB_PERMISSION";
	private UsbManager usbManager;
	
	private native void doImport(int fd);

	static {
		System.loadLibrary("ostc3_import");
	}
	
	public UsbPermissionReceiver(UsbManager usbManager) {
		super();
		this.usbManager = usbManager;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		if (action.equals(ACTION_USB_PERMISSION)) {
			synchronized (this) {
				UsbDevice device = intent
						.getParcelableExtra(UsbManager.EXTRA_DEVICE);
				if (intent.getBooleanExtra(
						UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
					if (device != null) {
						Toast.makeText(context, device.toString(),
								Toast.LENGTH_LONG).show();
						UsbDeviceConnection usbConnection = usbManager.openDevice(device);
						if(usbConnection != null) {
							int fd = usbConnection.getFileDescriptor();
							Log.i(TAG, "File Descriptor : " + fd);
							if(fd > 0) { 
								doImport(fd);
							}
						}
						
//						Intent usbIntent = new Intent(context,
//								UsbImport.class);
//						usbIntent.putExtra(UsbManager.EXTRA_DEVICE, device);
//						startActivity(usbIntent);
					}
				} else {
					Log.e(TAG, "permission denied" + " for device "
							+ device);
				}
			}
		}

	}
}