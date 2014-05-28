package com.subsurface.ui;

import java.util.ArrayList;

import com.subsurface.R;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class DevicelistAdapter extends BaseAdapter{
	private final ArrayList<UsbDevice> devicelist;
	private final LayoutInflater inflater;

	public DevicelistAdapter(Context context, ArrayList<UsbDevice> devicelist) {
		super();
		this.devicelist = devicelist;
		this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}

	@Override
	public int getCount() {
		return devicelist.size();
	}

	@Override
	public Object getItem(int position) {
		return devicelist.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ViewHolder holder = null;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.devicelistitem, parent, false);
            holder = new ViewHolder();
            holder.tvUsbDevName = (TextView) convertView.findViewById(R.id.tvUsbDevName);
            holder.tvUsbDevPID = (TextView) convertView.findViewById(R.id.tvUsbDevPID);
            holder.tvUsbDevVID = (TextView) convertView.findViewById(R.id.tvUsbDevVID);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        String name = devicelist.get(position).getDeviceName();
        int pid = devicelist.get(position).getProductId();
        int vid = devicelist.get(position).getVendorId();

        holder.tvUsbDevName.setText(name);
        holder.tvUsbDevPID.setText(String.format("%04x", pid));
        holder.tvUsbDevVID.setText(String.format("%04x", vid));
        return convertView;
	}

	public static class ViewHolder {
		public TextView tvUsbDevName;
		public TextView tvUsbDevPID;
		public TextView tvUsbDevVID;
	}
}
