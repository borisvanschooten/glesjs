// Copyright (c) 2014 by B.W. van Schooten, info@borisvanschooten.nl
package net.tmtg.glesjs;

import java.util.*;
import android.util.Log;
import java.lang.reflect.*;

import android.opengl.*;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import android.preference.*;
import android.content.SharedPreferences;

import android.app.Activity;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.content.res.AssetManager;
import android.content.res.AssetFileDescriptor;
import java.io.IOException;
import java.io.InputStream;


/** Functions that the glesjs native library uses.
*/
public class GlesJSUtils {

	public final static int LOAD=0;
	public final static int PLAY=1;
	public final static int PAUSE=2;

	private static SoundPool soundpool=null;

	static Activity act=null;

	static AssetManager assets=null;

	static PaymentSystem payment=null;

	public static void init(Activity activity) {
		act = activity;
		assets = act.getAssets();
		getPaymentSystem();
	}

	private static void getPaymentSystem() {
		try {
			Class impl = Class.forName("tv.ouya.console.api.OuyaFacade");
			Method getinstance = impl.getMethod("getInstance", new Class[] {} );
			Object instance = getinstance.invoke(null);
			Method check = instance.getClass()
				.getMethod("isRunningOnOUYAHardware", new Class[] {} );
			Object isouya = check.invoke(instance);
			if (!((Boolean)isouya).booleanValue()) return;
			// we got an ouya
			Class system = Class.forName("net.tmtg.glesjs.OuyaPaymentSystem");
			payment = (PaymentSystem) system.newInstance();
			System.out.println("Got Ouya payment system");
		} catch (Exception e) {
			// no payment system
			System.out.println("Error getting payment system");
			e.printStackTrace();
		}
	}


	// -----------------------------------------------
	// PAYMENT
	// -----------------------------------------------

	public static String paymentGetType() {
		if (payment==null) return "";
		return payment.getType();
	}

	public static void paymentInit(String secrets) {
		if (payment==null) return;
		payment.init(act,secrets);
	}

	public static void paymentExit() {
		if (payment==null) return;
		payment.exit(); 
	}

	public static boolean paymentRequestPayment(String productID) {
		if (payment==null) return false;
		return payment.requestPayment(productID);
	}

	public static int paymentCheckReceipt(String productID) {
		if (payment==null) return -1;
		return payment.checkReceipt(productID);
	}

	public static String [] paymentGetAllReceipts() {
		if (payment==null) return null;
		return payment.getAllReceipts();
	}

	public static boolean paymentConsumeReceipt(String productID) {
		if (payment==null) return false;
		return payment.consumeReceipt(productID);
	}

	public static String[] paymentGetProductInfo(String productID) {
		if (payment==null) return null;
		return payment.getProductInfo(productID);
	}



	// -----------------------------------------------
	// STORAGE
	// -----------------------------------------------

	public static final String STORE_PREFIX="GLESJS_";

	public static String storeGetString(String id) {
		//System.out.println("STORE GET: "+id);
		String defaultval = null;
		SharedPreferences sp=PreferenceManager.getDefaultSharedPreferences(act);
		return sp.getString(STORE_PREFIX+id, defaultval);
	}

	public static void storeSetString(String id,String value) {
		System.out.println("STORE PUT: "+id+"="+value);
		SharedPreferences.Editor spe =
			PreferenceManager.getDefaultSharedPreferences(act).edit();
		spe.putString(STORE_PREFIX+id, value);
		spe.commit();
	}


	public static void storeRemove(String id) {
		System.out.println("STORE REMOVE: "+id);
		SharedPreferences.Editor spe =
			PreferenceManager.getDefaultSharedPreferences(act).edit();
		spe.remove(STORE_PREFIX+id);
		spe.commit();
	}

	/** Not exposed to JS, used only to abstract away Ouya vs Android storage,
	 * in case we implement Ouya storage. */
	public static Map<String,?> storeGetAll() {
		SharedPreferences sp=PreferenceManager.getDefaultSharedPreferences(act);
		return sp.getAll();
	}



