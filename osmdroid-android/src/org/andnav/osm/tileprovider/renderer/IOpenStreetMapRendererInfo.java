package org.andnav.osm.tileprovider.renderer;

import java.io.InputStream;

import org.andnav.osm.ResourceProxy;
import org.andnav.osm.tileprovider.CloudmadeException;
import org.andnav.osm.tileprovider.IOpenStreetMapTileProviderCallback;
import org.andnav.osm.tileprovider.IOpenStreetMapTileProviderCloudmadeTokenCallback;
import org.andnav.osm.tileprovider.OpenStreetMapTile;

import android.graphics.drawable.Drawable;

public interface IOpenStreetMapRendererInfo {

	int ordinal();
	String name();
	String localizedName(ResourceProxy proxy);
	int maptileSizePx();
	int maptileZoom();
	int zoomMinLevel();
	int zoomMaxLevel();
	String getTileRelativeFilenameString(OpenStreetMapTile aTile);
	String getTileURLString(OpenStreetMapTile aTile, IOpenStreetMapTileProviderCloudmadeTokenCallback aCloudmadeTokenCallback) throws CloudmadeException;
	Drawable getDrawable(String aFilePath);
	Drawable getDrawable(InputStream aTileInputStream);
	void setCloudmadeStyle(int styleId);
}
