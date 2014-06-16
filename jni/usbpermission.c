#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <android/log.h>
#include <jni.h>

#define LOG_TAG "com.subsurface"

#define LOG_D(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOG_F(fn_name) __android_log_write(ANDROID_LOG_DEBUG, LOG_TAG, "Called : " fn_name )

// From http://developer.android.com/reference/android/content/Context.html#USB_SERVICE
#define USB_SERVICE "usb"

#define FTDI_VID 0x0403
#define ACTION_USB_PERMISSION "org.subsurface.USB_PERMISSION"

jobject get_ftdi_usb_devices (JNIEnv *, jobject);

// A single JavaVM is shared by all threads in a process.
// Hence it can be cached.
static JavaVM *java_vm;

/**
 * Gets permission for usage of FTDI device attached to USB.
 * Implemented verbatim from Home.java
 *
 * \param activity - The activity object of the android application from which this
 * method is called
 *
 * \return
 *  0 - On success
 * -1 - On Failure
 *
 **/
int get_usb_permission (JNIEnv *env, jobject activity)
{
	LOG_F ("get_usb_permission");

	jstring action_usb_permission = (*env)->NewStringUTF (env, ACTION_USB_PERMISSION);
	jstring usb_service = (*env)->NewStringUTF(env, USB_SERVICE);

	jclass Activity = (*env)->GetObjectClass (env, activity);
	jmethodID getSystemService = (*env)->GetMethodID (env, Activity, "getSystemService", "(Ljava/lang/String;)Ljava/lang/Object;");
	jmethodID registerReceiver = (*env)->GetMethodID (env, Activity, "registerReceiver", "(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;");

	jclass UsbManager = (*env)->FindClass (env, "android/hardware/usb/UsbManager");
	jmethodID UsbManagerGetDeviceMap = (*env)->GetMethodID (env, UsbManager, "getDeviceList", "()Ljava/util/HashMap;");
	jmethodID UsbManagerRequestPermission = (*env)->GetMethodID (env, UsbManager, "requestPermission", "(Landroid/hardware/usb/UsbDevice;Landroid/app/PendingIntent;)V");
	jmethodID UsbManagerHasPermission = (*env)->GetMethodID (env, UsbManager, "hasPermission", "(Landroid/hardware/usb/UsbDevice;)Z");
	jobject usbManager = (*env)->CallObjectMethod(env, activity, getSystemService, usb_service);

	jclass UsbDeviceConnection = (*env)->FindClass (env, "android/hardware/usb/UsbDeviceConnection");
	jclass UsbDevice = (*env)->FindClass (env, "android/hardware/usb/UsbDevice");

	jclass Intent = (*env)->FindClass (env, "android/content/Intent");
	jmethodID IntentConstructor = (*env)->GetMethodID (env, Intent, "<init>", "(Ljava/lang/String;)V");
	jobject intent = (*env)->NewObject (env, Intent, IntentConstructor, action_usb_permission);

	jclass PendingIntent = (*env)->FindClass (env, "android/app/PendingIntent");
	jmethodID PendingIntentGetBroadcast = (*env)->GetStaticMethodID (env, PendingIntent, "getBroadcast", "(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;");

	jclass UsbPermissionReceiver = (*env)->FindClass (env, "com/subsurface/UsbPermissionReceiver");
	jmethodID UsbPermissionReceiverConstructor = (*env)->GetMethodID (env, UsbPermissionReceiver, "<init>", "(Landroid/hardware/usb/UsbManager;)V");
	jobject usbPermissionReceiver = (*env)->NewObject (env, UsbPermissionReceiver, UsbPermissionReceiverConstructor, usbManager);

	jclass IntentFilter = (*env)->FindClass (env, "android/content/IntentFilter");
	jmethodID IntentFilterConstructor = (*env)->GetMethodID (env, IntentFilter, "<init>", "(Ljava/lang/String;)V");
	jobject intentFilter = (*env)->NewObject (env, IntentFilter, IntentFilterConstructor, action_usb_permission);

	jobject permissionIntent = (*env)->CallStaticObjectMethod(env, PendingIntent, PendingIntentGetBroadcast, activity, 0, intent, 0);

	jobject intent_return = (*env)->CallObjectMethod (env, activity, registerReceiver, usbPermissionReceiver, intentFilter);

	jclass ArrayList = (*env)->FindClass (env, "java/util/ArrayList");
	jmethodID ArrayListConstructor = (*env)->GetMethodID (env, ArrayList, "<init>", "()V");
	jmethodID ArrayListAdd = (*env)->GetMethodID (env, ArrayList, "add", "(Ljava/lang/Object;)Z");
	jmethodID ArrayListGet = (*env)->GetMethodID (env, ArrayList, "get", "(I)Ljava/lang/Object;");
	jmethodID ArrayListSize = (*env)->GetMethodID (env, ArrayList, "size", "()I");

	jclass HashMap = (*env)->FindClass (env, "java/util/HashMap");
	jmethodID HashMapConstructor = (*env)->GetMethodID (env, HashMap, "<init>", "()V");

	jobject allUsbDevice = (*env)->CallObjectMethod (env, usbManager, UsbManagerGetDeviceMap);

	jobject deviceList = get_ftdi_usb_devices (env, allUsbDevice);
	jint size = (*env)->CallIntMethod (env, deviceList, ArrayListSize);

	if (size > 0) {
		jobject usbDevice = (*env)->CallObjectMethod (env, deviceList, ArrayListGet, 0);
		if ((*env)->CallBooleanMethod (env, usbManager, UsbManagerHasPermission, usbDevice)) {
			LOG_D ("Already Have permission for USB device");
		} else {
			LOG_D ("Asking permission for USB device");
		}
		(*env)->CallVoidMethod (env, usbManager, UsbManagerRequestPermission, usbDevice, permissionIntent);

	}
	LOG_D ("get_usb_permission finished.");
}

jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
	LOG_F ("JNI_OnLoad");
	java_vm = vm;

	// Get JNI Env for all function calls
	JNIEnv* env;
	if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
	    LOG_D ("GetEnv failed.");
	    return -1;
	}

	// Find the class calling native function
	jclass NativeUsb = (*env)->FindClass(env, "com/subsurface/NativeUsb");
	if (NativeUsb == NULL) {
		LOG_D ("FindClass failed : No class found.");
		return -1;
	}

	// Register native method for getUsbPermission
	JNINativeMethod nm[1] = {
		{ "getUsbPermission", "()I", get_usb_permission}
	};

	if ((*env)->RegisterNatives(env, NativeUsb, nm , 1)) {
	     LOG_D ("RegisterNatives Failed.");
	     return -1;
	}

	return JNI_VERSION_1_6;
}

/**
 * Get an ArrayList of all attached FTDI usb devices.
 *
 * \param env JNIEnv - The JNI Environment necessary for calling all JNI functions
 * \param deviceMap - HashMap<String, UsbDevice> of all the Usb Devices attached to android
 *
 * \return
 * On success - ArrayList of all FTDI devices attached to USB of android.
 *
 **/
jobject get_ftdi_usb_devices (JNIEnv *env, jobject deviceMap) {
	LOG_F ("get_ftdi_usb_devices");

	jclass ArrayList = (*env)->FindClass (env, "java/util/ArrayList");
	jmethodID ArrayListConstructor = (*env)->GetMethodID (env, ArrayList, "<init>", "()V");
	jmethodID ArrayListAdd = (*env)->GetMethodID (env, ArrayList, "add", "(Ljava/lang/Object;)Z");
	jobject deviceList = (*env)->NewObject(env, ArrayList, ArrayListConstructor);

	jclass UsbDevice = (*env)->FindClass (env, "android/hardware/usb/UsbDevice");
	jmethodID UsbDeviceGetVendorId = (*env)->GetMethodID (env, UsbDevice, "getVendorId", "()I");

	jclass HashMap = (*env)->GetObjectClass (env, deviceMap);
	jmethodID HashMapSize = (*env)->GetMethodID (env, HashMap, "size", "()I");
	jmethodID HashMapGet = (*env)->GetMethodID (env, HashMap, "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
	jmethodID KeySet = (*env)->GetMethodID (env, HashMap, "keySet", "()Ljava/util/Set;");
	jsize size = (*env)->CallIntMethod (env, deviceMap, HashMapSize);
	jobject keySet = (*env)->CallObjectMethod (env, deviceMap, KeySet);

	LOG_D ("Size of the USB Device List : %d", size);

	jclass Set = (*env)->GetObjectClass (env, keySet);
	jmethodID getIterator = (*env)->GetMethodID (env, Set, "iterator", "()Ljava/util/Iterator;");
	jobject keyIterator = (*env)->CallObjectMethod (env, keySet, getIterator);

	jclass Iterator = (*env)->GetObjectClass (env, keyIterator);
	jmethodID IteratorGetNext = (*env)->GetMethodID (env, Iterator, "next", "()Ljava/lang/Object;");

	int i;
	for (i = 0; i < size; i++) {
		jstring devicename = (*env)->CallObjectMethod (env, keyIterator, IteratorGetNext);
		jsize length = (*env)->GetStringUTFLength(env, devicename);
		char name[100];
		(*env)->GetStringUTFRegion(env, devicename, 0, length, name);
		LOG_D ("Device %d : %s", i, name);

		jobject usbDevice = (*env)->CallObjectMethod (env, deviceMap, HashMapGet, devicename);
		jint device_vid = (*env)->CallIntMethod (env, usbDevice, UsbDeviceGetVendorId);

		if (device_vid == FTDI_VID) {
			LOG_D ("It is a FTDI Device");
			jboolean ret = (*env)->CallBooleanMethod (env, deviceList, ArrayListAdd, usbDevice);
		} else {
			LOG_D ("Not a FTDI Device");
		}
	}
	LOG_D ("returning get_ftdi_usb_devices");
	return deviceList;
}