	// -----------------------------------------------
	// MISC
	// -----------------------------------------------


	public static byte[] readAsset(String assetname) {
		try {
			InputStream is = assets.open(assetname);
			byte [] data = new byte [is.available()];
			is.read(data);
			is.close();
			return data;
		} catch (Throwable t) {
			System.err.println("Error opening asset "+assetname);
			return new byte[0];
		}
	}

	// -----------------------------------------------
	// SOUND
	// -----------------------------------------------


	/* Audio handling has to clean up garbage without receiving destroy events
	* from the encapsulating JS Audio objects. This is done as follows:
	*
	* Looping or long sounds cannot be triggered multiple times. There is
	* always at most one mediaplayer associated with each resourcename. Pause()
	* will stop and delete the mediaplayer.  So, pause/resume will restart the
	* sound.  
	*
	* Nonlooping short sounds are played in the sound pool.  Their streams
	* cannot be referenced again after start, so they cannot be paused.
	*
	* XXX not sure if sound should also stop if reference to Audio object is
	* dropped.  This cannot be implemented yet.  Workaround is to pause() it
	* explicitly before dropping it.
	*/

	// asset name to MediaPlayer
	private static HashMap<String,MediaPlayer> players = new HashMap<String,MediaPlayer>();
	private static HashMap<String,Integer> playerids = new HashMap<String,Integer>();

	// js id to stream id
	//private static HashMap<Integer,Integer> streams = new HashMap<Integer,Integer>();

	// asset name to sound id
	private static HashMap<String,Integer> sounds = new HashMap<String,Integer>();


	public static void pauseAudio() {
		if (soundpool!=null) soundpool.autoPause();
		for (MediaPlayer player: players.values()) {
			try {
				player.pause();
			} catch (IllegalStateException e) { /* ignore */ }
		}
	}

	public static void resumeAudio() {
		if (soundpool!=null) soundpool.autoResume();
		for (MediaPlayer player: players.values()) {
			try {
				player.start();
			} catch (IllegalStateException e) { /* ignore */ }
		}
	}

	

	private static void ensureSoundpoolExists() {
		if (soundpool == null) {
			soundpool = new SoundPool(8,AudioManager.STREAM_MUSIC,0);
		}
	}

