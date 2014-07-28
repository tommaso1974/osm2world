package org.osm2world.core.osm.creation;

import static org.openstreetmap.josm.plugins.graphview.core.data.EmptyTagGroup.EMPTY_TAG_GROUP;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openstreetmap.josm.plugins.graphview.core.data.MapBasedTagGroup;
import org.openstreetmap.josm.plugins.graphview.core.data.TagGroup;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Bound;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.openstreetmap.osmosis.core.task.v0_6.RunnableSource;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
import org.openstreetmap.osmosis.xml.common.CompressionMethod;
import org.openstreetmap.osmosis.xml.v0_6.XmlReader;
import org.osm2world.core.osm.data.OSMData;
import org.osm2world.core.osm.data.OSMElement;
import org.osm2world.core.osm.data.OSMMember;
import org.osm2world.core.osm.data.OSMNode;
import org.osm2world.core.osm.data.OSMRelation;
import org.osm2world.core.osm.data.OSMWay;

/**
 * DataSource providing information from a single .osm file. The file is read
 * during the constructor call, there will be no updates when the file is
 * changed later. This class uses osmosis to read the file.
 */
public class OsmosisReader implements OSMDataReader {

    private boolean complete = false;

    private synchronized boolean isComplete() {
        return complete;
    }

    private synchronized void setCompleteTrue() {
        this.complete = true;
    }

    private List<Bound> bounds = new ArrayList<>();
    private Map<Long, Node> nodesById = new HashMap<>();
    private Map<Long, Way> waysById = new HashMap<>();
    private Map<Long, Relation> relationsById = new HashMap<>();

    private Collection<OSMNode> ownNodes;
    private Collection<OSMWay> ownWays;
    private Collection<OSMRelation> ownRelations;

    private final Sink sinkImplementation = new Sink() {
        public void initialize(Map<String, Object> arg0) {
            /* do nothing */
        }

        public void release() {
            /* do nothing */
        }

        public void complete() {
            setCompleteTrue();
        }

        public void process(EntityContainer entityContainer) {
            Entity entity = entityContainer.getEntity();
            if (entity instanceof Node) {
                nodesById.put(entity.getId(), ((Node) entity));
            } else if (entity instanceof Way) {
                waysById.put(entity.getId(), ((Way) entity));
            } else if (entity instanceof Relation) {
                relationsById.put(entity.getId(), ((Relation) entity));
            } else if (entity instanceof Bound) {
                bounds.add((Bound) entity);
            }
        }
    };

    public OsmosisReader(File file) throws IOException {

        RunnableSource reader = createReaderForFile(file);

        reader.setSink(sinkImplementation);

        Thread readerThread = new Thread(reader);
        readerThread.start();

        while (readerThread.isAlive()) {
            try {
                readerThread.join();
            } catch (InterruptedException e) { /* do nothing */

            }
        }

        if (!isComplete()) {
            throw new IOException("couldn't read from file");
        }

        convertToOwnRepresentation();

    }

    public static final RunnableSource createReaderForFile(File file)
            throws FileNotFoundException {

        boolean pbf = false;
        CompressionMethod compression = CompressionMethod.None;

        if (file.getName().endsWith(".pbf")) {
            pbf = true;
        } else if (file.getName().endsWith(".gz")) {
            compression = CompressionMethod.GZip;
        } else if (file.getName().endsWith(".bz2")) {
            compression = CompressionMethod.BZip2;
        }

        RunnableSource reader;

        if (pbf) {
            reader = new crosby.binary.osmosis.OsmosisReader(
                    new FileInputStream(file));
        } else {
            reader = new XmlReader(file, false, compression);
        }

        return reader;

    }

    private void convertToOwnRepresentation() {

        ownNodes = new ArrayList<>(nodesById.size());
        ownWays = new ArrayList<>(waysById.size());
        ownRelations = new ArrayList<>(relationsById.size());

        Map<Node, OSMNode> nodeMap = new HashMap<>();
        Map<Way, OSMWay> wayMap = new HashMap<>();
        Map<Relation, OSMRelation> relationMap = new HashMap<>();

        for (Node node : nodesById.values()) {

            OSMNode ownNode = new OSMNode(node.getLatitude(), node
                    .getLongitude(), tagGroupForEntity(node), node.getId());

            ownNodes.add(ownNode);
            nodeMap.put(node, ownNode);

        }

        for (Way way : waysById.values()) {

            List<WayNode> origWayNodes = way.getWayNodes();
            List<OSMNode> wayNodes = new ArrayList<OSMNode>(origWayNodes.size());
            for (WayNode origWayNode : origWayNodes) {
                Node origNode = nodesById.get(origWayNode.getNodeId());
                if (origNode != null) {
                    wayNodes.add(nodeMap.get(origNode));
                }
            }

            OSMWay ownWay = new OSMWay(tagGroupForEntity(way),
                    way.getId(), wayNodes);

            ownWays.add(ownWay);
            wayMap.put(way, ownWay);

        }

        for (Relation relation : relationsById.values()) {

            OSMRelation ownRelation = new OSMRelation(
                    tagGroupForEntity(relation), relation.getId(),
                    relation.getMembers().size());

            ownRelations.add(ownRelation);
            relationMap.put(relation, ownRelation);

        }

        // add relation members
        // (needs to be done *after* creation because relations can be members
        // of other relations)
        for (Relation relation : relationMap.keySet()) {

            OSMRelation ownRelation = relationMap.get(relation);

            for (org.openstreetmap.osmosis.core.domain.v0_6.RelationMember member : relation
                    .getMembers()) {

                OSMElement memberObject = null;
                if (member.getMemberType() == EntityType.Node) {
                    memberObject = nodeMap.get(nodesById.get(member
                            .getMemberId()));
                } else if (member.getMemberType() == EntityType.Way) {
                    memberObject = wayMap.get(waysById
                            .get(member.getMemberId()));
                } else if (member.getMemberType() == EntityType.Relation) {
                    memberObject = relationMap.get(relationsById.get(member
                            .getMemberId()));
                } else {
                    continue;
                }

                if (memberObject != null) {

                    OSMMember ownMember = new OSMMember(member
                            .getMemberRole(), memberObject);

                    ownRelation.relationMembers.add(ownMember);

                }

            }

        }

        // give up references to original collections
        nodesById = null;
        waysById = null;
        relationsById = null;

    }

    private TagGroup tagGroupForEntity(Entity entity) {
        if (entity.getTags().isEmpty()) {
            return EMPTY_TAG_GROUP;
        } else {
            Map<String, String> tagMap = new HashMap<String, String>(entity.getTags().size());
            for (Tag tag : entity.getTags()) {
                tagMap.put(tag.getKey(), tag.getValue());
            }
            return new MapBasedTagGroup(tagMap);
        }
    }

    @Override
    public OSMData getData() {
        return new OSMData(bounds, ownNodes, ownWays, ownRelations);
    }

}
