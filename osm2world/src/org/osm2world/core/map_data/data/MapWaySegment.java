package org.osm2world.core.map_data.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.openstreetmap.josm.plugins.graphview.core.data.TagGroup;
import org.osm2world.core.map_data.data.overlaps.MapIntersectionWW;
import org.osm2world.core.map_data.data.overlaps.MapOverlap;
import org.osm2world.core.math.AxisAlignedBoundingBoxXZ;
import org.osm2world.core.osm.data.OSMWay;
import org.osm2world.core.world.data.WaySegmentWorldObject;

import com.google.common.collect.Iterables;

/**
 * line between two nodes in the map data
 */
public class MapWaySegment extends MapSegment implements MapElement {

	private final OSMWay osmWay;
	
	private List<WaySegmentWorldObject> representations = new ArrayList<WaySegmentWorldObject>(1);
	
	@SuppressWarnings("unchecked") //is later checked for EMPTY_LIST using ==
	private Collection<MapOverlap<?,?>> overlaps = Collections.EMPTY_LIST;
	
	public MapWaySegment(OSMWay osmWay, MapNode startNode, MapNode endNode) {
		super(startNode, endNode);
		this.osmWay = osmWay;
	}
	
	@Override
	public int getLayer() {
		if (osmWay.tags.containsKey("layer")) {
			try {
				return Integer.parseInt(osmWay.tags.getValue("layer"));
			} catch (NumberFormatException nfe) {
				return 0;
			}
		}
		return 0;
	}
	
	public OSMWay getOsmWay() {
		return osmWay;
	}

	@Override
	public TagGroup getTags() {
		return getOsmWay().tags;
	}
	
	public void addOverlap(MapOverlap<?, ?> overlap) {
		assert overlap.e1 == this || overlap.e2 == this;
		if (overlaps == Collections.EMPTY_LIST) {
			overlaps = new ArrayList<MapOverlap<?,?>>();
		}
		overlaps.add(overlap);
	}
	
	@Override
	public Collection<MapOverlap<?,?>> getOverlaps() {
		return overlaps;
	}
	
	public Iterable<MapIntersectionWW> getIntersectionsWW() {
		return Iterables.filter(overlaps, MapIntersectionWW.class);
	}
	
	@Override
	public AxisAlignedBoundingBoxXZ getAxisAlignedBoundingBoxXZ() {
		return new AxisAlignedBoundingBoxXZ(Arrays.asList(
				startNode.getPos(), endNode.getPos()));
	}
	
	@Override
	public List<WaySegmentWorldObject> getRepresentations() {
		return representations;
	}
	
	@Override
	public WaySegmentWorldObject getPrimaryRepresentation() {
		if (representations.isEmpty()) {
			return null;
		} else {
			return representations.get(0);
		}
	}

	/**
	 * adds a visual representation for this way segment
	 */
	public void addRepresentation(WaySegmentWorldObject representation) {
		this.representations.add(representation);
	}
	
	@Override
	public String toString() {
		return startNode + "->" + endNode;
	}
	
}
