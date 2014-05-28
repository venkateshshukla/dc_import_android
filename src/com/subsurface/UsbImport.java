package com.subsurface;

import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;

public class UsbImport extends SherlockActivity {

    private Intent receivedIntent;
    private TextView tvImportLogs;
    private UsbManager usbManager;
    private UsbDevice device;
    private String logs;
    private ImportTask importTask;
    private enum ThreadState {RUNNING, STOPPED};
    private  ThreadState threadState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.usb_import);
        setTitle(R.string.title_usb_import);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        receivedIntent = getIntent();
        tvImportLogs = (TextView) findViewById(R.id.tvImportLogs);
        logs = "";
        getSherlock().setProgressBarIndeterminateVisibility(true);
        importTask = new ImportTask();
        threadState = ThreadState.STOPPED;
    }

    @Override
    protected void onResume() {
        super.onResume();
        device = receivedIntent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

        if(device != null && logs.contentEquals("")) {
            logs += device.toString();
        }
        tvImportLogs.setText(logs);

        if(threadState == ThreadState.STOPPED) {
            importTask.execute();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_usbimport_refresh:
                this.onResume();
                tvImportLogs.setText(logs);
                return true;
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getSupportMenuInflater().inflate(R.menu.usb_import, menu);
        return super.onCreateOptionsMenu(menu);
    }

    private class ImportTask extends AsyncTask<Void, Void, Void> {

        private static final String TAG = "ImportTask";

        private static final int FTDI_REQTYPE_READ  = 0xC0;
        private static final int FTDI_REQTYPE_WRITE = 0x40;

        private static final int FTDI_REQUEST_RESET          = 0x00; /* Reset the port */
        private static final int FTDI_REQUEST_SET_MODEM_CTRL = 0x01; /* Set the modem control register */
        private static final int FTDI_REQUEST_SET_FLOW_CTRL  = 0x02; /* Set flow control register */
        private static final int FTDI_REQUEST_SET_BAUDRATE   = 0x03; /* Set baud rate */
        private static final int FTDI_REQUEST_SET_DATA       = 0x04; /* Set the data characteristics of the port */

        private static final int FTDI_VALUE_DATA_CONFIG = 0x08; /* Parity None, 8 Data Bits and 1 Stop Bit */
        private static final int FTDI_VALUE_BAUDRATE    = 0x1A; /* corresponding to 115200 baudrate - the factor would be 3000000/115200 */

        private static final int FTDI_VALUE_RESET_IO    = 0x00;
        private static final int FTDI_VALUE_RESET_RX    = 0x01;
        private static final int FTDI_VALUE_RESET_TX    = 0x02;

        private static final int FTDI_TIMEOUT_READ  = 5000;
        private static final int FTDI_TIMEOUT_WRITE = 5000;

        private static final int FTDI_TIME_SLEEP = 300;

        /**
         * OSTC 3 Specific Commands
         */
        public static final int INIT       = 0xBB;
        public static final int EXIT       = 0xFF;
        public static final int READY      = 0x4D;
        public static final int IDENTITY   = 0x69;
        public static final int HEADER     = 0x61;
        public static final int DIVE       = 0x66;


        private byte[] readData = new byte[64];
        private byte[] writeData = new byte[1];

        private boolean forceClaim = true;

        private UsbInterface usbInterface;
        private UsbEndpoint readEndpoint;
        private UsbEndpoint writeEndpoint;
        private UsbDeviceConnection usbConnection;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            getSherlock().setProgressBarIndeterminateVisibility(false);
            threadState = ThreadState.RUNNING;

            usbInterface = device.getInterface(0);
            readEndpoint = usbInterface.getEndpoint(0);
            writeEndpoint = usbInterface.getEndpoint(1);
            usbConnection= usbManager.openDevice(device);

            if(usbConnection != null) {
                Log.d(TAG, "Device opened");
                logs += "Device opened\n";
            } else {
                Log.e(TAG, "Failed to open device");
                logs += "Failed to open device\n";
                this.cancel(true);
            }
            boolean claimed  = usbConnection.claimInterface(usbInterface, forceClaim);
            if(claimed) {
                Log.d(TAG, "Interface claimed");
                logs += "Interface claimed\n";
            } else {
                Log.e(TAG, "Failed to claim interface");
                logs += "Failed to claim interface\n";
                this.cancel(true);
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            synchronized (this) {
                setBaudRate();
                setDataConfiguration();
                purgeBuffers(true, true);
                initialiseDevice();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            super.onPostExecute(v);
            closeDevice();
            tvImportLogs.setText(logs);
            threadState = ThreadState.STOPPED;
            importTask = new ImportTask();
            logs += "\n\n";
        }

        @Override
        protected void onCancelled(Void v) {
            super.onCancelled(v);
            if(usbConnection != null) {
                closeDevice();
            }
            tvImportLogs.setText(logs);
            threadState = ThreadState.STOPPED;
            importTask = new ImportTask();
            logs += "\n\n";
        }

        void closeDevice() {
            byte[] exit = {(byte) EXIT};
            int result = usbConnection.bulkTransfer(
                    writeEndpoint,
                    exit, 1,
                    FTDI_TIMEOUT_WRITE);
            if(result < 0) {
                Log.e(TAG, "Failed to send EXIT command.");
                logs += "Failed to send EXIT command\n";
            } else {
                Log.d(TAG, "EXIT command sent.");
                logs += "EXIT command sent.\n";
            }
        }

        void initialiseDevice() {
            byte[] init = {(byte) INIT};
            int result = usbConnection.bulkTransfer(
                    writeEndpoint,
                    init, 1,
                    FTDI_TIMEOUT_WRITE);
            if(result < 0) {
                Log.e(TAG, "Failed to write INIT command.");
                logs += "Failed to write INIT command.\n";
                this.cancel(true);
            } else {
                Log.d(TAG, "INIT command written");
                logs += "INIT command written.\n";
                sleep();
            }

            byte[] echo = new byte[3];
            result = usbConnection.bulkTransfer(
                    readEndpoint,
                    echo, 3,
                    FTDI_TIMEOUT_READ);
            if(result < 0) {
                Log.e(TAG, "Failed to read echo of INIT.");
                logs += "Failed to read echo of INIT.\n";
                this.cancel(true);
            } else {
                Log.d(TAG, String.format("Echo of INIT read : %02x|%02x|%02x",
                        echo[0], echo[1], echo[2]));
                logs += String.format("Echo of INIT read : %02x|%02x|%02x\n",
                        echo[0], echo[1], echo[2]);
                sleep();
            }

            if(echo[0] != init[0]) {
                Log.e(TAG, "Echo not same as INIT.");
                logs += "Echo not same as INIT.\n";
                this.cancel(true);
            } else {
                Log.d(TAG, "Echo verified.");
                logs += "Echo verified.\n";
            }

        }

        void setDataConfiguration() {
            int result = usbConnection.controlTransfer(
                    FTDI_REQTYPE_WRITE,
                    FTDI_REQUEST_SET_DATA,
                    FTDI_VALUE_DATA_CONFIG,
                    0, null, 0,
                    FTDI_TIMEOUT_WRITE);
            if(result != 0) {
                Log.e(TAG, "Failed to set the data configuration.");
                logs += "Failed to set the data configuration.\n";
                this.cancel(true);
            } else {
                Log.d(TAG, "Data configuration set");
                logs += "Data configuration set.\n";
                sleep();
            }
        }

        void setBaudRate() {
            int result = usbConnection.controlTransfer(
                    FTDI_REQTYPE_WRITE,
                    FTDI_REQUEST_SET_BAUDRATE,
                    FTDI_VALUE_BAUDRATE,
                    0, null, 0,
                    FTDI_TIMEOUT_WRITE);
            if(result != 0) {
                Log.e(TAG, "Failed to set the baudrate.");
                logs += "Failed to set the baudrate.\n";
                this.cancel(true);
            } else {
                Log.d(TAG, "Baudrate set.");
                logs += "Baudrate set.\n";
                sleep();
            }
        }

        private void purgeBuffers(boolean readBuffer, boolean writeBuffer) {
            if(readBuffer) {
                int result = usbConnection.controlTransfer(
                        FTDI_REQTYPE_WRITE,
                        FTDI_REQUEST_RESET,
                        FTDI_VALUE_RESET_RX,
                        0, null, 0,
                        FTDI_TIMEOUT_WRITE);
                if(result != 0) {
                    Log.e(TAG, "Failed to purge read buffer.");
                    logs += "Failed to purge read buffer.\n";
                    this.cancel(true);
                } else {
                    Log.d(TAG, "Read buffer purged.");
                    logs += "Read buffer purged.\n";
                    sleep();
                }
            }
            if(writeBuffer) {
                int result = usbConnection.controlTransfer(
                        FTDI_REQTYPE_WRITE,
                        FTDI_REQUEST_RESET,
                        FTDI_VALUE_RESET_TX,
                        0, null, 0, FTDI_TIMEOUT_WRITE);
                if(result != 0) {
                    Log.e(TAG, "Failed to purge write buffer.");
                    logs += "Failed to purge write buffer.\n";
                    this.cancel(true);
                } else {
                    Log.d(TAG, "Write buffers purged.");
                    logs += "Write buffers purged.\n";
                    sleep();
                }
            }
            sleep();
        }
        private void sleep() {
            try {
                new Thread().sleep(FTDI_TIME_SLEEP);
            } catch (InterruptedException e) {}
        }

    }

}
