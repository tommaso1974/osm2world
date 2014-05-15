package org.osm2world.core.osm.creation;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;
import org.junit.Test;
import org.osm2world.core.osm.creation.OsmosisReader;
import org.osm2world.core.osm.data.OSMData;
import org.osm2world.core.osm.data.OSMNode;
import org.osm2world.core.osm.data.OSMRelation;


public class OsmosisReaderTest {
	
	@Test
	public void testValidFile() throws IOException {
		
		File testFile = new File("test"+File.separator+"files"
				+File.separator+"validFile.osm");
		OSMData osmData = new OsmosisReader(testFile).getData();
		
		assertSame(4, osmData.getNodes().size());
		assertSame(1, osmData.getWays().size());
		assertSame(1, osmData.getRelations().size());
		
		List<OSMNode> wayNodes = osmData.getWays().iterator().next().nodes;
		assertSame(3, wayNodes.size());
		
		assertEquals("traffic_signals", wayNodes.get(1).tags.getValue("highway"));
		
		OSMRelation relation = osmData.getRelations().iterator().next();		
		assertEquals("associatedStreet", relation.tags.getValue("type"));
		
	}

}
