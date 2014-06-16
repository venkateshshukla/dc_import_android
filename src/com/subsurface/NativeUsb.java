package com.subsurface;

import android.app.Activity;
import android.os.Bundle;

public class NativeUsb extends Activity {

	private native int getUsbPermission();

	static {
		System.loadLibrary("usbpermission");
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.nativeusb);
		getUsbPermission();
	}
}
