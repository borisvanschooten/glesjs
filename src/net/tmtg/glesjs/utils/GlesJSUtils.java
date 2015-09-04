package net.tmtg.glesjs.utils;

import java.util.HashMap;

import android.opengl.*;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.app.Activity;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.content.res.AssetManager;
import android.content.res.AssetFileDescriptor;
import java.io.IOException;
import java.io.InputStream;

public class GlesJSUtils {

	public final static int LOAD=0;
	public final static int PLAY=1;
	public final static int PAUSE=2;

	private static SoundPool soundpool=null;

	static Activity act=null;

	static AssetManager assets=null;

	public static void init(Activity activity) {
		act = activity;
		assets = act.getAssets();
	}

	// asset name to MediaPlayer
	private static HashMap<String,MediaPlayer> players = new HashMap<String,MediaPlayer>();

	// js id to stream id
	private static HashMap<Integer,Integer> streams = new HashMap<Integer,Integer>();

	// asset name to sound id
	private static HashMap<String,Integer> sounds = new HashMap<String,Integer>();

	private static void ensureSoundpoolExists() {
		if (soundpool == null) {
			soundpool = new SoundPool(8,AudioManager.STREAM_MUSIC,0);
		}
	}

	private static MediaPlayer checkCreateMusicPlayer(String assetname)
	throws Exception {
		if (players.containsKey(assetname)) {
			return players.get(assetname);
		} else if (sounds.containsKey(assetname)) {
			// is loaded, but is not music
			return null;
		} else {
			// not loaded yet
			AssetFileDescriptor fd = assets.openFd(assetname);
			if (fd.getLength() > 100000) {
				// more than about 1 meg uncompressed -> music
		    	MediaPlayer player = new MediaPlayer();
				player.setAudioStreamType(AudioManager.STREAM_MUSIC);
				player.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());
				player.prepare();
				players.put(assetname,player);
				return player;
			} else {
				return null; // not music
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
				if (checkCreateMusicPlayer(assetname)==null) {
					ensureSoundLoaded(assetname);
				}
			break;
			case PLAY:
				player = checkCreateMusicPlayer(assetname);
				if (player==null) {
					int soundid = ensureSoundLoaded(assetname);
					//int streamid = defineGetStream(id);
					//int streamid = soundpool.play
					int streamid = soundpool.play(soundid,0.9f, 0.9f,
						1 /*priority */,
						loop ? -1 : 0,
						1.0f);
					streams.put(id,streamid);
				} else {
					player.setLooping(loop);
					// must supply start and length, otherwise error -80000000
					player.start();
				}
			break;
			case PAUSE:
				player = checkCreateMusicPlayer(assetname);
				if (player==null) {
					if (streams.containsKey(id)) {
						soundpool.pause(streams.get(id));
					}
				} else {
					player.pause();
				}
			break;
		}
	} catch (Throwable t) {
		System.out.println("handleAudio error: "+t);
	} }

	public static void Test() {
		System.out.println("##############JNI CALLED################");
	}

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

	public static int [] getImageDimensions(String assetname) {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		try {
			//AssetFileDescriptor assetfd = assets.openFd(assetname);
			InputStream assetin = assets.open(assetname);
			//Returns null, sizes are in the options variable
			BitmapFactory.decodeStream(assetin, null, options);
		} catch (IOException e) {
			System.err.println("Java: getImageDimensions: Could not decode bitmap "+assetname);
			return new int[] {0,0};
		}
		//System.out.println("Image:"+assetname+" "+ options.outWidth+" "+options.outHeight);
		return new int[] {options.outWidth,options.outHeight};
	}


}
