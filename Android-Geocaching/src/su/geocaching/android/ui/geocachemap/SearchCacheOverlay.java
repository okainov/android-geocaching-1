package su.geocaching.android.ui.geocachemap;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import su.geocaching.android.model.datatype.GeoCache;
import su.geocaching.android.utils.UiHelper;
import android.content.Context;
import android.graphics.drawable.Drawable;

import com.google.android.maps.MapView;
import com.google.android.maps.OverlayItem;

/**
 * @author Android-Geocaching.su student project team
 * @since October 2010 GeoCache Itemized Overlay for one or more caches
 */
public class SearchCacheOverlay extends com.google.android.maps.ItemizedOverlay<OverlayItem> {
	private List<GeoCacheOverlayItem> items;
	private Context context;

	public SearchCacheOverlay(Drawable defaultMarker, Context context) {
		super(defaultMarker);
		items = Collections.synchronizedList(new LinkedList<GeoCacheOverlayItem>());
		this.context = context;
		populate();
	}

	public synchronized void addOverlayItem(GeoCacheOverlayItem overlay) {
		if (!contains(overlay.getGeoCache()) || overlay.getTitle().equals("Group")) {
			items.add(overlay);
			setLastFocusedIndex(-1);
			populate();
		}
	}

	private boolean contains(GeoCache geoCache) {
		for (GeoCacheOverlayItem item : items) {
			if (item.getGeoCache().equals(geoCache)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected OverlayItem createItem(int i) {
		return items.get(i);
	}

	@Override
	public int size() {
		return items.size();
	}

	public synchronized void clear() {
		items.clear();
		setLastFocusedIndex(-1);
		populate();
	}

	@Override
	public void draw(android.graphics.Canvas canvas, MapView mapView, boolean shadow) {
		super.draw(canvas, mapView, false);
	}

	@Override
	public boolean onTap(int index) {
		UiHelper.showGeoCacheInfo(context, items.get(index).getGeoCache());
		return true;
	}

}
