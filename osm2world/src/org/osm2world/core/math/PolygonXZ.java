package org.osm2world.core.math;

import static org.osm2world.core.math.GeometryUtil.distanceFromLineSegment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public class PolygonXZ {

    /**
     * polygon vertices; first and last vertex are equal
     */
    protected final List<VectorXZ> vertexLoop;

    /**
     * @param vertexLoop vertices defining the polygon; first and last vertex
     * must be equal
     * @throws InvalidGeometryException if the polygon is self-intersecting or
     * produces invalid area calculation results
     */
    public PolygonXZ(List<VectorXZ> vertexLoop) {

        assertLoopProperty(vertexLoop);

        this.vertexLoop = vertexLoop;

    }

    /**
     * returns the number of vertices in this polygon. The duplicated first/last
     * vertex is <em>not</em> counted twice, so the result is equivalent to
     * {@link #getVertices()}.size().
     */
    public int size() {
        return vertexLoop.size() - 1;
    }

    /**
     * returns the polygon's vertices. Unlike {@link #getVertexLoop()}, there is
     * no duplication of the first/last vertex.
     * @return 
     */
    public List<VectorXZ> getVertices() {
        return vertexLoop.subList(0, vertexLoop.size() - 1);
    }

    /**
     * returns the polygon's vertices. First and last vertex are equal.
     * @return 
     */
    public List<VectorXZ> getVertexLoop() {
        return vertexLoop;
    }

    /**
     * returns a collection that contains all vertices of this polygon at least
     * once. Can be used if you don't care about whether the first/last vector
     * is duplicated.
     * @return 
     */
    public List<VectorXZ> getVertexCollection() {
        return vertexLoop;
    }

    /**
     * returns the vertex at a position in the vertex sequence
     * @param index
     * @return 
     */
    public VectorXZ getVertex(int index) {
        assert 0 <= index && index < vertexLoop.size() - 1;
        return vertexLoop.get(index);
    }

    /**
     * returns the successor of the vertex at a position in the vertex sequence.
     * This wraps around the vertex loop, so the successor of the last vertex is
     * the first vertex.
     * @param index
     * @return 
     */
    public VectorXZ getVertexAfter(int index) {
        assert 0 <= index && index < vertexLoop.size() - 1;
        return getVertex((index + 1) % size());
    }

    /**
     * returns the predecessor of the vertex at a position in the vertex
     * sequence. This wraps around the vertex loop, so the predecessor of the
     * first vertex is the last vertex.
     * @param index
     * @return 
     */
    public VectorXZ getVertexBefore(int index) {
        assert 0 <= index && index < vertexLoop.size() - 1;
        return getVertex((index + size() - 1) % size());
    }

    public List<LineSegmentXZ> getSegments() {
        List<LineSegmentXZ> segments = new ArrayList<>(vertexLoop.size());
        for (int i = 0; i + 1 < vertexLoop.size(); i++) {
            segments.add(new LineSegmentXZ(vertexLoop.get(i), vertexLoop.get(i + 1)));
        }
        return segments;
    }

    /**
     * returns the polygon segment with minimum distance to a given point
     * @param point
     * @return 
     */
    public LineSegmentXZ getClosestSegment(VectorXZ point) {

        LineSegmentXZ closestSegment = null;
        double closestDistance = Double.MAX_VALUE;

        for (LineSegmentXZ segment : getSegments()) {
            double distance = distanceFromLineSegment(point, segment);
            if (distance < closestDistance) {
                closestSegment = segment;
                closestDistance = distance;
            }
        }

        return closestSegment;

    }

    /**
     * returns true if there is an intersection between this polygon and the
     * line segment defined by the parameter
     * @param segmentP1
     * @param segmentP2
     * @return 
     */
    public boolean intersects(VectorXZ segmentP1, VectorXZ segmentP2) {
        //TODO: (performance): passing "vector TO second point", rather than point2, would avoid having to calc it here - and that information could be reused for all comparisons involving the segment

        for (int i = 0; i + 1 < vertexLoop.size(); i++) {

            VectorXZ intersection = GeometryUtil.getTrueLineSegmentIntersection(
                    segmentP1, segmentP2,
                    vertexLoop.get(i), vertexLoop.get(i + 1)
            );

            if (intersection != null) {
                return true;
            }

        }

        return false;
    }

    public boolean intersects(LineSegmentXZ lineSegment) {
        return intersects(lineSegment.p1, lineSegment.p2);
    }

    /**
     * returns true if there is an intersection between this polygon's and the
     * parameter polygon's sides
     * @param outlinePolygonXZ
     * @return 
     */
    public boolean intersects(PolygonXZ outlinePolygonXZ) {

        //TODO (performance): currently performs pairwise intersection checks for sides of this and other - this might not be the fastest method
        for (int i = 0; i + 1 < vertexLoop.size(); i++) {
            if (outlinePolygonXZ.intersects(vertexLoop.get(i), vertexLoop.get(i + 1))) {
                return true;
            }
        }

        return false;
    }

    public Collection<LineSegmentXZ> intersectionSegments(
            LineSegmentXZ lineSegment) {

        List<LineSegmentXZ> intersectionSegments = new ArrayList<>();

        for (LineSegmentXZ polygonSegment : getSegments()) {

            VectorXZ intersection = GeometryUtil.getTrueLineSegmentIntersection(
                    lineSegment.p1, lineSegment.p2,
                    polygonSegment.p1, polygonSegment.p2
            );

            if (intersection != null) {
                intersectionSegments.add(polygonSegment);
            }

        }

        return intersectionSegments;

    }

    public List<VectorXZ> intersectionPositions(
            LineSegmentXZ lineSegment) {

        List<VectorXZ> intersectionPositions = new ArrayList<>();

        for (int i = 0; i + 1 < vertexLoop.size(); i++) {

            VectorXZ intersection = GeometryUtil.getTrueLineSegmentIntersection(
                    lineSegment.p1, lineSegment.p2,
                    vertexLoop.get(i), vertexLoop.get(i + 1)
            );

            if (intersection != null) {
                intersectionPositions.add(intersection);
            }

        }

        return intersectionPositions;

    }

    /**
     * returns whether this polygon is self-intersecting
     * @return 
     */
    public boolean isSelfIntersecting() {
        return isSelfIntersecting(vertexLoop);
    }

    /**
     * returns true if the polygon defined by the polygonVertexLoop parameter is
     * self-intersecting.<br/>
     * The Code is based on Shamos-Hoey's algorithm
     *
     * TODO: if the end vertex of two line segments are the same the polygon is
     * never considered as self intersecting on purpose. This behavior should
     * probably be reconsidered, but currently left as is due to frequent cases
     * of such polygons.
     * @param polygonVertexLoop
     * @return 
     */
    public static boolean isSelfIntersecting(List<VectorXZ> polygonVertexLoop) {

        final class Event {

            boolean start;
            LineSegmentXZ line;

            Event(LineSegmentXZ l, boolean s) {
                this.line = l;
                this.start = s;
            }
        }

        // we have n-1 vertices as the first and last vertex are the same 
        final int segments = polygonVertexLoop.size() - 1;

        // generate an array of input events associated with their line segments
        Event[] events = new Event[segments * 2];
        for (int i = 0; i < segments; i++) {
            VectorXZ v1 = polygonVertexLoop.get(i);
            VectorXZ v2 = polygonVertexLoop.get(i + 1);

            // Create a line where the first vertex is left (or above) the second vertex
            LineSegmentXZ line;
            if ((v1.x < v2.x) || ((v1.x == v2.x) && (v1.z < v2.z))) {
                line = new LineSegmentXZ(v1, v2);
            } else {
                line = new LineSegmentXZ(v2, v1);
            }

            events[2 * i] = new Event(line, true);
            events[2 * i + 1] = new Event(line, false);
        }

        // sort the input events according to the x-coordinate, then z-coordinate
        Arrays.sort(events, new Comparator<Event>() {
            public int compare(Event e1, Event e2) {

                VectorXZ v1 = e1.start ? e1.line.p1 : e1.line.p2;
                VectorXZ v2 = e2.start ? e2.line.p1 : e2.line.p2;

                if (v1.x < v2.x) {
                    return -1;
                } else if (v1.x == v2.x) {
                    if (v1.z < v2.z) {
                        return -1;
                    } else if (v1.z == v2.z) {
                        return 0;
                    }
                }
                return 1;
            }
        });

        // A TreeSet, used for the sweepline algorithm
        TreeSet<LineSegmentXZ> sweepLine = new TreeSet<>(new Comparator<LineSegmentXZ>() {
            @Override
            public int compare(LineSegmentXZ l1, LineSegmentXZ l2) {

                VectorXZ v1 = l1.p1;
                VectorXZ v2 = l2.p1;

                if (v1.z < v2.z) {
                    return -1;
                } else if (v1.z == v2.z) {
                    if (v1.x < v2.x) {
                        return -1;
                    } else if (v1.x == v2.x) {
                        if (l1.p2.z < l2.p2.z) {
                            return -1;
                        } else if (l1.p2.z == l2.p2.z) {
                            if (l1.p2.x < l2.p2.x) {
                                return -1;
                            } else if (l1.p2.x == l2.p2.x) {
                                return 0;
                            }
                        }
                    }
                }
                return 1;
            }
        });

        // start the algorithm by visiting every event
        for (Event event : events) {
            LineSegmentXZ line = event.line;

            if (event.start) { // if it is a startpoint

                LineSegmentXZ lower = sweepLine.lower(line);
                LineSegmentXZ higher = sweepLine.higher(line);

                sweepLine.add(line);

                if (lower != null) {
                    if (lower.intersects(line.p1, line.p2)) {
                        return true;
                    }
                }

                if (higher != null) {
                    if (higher.intersects(line.p1, line.p2)) {
                        return true;
                    }
                }
            } else { // if it is an endpoint

                LineSegmentXZ lower = sweepLine.lower(line);
                LineSegmentXZ higher = sweepLine.higher(line);

                sweepLine.remove(line);

                if ((lower == null) || (higher == null)) {
                    continue;
                }

                if (lower.intersects(higher.p1, higher.p2)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * checks whether this polygon is simple
     * @return 
     */
    public boolean isSimple() {
        try {
            this.asSimplePolygon();
            return true;
        } catch (InvalidGeometryException e) {
            return false;
        }
    }

    /**
     * returns a polygon with the coordinates of this polygon that is an
     * instance of {@link SimplePolygonXZ}. Only works if it actually
     * {@link #isSimple()}!
     */
    public SimplePolygonXZ asSimplePolygon() {
        return new SimplePolygonXZ(vertexLoop);
    }

    /**
     * returns a triangle with the same vertices as this polygon. Requires that
     * the polygon is triangular!
     */
    public TriangleXZ asTriangleXZ() {
        if (vertexLoop.size() != 4) {
            throw new InvalidGeometryException("attempted creation of triangle "
                    + "from polygon with vertex loop of size " + vertexLoop.size()
                    + ": " + vertexLoop);
        } else {
            return new TriangleXZ(
                    vertexLoop.get(0),
                    vertexLoop.get(1),
                    vertexLoop.get(2));
        }
    }

    public PolygonXYZ xyz(final double y) {
        return new PolygonXYZ(VectorXZ.listXYZ(vertexLoop, y));
    }

    public PolygonXZ reverse() {
        List<VectorXZ> newVertexLoop = new ArrayList<>(vertexLoop);
        Collections.reverse(newVertexLoop);
        return new PolygonXZ(newVertexLoop);
    }

    /**
     * returns the average of all vertex coordinates. The result is not
     * necessarily contained by this polygon.
     * @return 
     */
    public VectorXZ getCenter() {
        double x = 0, z = 0;
        int numberVertices = vertexLoop.size() - 1;
        for (VectorXZ vertex : getVertices()) {
            x += vertex.x / numberVertices;
            z += vertex.z / numberVertices;
            /* single division per coordinate after loop would be faster,
             * but might cause numbers to get too large */
        }
        return new VectorXZ(x, z);
    }

    /**
     * returns the length of the polygon's outline. (This does <em>not</em>
     * return the number of vertices, but the sum of distances between
     * subsequent nodes.)
     */
    public double getOutlineLength() {
        double length = 0;
        for (int i = 0; i + 1 < vertexLoop.size(); i++) {
            length += VectorXZ.distance(vertexLoop.get(i), vertexLoop.get(i + 1));
        }
        return length;
    }

    /**
     * returns true if the other polygon has the same vertices in the same
     * order, possibly with a different start vertex
     */
    public boolean isEquivalentTo(PolygonXZ other) {

        if (vertexLoop.size() != other.vertexLoop.size()) {
            return false;
        }

        List<VectorXZ> ownVertices = getVertices();
        List<VectorXZ> otherVertices = other.getVertices();

        for (int offset = 0; offset < ownVertices.size(); offset++) {

            boolean matches = true;

            for (int i = 0; i < ownVertices.size(); i++) {
                int iWithOffset = (i + offset) % ownVertices.size();
                if (!otherVertices.get(i).equals(ownVertices.get(iWithOffset))) {
                    matches = false;
                    break;
                }
            }

            if (matches) {
                return true;
            }

        }

        return false;

    }

    /**
     * checks that the first and last vertex of the vertex list are equal.
     *
     * @throws IllegalArgumentException if first and last vertex aren't equal
     * (this is usually a programming error, therefore InvalidGeometryException
     * is not used)
     */
    protected static void assertLoopProperty(List<VectorXZ> vertexLoop) {
        if (!vertexLoop.get(0).equals(vertexLoop.get(vertexLoop.size() - 1))) {
            throw new IllegalArgumentException("first and last vertex must be equal\n"
                    + "Polygon vertices: " + vertexLoop);
        }
    }

    @Override
    public String toString() {
        return vertexLoop.toString();
    }

}
