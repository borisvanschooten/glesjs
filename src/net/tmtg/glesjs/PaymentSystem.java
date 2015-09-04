// Copyright (c) 2014 by B.W. van Schooten, info@borisvanschooten.nl
package net.tmtg.glesjs;

import android.content.Context;

public interface PaymentSystem {

	public String getType();

	public void init(Context source,String secrets);

	public void exit();

	/** Returns false if the request could not be initiated. */
	public boolean requestPayment(String productID);

	/** 1 = receipt exists; 0 = receipt is being retrieved; -1 = receipt
	* does not exist. */
	public int checkReceipt(String productID);

	public String [] getAllReceipts();

	public boolean consumeReceipt(String productID);

	public String[] getProductInfo(String productID);

}
