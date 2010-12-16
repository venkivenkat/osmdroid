// Created by plusminus on 21:46:41 - 25.09.2008
package org.andnav.osm.tileprovider.modules;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import org.andnav.osm.tileprovider.IRegisterReceiver;
import org.andnav.osm.tileprovider.OpenStreetMapTile;
import org.andnav.osm.tileprovider.OpenStreetMapTileRequestState;
import org.andnav.osm.tileprovider.constants.OpenStreetMapTileProviderConstants;
import org.andnav.osm.tileprovider.renderer.IOpenStreetMapRendererInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.os.Environment;

/**
 * Implements a file system cache and provides cached tiles. This functions as a
 * tile provider by serving cached tiles. It also implements an
 * IFilesystemCacheProvider which can be used by other tile providers to
 * register for file system cache access so they can put their tiles in the file
 * system cache.
 *
 * @author Marc Kurtz
 * @author Nicolas Gramlich
 *
 */
//FIXME move the code from this class elsewhere then delete this class
public class OpenStreetMapTileFilesystemProvider extends
		OpenStreetMapTileModuleProviderBase implements
		IFilesystemCacheProvider, OpenStreetMapTileProviderConstants {

	// ===========================================================
	// Constants
	// ===========================================================

	private static final Logger logger = LoggerFactory
			.getLogger(OpenStreetMapTileFilesystemProvider.class);

	// ===========================================================
	// Fields
	// ===========================================================

	/** whether the sdcard is mounted read/write */
	private boolean mSdCardAvailable = true;

	/** keep around to unregister when we're done */
	private final IRegisterReceiver aRegisterReceiver;
	private MyBroadcastReceiver mBroadcastReceiver;

	private final long mMaximumCachedFileAge;

	private final TileWriter mTileWriter;

	// ===========================================================
	// Constructors
	// ===========================================================

	public OpenStreetMapTileFilesystemProvider(
			final IRegisterReceiver aRegisterReceiver) {
		this(aRegisterReceiver, DEFAULT_MAXIMUM_CACHED_FILE_AGE);
	}

	/**
	 * Provides a file system based cache tile provider. Other providers can
	 * register and store data in the cache.
	 *
	 * @param aRegisterReceiver
	 */
	public OpenStreetMapTileFilesystemProvider(
			final IRegisterReceiver aRegisterReceiver, long maximumCachedFileAge) {
		super(NUMBER_OF_TILE_FILESYSTEM_THREADS,
				TILE_FILESYSTEM_MAXIMUM_QUEUE_SIZE, null);

		this.aRegisterReceiver = aRegisterReceiver;
		this.mMaximumCachedFileAge = maximumCachedFileAge;
		this.mBroadcastReceiver = new MyBroadcastReceiver();

		checkSdCard();

		final IntentFilter mediaFilter = new IntentFilter();
		mediaFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
		mediaFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
		mediaFilter.addDataScheme("file");
		aRegisterReceiver.registerReceiver(mBroadcastReceiver, mediaFilter);

		mTileWriter = new TileWriter();
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	// ===========================================================
	// Methods from SuperClass/Interfaces
	// ===========================================================

	@Override
	public boolean getUsesDataConnection() {
		return false;
	}

	@Override
	protected String threadGroupName() {
		return "filesystem";
	}

	@Override
	protected Runnable getTileLoader() {
		return new TileLoader();
	};

	@Override
	public void detach() {
		if (mBroadcastReceiver != null) {
			aRegisterReceiver.unregisterReceiver(mBroadcastReceiver);
			mBroadcastReceiver = null;
		}
		super.detach();
	}

	@Override
	public int getMinimumZoomLevel() {
		return mTileWriter.getMinimumZoomLevel();
	}

	@Override
	public int getMaximumZoomLevel() {
		return mTileWriter.getMaximumZoomLevel();
	}

	private void checkSdCard() {
		final String state = Environment.getExternalStorageState();
		logger.info("sdcard state: " + state);
		mSdCardAvailable = Environment.MEDIA_MOUNTED.equals(state);
		if (DEBUGMODE)
			logger.debug("mSdcardAvailable=" + mSdCardAvailable);
	}

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

	private class TileLoader extends
			OpenStreetMapTileModuleProviderBase.TileLoader {

		/**
		 * The tile loading policy for deciding which file to use... The order
		 * of preferences is... prefer actual tiles over dummy tiles prefer
		 * newest tile over older prefer local tiles over zip prefer zip files
		 * in lexicographic order
		 *
		 * When a dummy tile is generated it may be constructed from coarser
		 * tiles from a lower resolution level.
		 *
		 * aTile a tile to be constructed by the method.
		 */
		@Override
		public Drawable loadTile(final OpenStreetMapTileRequestState aState) {

			OpenStreetMapTile aTile = aState.getMapTile();

			// if there's no sdcard then don't do anything
			if (!mSdCardAvailable) {
				if (DEBUGMODE)
					logger.debug("No sdcard - do nothing for tile: " + aTile);
				return null;
			}

			// Check each registered renderer to see if their file is available
			// and if so, then render the drawable and return the tile
			for (IOpenStreetMapRendererInfo renderInfo : mRenderInfoList) {
				File file = new File(TILE_PATH_BASE,
						renderInfo.getTileRelativeFilenameString(aTile));
				if (file.exists()) {

					// Check to see if file has expired
					final long now = System.currentTimeMillis();
					final long lastModified = file.lastModified();
					boolean fileExpired = lastModified < now
							- mMaximumCachedFileAge;

					if (!fileExpired) {
						// If the file has not expired, then render it and
						// return it!
						Drawable drawable = renderInfo.getDrawable(file
								.getPath());
						return drawable;
					} else {
						// If the file has expired then we don't use it but we
						// update the time-stamp on the file. If another tile
						// provider down the line can provide this tile, then it
						// will replace this file in the file cache and the new
						// tile will be provided. If it cannot, then this
						// request will ultimately fail, and the original file
						// will be provided in the next iteration of this
						// request since the time-stamp is now no longer
						// expired.
						if (fileExpired) {
							file.setLastModified(System.currentTimeMillis());
						}
					}
				}
			}

			// If we get here then there is no file in the file cache
			return null;
		}
	}

	/**
	 * This broadcast receiver will recheck the sd card when the mount/unmount
	 * messages happen
	 *
	 */
	private class MyBroadcastReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(final Context aContext, final Intent aIntent) {

			final String action = aIntent.getAction();
			logger.info("onReceive: " + action);

			checkSdCard();
		}
	}

	private final List<IOpenStreetMapRendererInfo> mRenderInfoList = new LinkedList<IOpenStreetMapRendererInfo>();

	@Override
	public IFilesystemCache registerRendererForFilesystemAccess(
			IOpenStreetMapRendererInfo pRendererInfo, int pMinimumZoomLevel,
			int pMaximumZoomLevel) {

		mTileWriter.addZoomLevel(pMinimumZoomLevel);
		mTileWriter.addZoomLevel(pMaximumZoomLevel);
		mRenderInfoList.add(pRendererInfo);
		return mTileWriter;
	}

	@Override
	public void unregisterRendererForFilesystemAccess(
			IOpenStreetMapRendererInfo pRendererInfo) {
		mRenderInfoList.remove(pRendererInfo);
	}
}
