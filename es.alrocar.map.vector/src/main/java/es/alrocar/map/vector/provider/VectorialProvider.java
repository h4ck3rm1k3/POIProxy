/* Copyright (C) 2011 Alberto Romeu Carrasco
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,USA.
 *
 *   
 *   author: Alberto Romeu Carrasco (alberto@alrocar.es)
 */

package es.alrocar.map.vector.provider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import es.alrocar.map.vector.provider.driver.ProviderDriver;
import es.alrocar.map.vector.provider.observer.VectorialProviderListener;
import es.alrocar.map.vector.provider.strategy.IVectorProviderStrategy;
import es.prodevelop.gvsig.mini.geom.Extent;
import es.prodevelop.gvsig.mini.utiles.Cancellable;
import es.prodevelop.tilecache.renderer.MapRenderer;

/**
 * 
 * @author albertoromeu
 * 
 */
public class VectorialProvider implements VectorialProviderListener {

	private MapRenderer renderer;
	private IVectorProviderStrategy strategy;
	private ProviderDriver driver;
	private VectorTileCache cache;
	private VectorialProviderListener observer;
	private int lastZoomLevel = -1;
	private Cancellable currentCancellable;
	public HashSet<String> mPending = new HashSet<String>();
	private ExecutorService executor = Executors.newCachedThreadPool();

	public VectorialProvider(MapRenderer renderer,
			IVectorProviderStrategy strategy, ProviderDriver driver,
			VectorialProviderListener observer) {
		this.renderer = renderer;
		this.strategy = strategy;
		this.strategy.setProvider(this);
		cache = new VectorTileCache(VectorTileCache.DEFAULT_CACHE_SIZE * 2);
		this.observer = observer;
		this.driver = driver;
		this.driver.setProvider(this);
	}

	public void getDataAsynch(final int[][] tiles, final int zoomLevel,
			final Extent viewExtent, final Cancellable cancellable) {
		try {
			if (zoomLevel < 2)
				return;
//			WorkQueue.getExclusiveInstance().clearPendingTasks();
			executor.execute(new Runnable() {

				public void run() {
					if (lastZoomLevel != zoomLevel) {
						lastZoomLevel = zoomLevel;
						if (currentCancellable != null) {
							currentCancellable.setCanceled(true);
						}
						// cache.mCachedTiles.clear();
						observer.onVectorDataRetrieved(null, null, null,
								zoomLevel);
					}

					currentCancellable = cancellable;

					ArrayList tilesToRetrieve = new ArrayList();
					final int size = tiles.length;

					boolean found = false;
					for (int i = 0; i < size; i++) {
						if (!mPending.contains(format(tiles[i]))) {
							mPending.add(format(tiles[i]));
							found = false;
							if (tiles[i] != null) {
								ArrayList values = cache.getTile(format(tiles[i]));
								if (values == null) {
									values = driver.getFileSystemProvider()
											.load(tiles[i], zoomLevel,
													driver.getName(),
													cancellable);
									if (values == null) {
										tilesToRetrieve.add(tiles[i]);
									} else {
										found = true;
									}
								} else {
									found = true;
								}

								if (found) {
									mPending.remove(format(tiles[i]));
									observer.onVectorDataRetrieved(tiles[i],
											values, cancellable, zoomLevel);
								}
							}
						}
					}
					
					final int length = tilesToRetrieve.size();
					if (length > 0) {
						int[][] t = new int[length][2];

						for (int i = 0; i < length; i++) {
							t[i] = (int[]) tilesToRetrieve.get(i);
						}
						strategy.getVectorialData(t, zoomLevel, viewExtent,
								VectorialProvider.this, cancellable);
					}
				}
			});

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public MapRenderer getRenderer() {
		return this.renderer;
	}

	public ProviderDriver getDriver() {
		return this.driver;
	}

	public void onVectorDataRetrieved(int[] tile, ArrayList data,
			Cancellable cancellable, int zoomLevel) {
		try {
			if (data != null) {
				mPending.remove(format(tile));
				cache.putTile(format(tile), data);

				observer.onVectorDataRetrieved(tile, data, cancellable,
						zoomLevel);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public String format(int[] tile) {
		return new StringBuffer().append(tile[0]).append("_").append(tile[1])
				.toString();
	}

	public void onRawDataRetrieved(int[] tile, Object data,
			Cancellable cancellable, ProviderDriver driver, int zoomLevel) {
		if (driver.getFileSystemProvider() != null) {
			if (cancellable != null && cancellable.getCanceled())
				return;
			driver.getFileSystemProvider().save(tile, zoomLevel,
					driver.getName(), cancellable, data);
		}

	}
}
