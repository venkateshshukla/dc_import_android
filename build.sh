#!/bin/bash
set -e

# Configure where we can find things here
export ANDROID_NDK_ROOT=/android_ndk
export ANDROID_SDK_ROOT=/android_sdk

# Platform architecture
export ARCH=${1-x86}

if [ "$ARCH" = "arm" ] ;
then
	BUILDCHAIN=arm-linux-androideabi
elif [ "$ARCH" = "x86" ] ;
then
	BUILDCHAIN=i686-linux-android
fi

pushd crossbuild

# Initialise cross toolchain
if [ ! -e ndk-$ARCH ] ; then
	$ANDROID_NDK_ROOT/build/tools/make-standalone-toolchain.sh --system=linux-x86_64 --arch=$ARCH --install-dir=ndk-$ARCH --platform=android-19
fi

# Declare necessary variables for cross compilation
export BUILDROOT=$PWD
export PATH=${BUILDROOT}/ndk-$ARCH/bin:$PATH
export PREFIX=${BUILDROOT}/ndk-$ARCH/sysroot/usr
export PKG_CONFIG_PATH=${PREFIX}/lib/pkgconfig
export CC=${BUILDCHAIN}-gcc
export CXX=${BUILDCHAIN}-g++

# Download libdivecomputer submodule
if [ ! -e libdivecomputer/configure.ac ] ; then
	git submodule init
	git submodule update
fi

# Fetch external repos
mkdir -vp archives
pushd archives

if [ ! -e libusb-1.0.9.tar.bz2 ] ; then
	wget http://sourceforge.net/projects/libusb/files/libusb-1.0/libusb-1.0.9/libusb-1.0.9.tar.bz2
fi

if [ ! -e libftdi1-1.1.tar.bz2 ] ; then
	wget http://www.intra2net.com/en/developer/libftdi/download/libftdi1-1.1.tar.bz2
fi

popd

# Prepare for building
mkdir -vp build

# Extract and build libusb
if [ ! -e libusb-1.0.9 ] ; then
	tar -jxf archives/libusb-1.0.9.tar.bz2
fi
if ! grep -q __ANDROID__ libusb-1.0.9/libusb/io.c ; then
	# patch libusb to build with android-ndk
	patch -p1 < libusb-1.0.9-android.patch  libusb-1.0.9/libusb/io.c
fi

pushd build
if [ ! -e $PKG_CONFIG_PATH/libusb-1.0.pc ] ; then
	mkdir -p libusb-build-$ARCH
	pushd libusb-build-$ARCH
	../../libusb-1.0.9/configure --host=${BUILDCHAIN} --prefix=${PREFIX} --enable-static --disable-shared
	make
	make install
	popd
fi
popd

# Extract and build libftdi
if [ ! -e libftdi1-1.1 ] ; then
	tar -jxf archives/libftdi1-1.1.tar.bz2
fi

pushd build
if [ ! -e $PKG_CONFIG_PATH/libftdipp1.pc ] ; then
	mkdir -p libftdi1-1.1-build-$ARCH
	pushd libftdi1-1.1-build-$ARCH
	cmake -DCMAKE_C_COMPILER=${CC} -DCMAKE_INSTALL_PREFIX=${PREFIX} -DBUILD_SHARED_LIBS=OFF -DCMAKE_FIND_ROOT_PATH=${PREFIX} -DCMAKE_FIND_ROOT_PATH_MODE_PROGRAM=NEVER -DCMAKE_FIND_ROOT_PATH_MODE_LIBRARY=ONLY -DCMAKE_FIND_ROOT_PATH_MODE_INCLUDE=ONLY ../../libftdi1-1.1
	make
	make install
	popd
fi
popd

# Build libdivecomputer
if [ ! -e libdivecomputer/configure ] ; then
	pushd libdivecomputer
	autoreconf -i
	popd
fi

pushd build
if [ ! -e $PKG_CONFIG_PATH/libdivecomputer.pc ] ; then
	mkdir -p libdivecomputer-build-$ARCH
	pushd libdivecomputer-build-$ARCH
	../../libdivecomputer/configure --host=${BUILDCHAIN} --prefix=${PREFIX} --enable-static --disable-shared --enable-logging LDFLAGS=-llog
	make
	make install
	popd
fi
popd

popd # from crossbuild

if [[ $? == 0 ]] ; then
echo "Finished building requisites."
fi

# Build native libraries
ndk-build -B

# Update application if build.xml is not present
if [ ! -e build.xml ] ; then
	android update project -n HwDiveImport -p ./ -t android-19 -l ../actionbarsherlock --subprojects
fi

# Build the project in debug mode and install on the connected device
#ant clean
#ant -Dadb.device.arg="-e" debug install
ant debug install

# Run the application on the emulator
adb shell am start -a android.intent.action.MAIN -n com.subsurface/com.subsurface.Home
