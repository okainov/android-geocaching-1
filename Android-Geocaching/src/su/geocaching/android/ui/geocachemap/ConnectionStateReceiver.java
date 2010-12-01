package su.geocaching.android.ui.geocachemap;

import su.geocaching.android.application.ApplicationMain;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * used for listen broadcast messages for activities which uses internet
 * 
 * @author Grigory Kalabin. grigory.kalabin@gmail.com
 * @since Nov 22, 2010
 */
public class ConnectionStateReceiver extends BroadcastReceiver {
    private static final String TAG = ConnectionStateReceiver.class.getCanonicalName();

    /*
     * (non-Javadoc)
     * 
     * @see android.content.BroadcastReceiver#onReceive(android.content.Context,
     * android.content.Intent)
     */
    @Override
    public void onReceive(Context context, Intent intent) {
	Log.d(TAG, "Recieved message about internet status");
	ConnectionManager connManager = ((ApplicationMain) context.getApplicationContext()).getConnectionManager();
	if (connManager.isInternetConnected()) {
	    connManager.onInternetFound();
	} else {
	    connManager.onInternetLost();
	}
    }
}
