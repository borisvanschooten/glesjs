// Copyright (c) 2014 by B.W. van Schooten, info@borisvanschooten.nl
package net.tmtg.glesjs;

import java.util.HashMap;

import android.app.Activity;
import android.content.Context;

import android.view.KeyEvent;
import android.view.MotionEvent;

import java.lang.reflect.*;

/** Generic GameController class. Mostly follows OuyaController at this point.
 * Uses reflection to access OuyaController, so that ouya-sdk.jar can be left
 * out. */
public class GameController {

	public static final int NR_PLAYERS=4;

	// Ouya names, numbers follows html5 gamepad specs
	public static final int AXIS_RS_X = 2;
	public static final int AXIS_RS_Y = 3;
	public static final int AXIS_LS_X = 0;
	public static final int AXIS_LS_Y = 1;
	public static final int AXIS_L2 = 4;
	public static final int AXIS_R2 = 5;

	public static final int BUTTON_O = 0;
	public static final int BUTTON_U = 2;
	public static final int BUTTON_Y = 3;
	public static final int BUTTON_A = 1;
	public static final int BUTTON_L1 = 4;
	public static final int BUTTON_L2 = 6; // simulated using threshold
	public static final int BUTTON_L3 = 10;
	public static final int BUTTON_R1 = 5;
	public static final int BUTTON_R2 = 7; // simulated using threshold
	public static final int BUTTON_R3 = 11;
	public static final int BUTTON_DPAD_UP = 12;
	public static final int BUTTON_DPAD_DOWN = 13;
	public static final int BUTTON_DPAD_LEFT = 14;
	public static final int BUTTON_DPAD_RIGHT = 15;
	public static final int BUTTON_MENU = 16;

	public static float STICK_DEADZONE = 0.25f;
	public static float BUMPER_DEADZONE = 0.5f;

	//public static HashMap ouyatohtml = new HashMap<Integer,Integer>();
	public static HashMap htmltoouya_but = new HashMap<Integer,Integer>();
	public static HashMap htmltoouya_ax = new HashMap<Integer,Integer>();

	// ouya methods
	static Class impl=null;
	static Method _init,keyup,keydown,motionevent,startofframe,
			controllerbyplayer;
	static Method getaxisvalue, getbutton, buttonchanged;

	public static GameController[] controllers = new GameController[NR_PLAYERS];

	boolean [] buttons = new boolean[17];
	float [] axes = new float[6];

	// instance fields
	int player;
	// null = inactive / not connected
	Object implinstance = null;

	public static void init(Activity activity) {
		System.out.println("GameController: Probing ...");
		// init instances
		for (int i=0; i<controllers.length; i++) {
			controllers[i] = new GameController(i);
		}
		// get reflection items
		try {
			impl = Class.forName("tv.ouya.console.api.OuyaController");
		} catch (Exception e) {
			// no implementation
		}
		if (impl==null) return;
		System.out.println("Found Ouya controller");
		// get mapping of ouya codes to html codes
		Field[] declaredFields = GameController.class.getDeclaredFields();
		for (Field field : declaredFields) {
			if (!Modifier.isStatic(field.getModifiers())) continue;
			if (!field.getType().isPrimitive()) continue;
			if (field.getType() != Integer.TYPE
			&&  field.getType() != Float.TYPE) continue;
			try {
				Field ouyafield = impl.getField(field.getName());
				System.out.println("Got field: "+field.getName());
				if (field.getType() == Integer.TYPE) {
					if (field.getName().startsWith("BUTTON")) {
						//ouyatohtml.put(ouyafield.getInt(null),field.getInt(null));
						htmltoouya_but.put(field.getInt(null),ouyafield.getInt(null));
					} else { // AXIS
						//ouyatohtml.put(ouyafield.getFloat(null),field.getFloat(null));
						htmltoouya_ax.put(field.getInt(null),ouyafield.getInt(null));
					}
				} else { // float
					if (field.getName().equals("STICK_DEADZONE")) {
						STICK_DEADZONE = field.getFloat(null);
					}
				}
			} catch (Exception e) { /* skip */ }
		}
		try {
			// prefetch methods for later use
			_init = impl.getMethod("init",new Class[] {Context.class});
			_init.invoke(null,activity);
			keyup = impl.getMethod("onKeyUp",
				new Class[] {Integer.TYPE,KeyEvent.class} );
			keydown = impl.getMethod("onKeyDown",
				new Class[] {Integer.TYPE,KeyEvent.class} );
			motionevent = impl.getMethod("onGenericMotionEvent",
				new Class[] {MotionEvent.class} );
			startofframe = impl.getMethod("startOfFrame",new Class[] {});
			controllerbyplayer = impl.getMethod("getControllerByPlayer",
				new Class[] {Integer.TYPE} );
			// instance methods
			getaxisvalue = impl.getMethod("getAxisValue",
				new Class[] {Integer.TYPE} );
			getbutton = impl.getMethod("getButton",
				new Class[] {Integer.TYPE} );
			buttonchanged = impl.getMethod("buttonChangedThisFrame",
				new Class[] {Integer.TYPE} );
		} catch (Exception e) {
			System.err.println("Could not get OuyaController methods.");
			e.printStackTrace();
			impl = null;
		}
	}

