// Copyright (c) 2014 by B.W. van Schooten, info@borisvanschooten.nl
package net.tmtg.glesjs;


import android.content.Context;
import java.util.*;
import android.util.Log;

import android.os.Bundle;

import android.widget.Toast;
import java.lang.reflect.*;

import java.io.*;
import java.io.UnsupportedEncodingException;
import org.json.JSONException;
import org.json.JSONObject;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import tv.ouya.console.api.*;
import android.util.Base64;

public class OuyaPaymentSystem implements PaymentSystem {

	private Context context;

	public static final String TAG = "glesjs";

	public final String PRODUCT_PREFIX="_PRODUCT_";

	private PublicKey mPublicKey;

	// milliseconds
	private long last_try_connect=0;
	// milliseconds
	private long last_returned_receipts=0;
	
	private final long connect_delay = 1000*60*10; // 10 minutes

	// used for processing payment callback
	private HashMap<String,String> uniqid_to_product =
		new HashMap<String,String>();
	

	public OuyaPaymentSystem() {}

	public String getType() {
		if (!OuyaFacade.getInstance().isRunningOnOUYAHardware()) return "";
		return "ouya";
	}


	public void init(Context source,String dev_id) {
		System.out.println("OuyaPaymentSystem: init " + dev_id);
		OuyaFacade.getInstance().init(source, dev_id);
		context=source;
		getPublicKey();
		// try to refresh receipt list on every startup
		tryRequestReceipts();
	}


	public void exit() {
		OuyaFacade.getInstance().shutdown();
	}