	/** Creates or gets a mediaplayer when appropriate.  This is when:
	* (1) sound is too long for playing in sound pool, OR
	* (2) sound is looping.
	* Only one mediaplayer exists for each asset.  If the asset already
	* has an associated mediaplayer, it is returned, or if the id is
	* different, it is removed and a new one created.
	*/
	private static MediaPlayer checkCreateMusicPlayer(String assetname,
	boolean loop, int id) throws Exception {
		if (players.containsKey(assetname)) {
			// old player found. Check if remove or keep.
			int oldid = playerids.get(assetname);
			if (oldid != id) {
				players.remove(assetname);
				playerids.remove(assetname);
			} else {
				return players.get(assetname);
			}
		}
		// no appropriate old player exists
		if (!loop && sounds.containsKey(assetname)) {
			// is nonlooping and already loaded in sound pool
			return null;
		} else {
			// not loaded yet
			AssetFileDescriptor fd = assets.openFd(assetname);
			if (loop || fd.getLength() > 100000) {
				// looping or more than about 1 meg uncompressed
				// -> use mediaplayer
		    	MediaPlayer player = new MediaPlayer();
				player.setAudioStreamType(AudioManager.STREAM_MUSIC);
				player.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());
				player.prepare();
				players.put(assetname,player);
				playerids.put(assetname,id);
				return player;
			} else {
				return null; // not loaded, not suitable for mediaplayer
			}
		}
	}

	private static int ensureSoundLoaded(String assetname)
	throws Exception {
		ensureSoundpoolExists();
		if (sounds.containsKey(assetname)) {
			return sounds.get(assetname);
		}
		try {
			AssetFileDescriptor fd = assets.openFd(assetname);
			int soundid = soundpool.load(fd, 1/*priority*/);
			sounds.put(assetname,soundid);
			return soundid;
		} catch (IOException e) {
			throw new Exception("Cannot open asset "+assetname);
		}
	}

	// open
	private int defineGetStream(int jsid) {
		return 0;
	}

	public static void handleAudio(int op,String assetname,boolean loop,
	int id) { try {
		MediaPlayer player=null;
		//System.out.println("### handleAudio "+op+" "+assetname+" "+loop+" "+id);
		switch (op) {
			case LOAD:
				if (checkCreateMusicPlayer(assetname,loop,id)==null) {
					ensureSoundLoaded(assetname);
				}
			break;
			case PLAY:
				player = checkCreateMusicPlayer(assetname,loop,id);
				if (player==null) {
					int soundid = ensureSoundLoaded(assetname);
					//int streamid = defineGetStream(id);
					//int streamid = soundpool.play
					int streamid = soundpool.play(soundid,0.33f, 0.33f,
						1 /*priority */,
						0, /* cannot loop yet */ /*loop ? -1 : 0, */
						1.0f);
					//streams.put(id,streamid);
				} else {
					player.setLooping(loop);
					player.setVolume(0.33f,0.33f);
					// must supply start and length, otherwise error -80000000
					player.start();
				}
			break;
			case PAUSE:
				player = checkCreateMusicPlayer(assetname,loop,id);
				if (player==null) {
					// cannot pause soundpool sounds yet
					//if (streams.containsKey(id)) {
					//	soundpool.pause(streams.get(id));
					//}
				} else {
					// for mediaplayer, pause means stop. Play will play again
					// from the beginning.
					player.pause();
					players.remove(assetname);
					playerids.remove(assetname);
				}
			break;
		}
	} catch (Throwable t) {
		System.out.println("handleAudio error: "+t);
	} }

	public static void Test() {
		System.out.println("##############JNI CALLED################");
	}


	// -----------------------------------------------
	// GL
	// -----------------------------------------------


	// Bitmap can be destroyed after loading into GL.
	// Use bitmap.recycle() to free bitmap resources early

	// target is something like GLES20.GL_TEXTURE_2D
	// level is mipmap level (0=top level)
	// border: 0 = no border, 1 = border (used for GL_CLAMP)
	// formats and type are currently not supported
	public static int[]texImage2D(int target,int level,byte [] data,int border){
		System.out.println("Java: texImage2D: target="+target+" level="+level+" border="+border);
		Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
		int[] dim = new int[] {bitmap.getWidth(),bitmap.getHeight()};
		if (bitmap==null) {
			System.err.println("Java: texImage2D: Could not decode bitmap!");
		} else {
			GLUtils.texImage2D(target, level, bitmap, border);
			bitmap.recycle();
		}
		return dim;
	}

	private static final String DATA_PREFIX = "data:image/png;base64,";

	public static int [] getImageDimensions(String assetname) {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		try {

			if (assetname.startsWith(DATA_PREFIX)) {
				byte[] decodedByte = Base64.decode(assetname.substring(DATA_PREFIX.length()), Base64.DEFAULT);
				BitmapFactory.decodeByteArray(decodedByte, 0, decodedByte.length, options); 
			}
			else {
				//AssetFileDescriptor assetfd = assets.openFd(assetname);
				InputStream assetin = assets.open(assetname);
				//Returns null, sizes are in the options variable
				BitmapFactory.decodeStream(assetin, null, options);
			}
		} catch (IOException e) {
			System.err.println("Java: getImageDimensions: Could not decode bitmap "+assetname);
			return new int[] {0,0};
		}
		//System.out.println("Image:"+assetname+" "+ options.outWidth+" "+options.outHeight);
		return new int[] {options.outWidth,options.outHeight};
	}


}