	public static boolean onKeyDown(int keyCode, KeyEvent event) {
		if (impl==null) return false;
		try {
			Object ret = keydown.invoke(null,keyCode,event);
			return ((Boolean)ret).booleanValue();
		} catch (Exception e) { e.printStackTrace(); }
		return false;
	}

	public static boolean onKeyUp(int keyCode, KeyEvent event) {
		if (impl==null) return false;
		try {
			Object ret = keyup.invoke(null,keyCode,event);
			return ((Boolean)ret).booleanValue();
		} catch (Exception e) { e.printStackTrace(); }
		return false;
	}

	public static boolean onGenericMotionEvent(MotionEvent event) {
		if (impl==null) return false;
		try {
			Object ret = motionevent.invoke(null,event);
			return ((Boolean)ret).booleanValue();
		} catch (Exception e) { e.printStackTrace(); }
		return false;
	}

	public static void startOfFrame() {
		if (impl==null) return;
		try {
			startofframe.invoke(null);
		} catch (Throwable t) {
			//http://forums.ouya.tv/discussion/1108/ouyacontroller-startofframe-hitting-a-a-nullpointerexception
			// just ignore the invocationtarget-nullpointer and it works
			//e.printStackTrace(); 
		}
	}


	public static GameController getControllerByPlayer(int player) {
		return controllers[player];
	}


	// instance

	public GameController(int player) {
		this.player = player;
	}


	// try to get ouyacontroller if not already got. This is done on demand
	// because in some cases, getControllerByPlayer returns null.
	private void tryGetController(int player) {
		try {
			implinstance = controllerbyplayer.invoke(null,player);
		} catch (Exception e) { e.printStackTrace(); }
	}


	public float getAxisValue(int axis) {
		tryGetController(player);
		if (implinstance==null) return 0.0f;
		try {
			Object ret = getaxisvalue.invoke(implinstance,axis);
			return ((Float)ret).floatValue();
		} catch (Exception e) { e.printStackTrace(); }
		return 0.0f;

	}
	public boolean getButton(int but) {
		tryGetController(player);
		if (implinstance==null) return false;
		// the menu button can only be detected by buttonChangedThisFrame,
		// because it is signalled by a onKeyDown immediately followed by an
		// onKeyUp.
		try {
			Object ret;
			if (but==BUTTON_MENU) {
				// changed -> pressed
				ret = buttonchanged.invoke(implinstance, but);
			} else {
				ret = getbutton.invoke(implinstance, but);
			}
			return ((Boolean)ret).booleanValue();
		} catch (Exception e) { e.printStackTrace(); }
		return false;
	}

	public boolean isConnected() {
		tryGetController(player);
		return implinstance != null;
	}

	public boolean [] getButtons() {
		// proof of concept implementation
		// optimize using event handling
		for (int i=0; i<buttons.length; i++) {
			if (!htmltoouya_but.containsKey(i)) {
				// 8 and 9 not found
				//System.err.println("Button index not found: "+i);
				continue;
			}
			buttons[i] = getButton(
				((Integer)htmltoouya_but.get(i)).intValue()
			);
		}
		return buttons;
	}
	public float [] getAxes() {
		for (int i=0; i<axes.length; i++) {
			if (!htmltoouya_ax.containsKey(i)) {
				//System.err.println("Axis index not found: "+i);
				continue;
			}
			axes[i] = getAxisValue(
				((Integer)htmltoouya_ax.get(i)).intValue()
			);
		}
		return axes;
	}

}
