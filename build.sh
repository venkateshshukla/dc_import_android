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

# Download libdivecomputer, libusb and libftdi submodule
if [ ! -e libdivecomputer/configure.ac ] || [ ! -e libusb/configure.ac ] || [ ! -e libftdi/CMakeLists.txt] ; then
	git submodule init
	git submodule update
fi

# Prepare for building
mkdir -vp build

# Build libusb
if [ ! -e libusb/configure ] ; then
	pushd libusb
	autoreconf --install
	popd
fi

mkdir -p build/libusb-build-$ARCH
pushd build/libusb-build-$ARCH
if [ ! -e Makefile ] ; then
	../../libusb/configure --host=${BUILDCHAIN} --prefix=${PREFIX} --enable-static --disable-shared --disable-udev
fi
make
make install
popd

# Build libftdi
mkdir -p build/libftdi-build-$ARCH
pushd build/libftdi-build-$ARCH
if [ ! -e Makefile ] ; then
	cmake -DCMAKE_C_COMPILER=${CC} -DCMAKE_INSTALL_PREFIX=${PREFIX} -DCMAKE_PREFIX_PATH=${PREFIX} -DBUILD_SHARED_LIBS=OFF ../../libftdi
fi
make
make install
popd

# Build libdivecomputer
if [ ! -e libdivecomputer/configure ] ; then
	pushd libdivecomputer
	autoreconf -i
	popd
fi

mkdir -p build/libdivecomputer-build-$ARCH
pushd build/libdivecomputer-build-$ARCH
if [ ! -e Makefile ] ; then
	../../libdivecomputer/configure --host=${BUILDCHAIN} --prefix=${PREFIX} --enable-static --disable-shared --enable-logging LDFLAGS=-llog
fi
make
make install
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
