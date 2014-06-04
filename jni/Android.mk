LOCAL_PATH := $(call my-dir)
LIB_PATH := ../crossbuild/ndk-x86/sysroot/usr/lib
INCLUDE_PATH := $(LOCAL_PATH)/../crossbuild/ndk-x86/sysroot/usr/include

include $(CLEAR_VARS)
LOCAL_MODULE    := prebuilt_libusb
LOCAL_SRC_FILES := $(LIB_PATH)/libusb-1.0.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE	:= prebuilt_libftdi
LOCAL_SRC_FILES := $(LIB_PATH)/libftdi1.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := prebuilt_libdivecomputer
LOCAL_SRC_FILES := $(LIB_PATH)/libdivecomputer.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := local_utils
LOCAL_SRC_FILES := utils.c
LOCAL_C_INCLUDES := $(INCLUDE_PATH)
LOCAL_STATIC_LIBRARIES := prebuilt_libdivecomputer prebuilt_libusb
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := local_common
LOCAL_SRC_FILES := common.c
LOCAL_C_INCLUDES := $(INCLUDE_PATH)
LOCAL_STATIC_LIBRARIES := local_utils
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := ostc3_import
LOCAL_SRC_FILES := com_subsurface_Home.c
LOCAL_LDLIBS := -llog
LOCAL_C_INCLUDES := $(INCLUDE_PATH)
LOCAL_WHOLE_STATIC_LIBRARIES := prebuilt_libusb prebuilt_libftdi prebuilt_libdivecomputer
LOCAL_STATIC_LIBRARIES := local_utils local_common
include $(BUILD_SHARED_LIBRARY)
