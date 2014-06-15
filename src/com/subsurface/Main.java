package com.subsurface;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class Main extends Activity implements OnClickListener{

	private Button bNative;
	private Button bJava;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		bNative = (Button) findViewById(R.id.bNativeActivity);
		bJava = (Button) findViewById(R.id.bJavaActivity);
		
		bNative.setOnClickListener(this);
		bJava.setOnClickListener(this);
	}

	@Override
	public void onClick(View v) {
		Intent i = null;
		switch (v.getId()) {
		case R.id.bJavaActivity:
			i = new Intent(this, Home.class);
			break;
		case R.id.bNativeActivity:
			i = new Intent(this, NativeUsb.class);
			break;
		}
		startActivity(i);
	}


}
