// Copyright (c) 2014 by B.W. van Schooten, info@borisvanschooten.nl
package net.tmtg.glesjs;

import android.os.Bundle;

import android.app.Activity;
import android.app.Application;

import android.hardware.*;
import android.view.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;

import android.content.Intent;
import android.net.Uri;

import android.content.Context;
import android.opengl.*;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.util.Log;



public class MainActivity extends Activity {

	static boolean surface_already_created=false;

	MyGLSurfaceView mView=null;
	MyRenderer mRen=null;

	@Override protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		GlesJSUtils.init(this);
		mView = new MyGLSurfaceView(getApplication());
		// Try to hang on to GL context
		mView.setPreserveEGLContextOnPause(true);
		setContentView(mView);
	}

	@Override protected void onPause() {
		super.onPause();
		//mView.setVisibility(View.GONE);
		mView.onPause();
		GlesJSUtils.pauseAudio();
	}

	@Override protected void onResume() {
		super.onResume();
		mView.onResume();
		GlesJSUtils.resumeAudio();
	}

	/*@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus && mView.getVisibility() == View.GONE) {
			 mView.setVisibility(View.VISIBLE);
		}
	}*/

	@Override public boolean onKeyDown(int keyCode, KeyEvent event) {
		return GameController.onKeyDown(keyCode,event);
	}

	@Override public boolean onKeyUp(int keyCode, KeyEvent event) {
		return GameController.onKeyUp(keyCode,event);
	}

	@Override public boolean onGenericMotionEvent(MotionEvent event) {
		return GameController.onGenericMotionEvent(event);
	}



	class MyGLSurfaceView extends GLSurfaceView {
		public MyGLSurfaceView(Context context){
			super(context);
			setEGLContextClientVersion(2);
			// Set the Renderer for drawing on the GLSurfaceView
			mRen = new MyRenderer();
			setRenderer(mRen);
		}

		@Override
		public boolean onTouchEvent(MotionEvent me) {
			// queue event, and handle it from the renderer thread later
			// to avoid concurrency handling
			if (mRen!=null) mRen.queueTouchEvent(me);
			return true;
		}


	}


	class MyRenderer implements GLSurfaceView.Renderer {

		static final int MAXQUEUELEN = 6;
		Object queuelock = new Object();
		MotionEvent [] motionqueue = new MotionEvent [MAXQUEUELEN];
		int motionqueue_len = 0;

		public void queueTouchEvent(MotionEvent ev) {
			synchronized (queuelock) {
				if (motionqueue_len >= MAXQUEUELEN) return;
				motionqueue[motionqueue_len++] = ev;
			}
		}

		static final int MAXTOUCHES=8;
		int [] touchids = new int[MAXTOUCHES];
		double [] touchx = new double[MAXTOUCHES];
		double [] touchy = new double[MAXTOUCHES];
		int touchlen = 0;
		public void handleTouchEvent(MotionEvent me) {
			// XXX We should mask out the pointer id info in some of the
			// action codes.  Since 2.2, we should use getActionMasked for
			// this.
			// use MotionEventCompat to do this?
			int action = me.getAction();
			int ptridx = (action & MotionEvent.ACTION_POINTER_ID_MASK)
						 >> MotionEvent.ACTION_POINTER_ID_SHIFT;
			action &= MotionEvent.ACTION_MASK;
			// Default coord is the current coordinates of an arbitrary active
			// pointer.
			double x = me.getX();
			double y = me.getY();
			// on a multitouch, touches after the first touch are also
			// considered mouse-down flanks.
			boolean press = action == MotionEvent.ACTION_DOWN
						 || action == MotionEvent.ACTION_POINTER_DOWN;
			boolean down = press || action == MotionEvent.ACTION_MOVE;
			// Alcatel pop:
			// down event: 0
			// move event: 2
			// up event: 9.
			// ACTION_UP=1, ACTION_POINTER_UP=6
			//boolean release = (action & ( MotionEvent.ACTION_UP)) != 0;
			// Normal:
			boolean release = action == MotionEvent.ACTION_UP
						   || action == MotionEvent.ACTION_POINTER_UP;
			int ptrid=0;
			try {
				ptrid = me.getPointerId(ptridx);
			} catch (IllegalArgumentException e) {
				// getPointerId sometimes throws pointer index out of range
				// -> ignore
				System.err.println("Failed getting pointer. Ignoring.");
				e.printStackTrace();
				return;
			}
			// pass multitouch coordinates before touch event
			int pointerCount = me.getPointerCount();
			// signal start multitouch info
			GlesJSLib.onMultitouchCoordinates(-1,0,0);
			for (int p = 0; p < pointerCount; p++) {
				try {
					int pid = me.getPointerId(p);
					GlesJSLib.onMultitouchCoordinates(pid,me.getX(p),me.getY(p));
				} catch (IllegalArgumentException e) {
					// getPointerId sometimes throws pointer index out of range
					// -> ignore
					System.err.println("Failed getting pointer. Ignoring.");
					e.printStackTrace();
				}
			}
			// signal end coordinate info, start button info
			//GlesJSLib.onMultitouchCoordinates(-2,0,0);
			// single touch / press-release info
			// !press && !release means move
			GlesJSLib.onTouchEvent(ptrid,x,y,press,release);
			// signal end touch info
			GlesJSLib.onMultitouchCoordinates(-3,0,0);
		}


		public void onDrawFrame(GL10 gl) {
			GameController.startOfFrame();
			// handle events in the render thread
			synchronized (queuelock) {
				for (int i=0; i<motionqueue_len; i++) {
					if (motionqueue[i]!=null) handleTouchEvent(motionqueue[i]);
					motionqueue[i] = null;
				}
				motionqueue_len = 0;
			}
			for (int i=0; i<GameController.NR_PLAYERS; i++) {
				GameController con = GameController.getControllerByPlayer(i);
				if (con.isConnected()) {
					//System.out.println("##################active@@@@@"+i);
					boolean [] buttons = con.getButtons();
					float [] axes = con.getAxes();
					GlesJSLib.onControllerEvent(i,true,buttons,axes);
				} else {
					GlesJSLib.onControllerEvent(i,false,null,null);
				}
				/* FOR TESTING:
				} else if (i==0) {
					boolean [] buttons = new boolean[]
						{true,false,true,false,true,false,true};
					float [] axes = new float[]
						{9,8,7,6,5};
					GlesJSLib.onControllerEvent(i,true,buttons,axes);
				}
				*/
			}
			GlesJSLib.onDrawFrame();
		}

		public void onSurfaceChanged(GL10 gl, int width, int height) {
			GameController.init(MainActivity.this);
			GlesJSLib.onSurfaceChanged(width, height);
			surface_already_created=true;
		}

		public void onSurfaceCreated(GL10 gl, EGLConfig config) {
			if (surface_already_created) {
				// gl context was lost -> we can't handle that yet
				// -> commit suicide
				// TODO generate contextlost event in JS
				System.err.println("GL context lost. Cannot restore. Exiting.");
				System.exit(0);
			}
			// Do nothing.
		}
	}
}
