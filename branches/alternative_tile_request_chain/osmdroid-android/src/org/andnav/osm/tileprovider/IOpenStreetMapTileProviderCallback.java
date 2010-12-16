package org.andnav.osm.tileprovider;

import android.graphics.drawable.Drawable;

public interface IOpenStreetMapTileProviderCallback {

	/**
	 * The map tile request has completed.
	 *
	 * @param aState
	 *            a state object
	 * @param aDrawable
	 *            a drawable
	 */
	void mapTileRequestCompleted(OpenStreetMapTileRequestState aState,
			final Drawable aDrawable);

	/**
	 * The map tile request has failed.
	 *
	 * @param aState
	 *            a state object
	 */
	void mapTileRequestFailed(OpenStreetMapTileRequestState aState);

	/**
	 * Returns true if the network connection should be used, false if not.
	 *
	 * @return true if data connection should be used, false otherwise
	 */
	public boolean useDataConnection();

	public void setNextProvider(TileProviderBase pTileProvider);
}
