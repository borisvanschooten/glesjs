## GLES.JS - Fast and small Android WebGL bindings for V8

Copyright (c) 2015 by Boris van Schooten  tmtg.net  boris@13thmonkey.org
Released under revised BSD license. See LICENSE for details.

Gles.js provides Android WebGL bindings for the Javascript V8 engine (ARMv7
architecture).  WebGL is directly translated to OpenGL ES, providing what is
probably the fastest and smallest engine for running HTML5 games currently
available on Android.  APK footprint is about 1.5 Mb.

A minimalist HTML5 API emulation is provided.  While only a single Canvas
element is fully emulated, there is limited support for handling HTML and
XML structures and a fake API for the most common things that HTML5 apps do.

### Workflow

1. put your webpage and all resources, like scripts and images, in assets/.
   Gles.js expects index.html to be present, from which the rest is loaded.
   html5.js must be present in assets/, which is the bootloader for
   everything else.

2. compile using the standard Android SDK method, 'ant release'.  This will
   package everything into an APK, which is found in bin/.

   If you compile it for the first time, you have to use the following
   command to create the build template:
	
   `android update project --name GlesJSDemo --target android-14 --path .`


### Setting up (command line)

This assumes you are using the command line to build. To set it up, install
Apache Ant and the Android SDK. Within the Android SDK package manager,
install API level 14 (which is the minimum that guarantees ARMv7
architecture).  Then, make sure that the following is in your PATH:

```
    [ANT_DIR]/bin
    [ANDROIDSDK_DIR]/platform-tools
    [ANDROIDSDK_DIR]/tools
```

Also define the following variable:

```
    ANDROID_HOME=[ANDROIDSDKROOT]
```

Once you've done this, you should be able to execute step (1) and (2) above.


This procecure assumes you just use the compiled object file, libglesjs.so.
If you want to tinker with gles.js itself, you will need the Android NDK as
well. Download/unzip the NDK (no install is required). Make sure your PATH
points to the NDK root dir. 

### Directory structure

```
/            - Standard Android build files
res/         - Standard Android resources
assets/      - HTML5 bootloader (html5.js) and your webpage resources
src/         - Java classes
jni/         - Main source (main.cpp) and makefiles
jni/include/ - Static include files
jni/gluegen/ - Generated OpenGL ES bindings and definitions used by main.cpp
jni/lib/     - Precompiled V8 linkables
libs/        - Precompiled libs used by SDK, including libglesjs
```

### The full compile process (in case you want to modify gles.js)

1. Patch and build V8.  This is quite a mess, and currently not included in
   this package, so you'll have to do with the precompiled V8 binaries in
   jni/lib/ for now.

2. Generate the OpenGL ES bindings by running `php jni/gluegen/gluegen.php'.
   Yes, PHP is a very useful command-line string processing language, and
   is used here for the string processing involved in parsing the OpenGL
   header file and generating the bindings files. 

3. Run ndk-build to build libs/armeabi/libglesjs.so

4. Run ant release (this is normally the only step) to compile the Java
   classes and put everything in an APK.


Depending on how deep you want to dig with development, you can do just the
later steps, using the precompiled and pregenerated stuff already included in
the package.


### Features

- HTML and XML structure manipulation

- OUYA support (controller and payment)

- Multitouch support

- Audio element support.  Web audio API NOT supported. For just playing
  samples, the Audio element is actually more convenient. Also, JS apps need
  to support Audio element anyway, in order to be compatible with IE.

- HTML5 gamepad support.  Currently implemented for OUYA only.

- Features a simple JS payment API of my own devising, which should be
  conceptually compatible with multiple payment systems. Only OUYA
  implementation is currently provided.
  See src/net/tmtg/glesjs/PaymentSystem.java for more info.

- runs most Pixi demos, some after minor hacking of the html file

- runs jgame.js (for me, this is its main purpose)


Known issues:

- There is a bug which sometimes produces spurious mouse coordinates

- Pixi XML fonts don't work yet

- Some OpenGL ES functions are not yet implemented, in particular some of the
  delete and free functions, and a couple of complex functions for which no
  test code is available yet. Generally, OpenGL functions need to be tested
  with more test code.

- There are some minor differences between WebGL and individual
  implementations of OpenGL ES.  Some of these differences are just bugs.
  WebGL as featured in Chrome tries to emulate correct WebGL behaviour for
  known differences. In contrast, gles.js is designed to work without
  emulation layer.  This means you will have to test your app on more
  devices to make sure it works everywhere.