	/** Request a payment.  The response will be given at some later time. Use
	 * checkReceipt to see if it's arrived.  RequestPayment will only be
	 * executed if checkReceipt returns -1.
	 * @return true if attempt successful (checkReceipt returns -1)*/
	public boolean requestPayment(String product_id) { try {
		System.out.println("OuyaPaymentSystem: requestPayment: " + product_id);
		if (checkReceipt(product_id)!=-1) return false;
		SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");

		// This is an ID that allows you to associate a successful purchase with
		// its original request. The server does nothing with this string except
		// pass it back to you, so it only needs to be unique within this instance
		// of your app to allow you to pair responses with requests.
		String uniqueId = Long.toHexString(sr.nextLong());
		uniqid_to_product.put(uniqueId,product_id);

		JSONObject purchaseRequest = new JSONObject();
		purchaseRequest.put("uuid", uniqueId);
		purchaseRequest.put("identifier", product_id);
		// This value is only needed for testing, not setting it results in a live purchase
		//purchaseRequest.put("testing", "true"); 
		String purchaseRequestJson = purchaseRequest.toString();

		byte[] keyBytes = new byte[16];
		sr.nextBytes(keyBytes);
		SecretKey key = new SecretKeySpec(keyBytes, "AES");

		byte[] ivBytes = new byte[16];
		sr.nextBytes(ivBytes);
		IvParameterSpec iv = new IvParameterSpec(ivBytes);

		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "BC");
		cipher.init(Cipher.ENCRYPT_MODE, key, iv);
		byte[] payload = cipher.doFinal(purchaseRequestJson.getBytes("UTF-8"));

		cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding", "BC");
		cipher.init(Cipher.ENCRYPT_MODE, mPublicKey);
		byte[] encryptedKey = cipher.doFinal(keyBytes);

		Purchasable purchasable =
				new Purchasable(
						product_id,
						Base64.encodeToString(encryptedKey, Base64.NO_WRAP),
						Base64.encodeToString(ivBytes, Base64.NO_WRAP),
						Base64.encodeToString(payload, Base64.NO_WRAP) );

		OuyaFacade.getInstance().requestPurchase(purchasable,
		new OuyaResponseListener<String>() {
			@Override
			public void onSuccess(String response_str) {
				try {
					OuyaEncryptionHelper helper = new OuyaEncryptionHelper();
					JSONObject response = new JSONObject(response_str);
					String uniqueId = helper.decryptPurchaseResponse(response,
							mPublicKey);
					Log.d(TAG, "Product purchased: " + uniqueId);
					// indicate that a receipt was successfully updated
					// to prevent another call to retrieve all receipts
					String product_id = uniqid_to_product.get(uniqueId);
					if (product_id!=null) {
						GlesJSUtils.storeSetString(PRODUCT_PREFIX+product_id,"1");
					} else {
						Log.e(TAG,"Could not associate ID with product");
					}
					last_returned_receipts = System.currentTimeMillis();
					Toast.makeText(context,"Purchase successful!",
						Toast.LENGTH_LONG).show();
				} catch (Exception e) {
					Log.e(TAG, "Purchase failed.", e);
					Toast.makeText(context,"Purchase failed.",
						Toast.LENGTH_LONG).show();
				}

			}

			@Override
			public void onFailure(int errorCode, String errorMessage,
			Bundle optionalData) {
				Toast.makeText(context,"Purchase failed.",
					Toast.LENGTH_LONG).show();
				Log.d(TAG, "Error:"+errorMessage);
			}
			@Override
			public void onCancel() {
				Toast.makeText(context,"Purchase cancelled.",
					Toast.LENGTH_LONG).show();
			}
		});
		return true;
	} catch (Exception e) {
		//GeneralSecurityException,UnsupportedEncodingException,JSONException
		Log.e(TAG,"Error requesting payment", e);
		return false;
	} }


	/** Check if a payment has been completed or a receipt can be found of a
	 * previous payment. If payment is completed or a receipt is retrieved,
	 * the result is cached in the ouya store, to prevent the dreaded "phone
	 * home" DRM.
	 * Note that retrieval of the receipt, if necessary, is triggered by a
	 * call to this function.  This means the function will at first return 0.
	 * Only after a time it will return 1 or -1.  If called multiple times, it
	 * will periodically check for new receipts.
	 * @return -1 means that no receipt exists
	 *          0 means that receipt is being retrieved or no internet
	 *          1 means that a receipt exists */
	public int checkReceipt(String product_id) {
		//System.out.println("PAYMENT JNI checkreceipt: " + product_id);
		String status = GlesJSUtils.storeGetString(PRODUCT_PREFIX+product_id);
		if (status==null) {
			// Receipt not present or not known.
			// Try to retrieve it when timeout for previous try has expired
			long timestamp = System.currentTimeMillis();
			if (timestamp - last_try_connect > connect_delay) {
				tryRequestReceipts();
			}
			if (last_returned_receipts - last_try_connect < 0 /* still trying*/
			&&  timestamp - last_try_connect <= connect_delay) {
				// no receipt found a few minutes ago
				return 0;
			} else {
				// no receipt found, not even after awhile
				return -1;
			}
		} else {
			// currently always 1
			return Integer.parseInt(status);
		}
	}


	public String [] getAllReceipts() {
		// maybe trigger a load receipts at some point?
		return getAllCachedReceipts();
	}


	public boolean consumeReceipt(String productID) { return false; }


	public String[] getProductInfo(String productID) { return null; }



	private void tryRequestReceipts() {
		Log.d(TAG,"OuyaPaymentSystem: Requesting receipts ...");
		long timestamp = System.currentTimeMillis();
		OuyaFacade.getInstance().requestReceipts(new MyReceiptListener());
		last_try_connect=timestamp;
	}


	private String [] getAllCachedReceipts() {
		Map<String,?> receipts = GlesJSUtils.storeGetAll();
		ArrayList ret = new ArrayList();
		for (String rec : receipts.keySet()) {
			// check if it's a product
			if (rec.startsWith(GlesJSUtils.STORE_PREFIX+PRODUCT_PREFIX)){
				ret.add(rec);
			}
		}
		return (String[])ret.toArray(new String[] {});
	}




	class MyReceiptListener extends
	CancelIgnoringOuyaResponseListener<String> {
		//String product_id;
		//MyReceiptListener(String product_id) {
		//	this.product_id = product_id;
		//}
		@Override
		public void onSuccess(String receiptResponse) {
			OuyaEncryptionHelper helper = new OuyaEncryptionHelper();
			List<Receipt> receipts = null;
			try {
				JSONObject response = new JSONObject(receiptResponse);
				receipts = helper.decryptReceiptResponse(response,
					mPublicKey);
			} catch (Exception e) {
				Log.e(TAG,"checkReceipt error",e);
				//throw new RuntimeException(e);
				return;
			}
			Log.d(TAG,"OuyaPaymentSystem: Updating receipts ...");
			// We've got a list of all the user's receipts
			// remove all old cached receipts
			String [] oldreceipts = getAllCachedReceipts();
			for (String oldrec : oldreceipts) {
				System.out.println("OuyaPaymentSystem: Removing old receipt "+oldrec);
				GlesJSUtils.storeRemove(oldrec.substring(
					GlesJSUtils.STORE_PREFIX.length()) );
			}
			// write "1" status for any products found, this includes the
			// product we requested but also any other products
			// NOTE: hackers who hack the store can put a "1" here manually.
			// In a secure version, the value should be an encrypted value
			// that's different for each machine.
			for (Receipt r : receipts) {
				GlesJSUtils.storeSetString(PRODUCT_PREFIX+r.getIdentifier(),"1");
				Log.d(TAG, "OuyaPaymentSystem: Got receipt "
					+r.getIdentifier());
			}
			// indicate that receipts were successfully returned
			last_returned_receipts = System.currentTimeMillis();
		}

		@Override
		public void onFailure(int errorCode, String errorMessage,
		Bundle errorBundle) {
			Log.e("Error handling receipts", errorMessage);
		}
	}



	private void getPublicKey() {
		// Create a PublicKey object from the key data downloaded from the developer portal.
		try {
			// Read in the key.der file (downloaded from the developer portal)
			InputStream inputStream = context.getAssets().open("ouyakey.der");
			byte[] applicationKey = new byte[inputStream.available()];
			inputStream.read(applicationKey);
			inputStream.close();
			// Create a public key
			X509EncodedKeySpec keySpec = new X509EncodedKeySpec(applicationKey);
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			mPublicKey = keyFactory.generatePublic(keySpec);
		} catch (Exception e) {
			Log.e(TAG, "Unable to create encryption key", e);
		}
	}


}
