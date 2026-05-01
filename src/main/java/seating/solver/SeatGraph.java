package seating.solver;

import seating.model.Classroom;
import seating.model.Desk;
import seating.model.Seat;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adjacency graph representing which seats are "next to" each other.
 * Stored as a HashMap&lt;Seat, Set&lt;Seat&gt;&gt; — an adjacency list.
 *
 * <p>This is a core CS III data structure: a graph implemented via HashMap.
 * The graph is built automatically from the spatial desk layout by computing
 * pairwise distances between all seats.</p>
 */
public class SeatGraph {

    private HashMap<Seat, Set<Seat>> adjacency;
    /** Largest pairwise seat distance in this layout — the actual achievable
     *  "as far apart as possible" optimum. Used by ProximityConstraint to
     *  score relative to what's reachable in this classroom, not a fixed
     *  theoretical maximum. */
    private double maxAchievableDistance;

    /** Creates an empty graph. */
    public SeatGraph() {
        adjacency = new HashMap<Seat, Set<Seat>>();
        maxAchievableDistance = 0.0;
    }

    /**
     * Creates a graph from the given adjacency map.
     *
     * @param adjacency prebuilt adjacency map
     */
    public SeatGraph(HashMap<Seat, Set<Seat>> adjacency) {
        this.adjacency = adjacency;
        this.maxAchievableDistance = 0.0;
    }

    /** Sets the precomputed max pairwise seat distance (used by buildFrom). */
    public void setMaxAchievableDistance(double d) { this.maxAchievableDistance = d; }

    /** Returns the largest pairwise seat distance, or 0 if not computed.
     *  ProximityConstraint uses this to scale scores relative to the actual
     *  best-achievable distance in this classroom layout. */
    public double getMaxAchievableDistance() { return maxAchievableDistance; }

    /**
     * Builds the adjacency graph from a classroom's desk layout.
     * Two seats are adjacent if their global distance is within the threshold,
     * OR if they belong to the same desk.
     *
     * @param classroom the classroom to analyze
     * @return a new SeatGraph
     */
    public static SeatGraph buildFrom(Classroom classroom) {
        HashMap<Seat, Set<Seat>> adj = new HashMap<Seat, Set<Seat>>();
        List<Seat> allSeats = classroom.getAllSeats();
        int threshold = classroom.getAdjacencyThreshold();

        // Initialize empty sets
        for (Seat seat : allSeats) {
            adj.put(seat, new HashSet<Seat>());
        }

        // Pairwise distance check — O(n^2), acceptable for classroom sizes.
        // Track the maximum pairwise distance so ProximityConstraint can
        // score relative to the actual achievable "far apart" extreme.
        double maxDist = 0.0;
        for (int i = 0; i < allSeats.size(); i++) {
            for (int j = i + 1; j < allSeats.size(); j++) {
                Seat a = allSeats.get(i);
                Seat b = allSeats.get(j);
                Point2D posA = a.getGlobalPosition();
                Point2D posB = b.getGlobalPosition();
                double dist = posA.distance(posB);
                if (dist > maxDist) maxDist = dist;

                if (dist <= threshold) {
                    adj.get(a).add(b);
                    adj.get(b).add(a);
                }
            }
        }

        // Seats within the same desk are always adjacent
        for (Desk desk : classroom.getDesks()) {
            List<Seat> seats = desk.getSeats();
            for (int i = 0; i < seats.size(); i++) {
                for (int j = i + 1; j < seats.size(); j++) {
                    adj.get(seats.get(i)).add(seats.get(j));
                    adj.get(seats.get(j)).add(seats.get(i));
                }
            }
        }

        SeatGraph graph = new SeatGraph(adj);
        graph.setMaxAchievableDistance(maxDist);
        return graph;
    }

    /**
     * Returns all seats adjacent to the given seat.
     *
     * @param seat the seat to query
     * @return set of neighboring seats, or empty set
     */
    public Set<Seat> getNeighbors(Seat seat) {
        Set<Seat> neighbors = adjacency.get(seat);
        return (neighbors != null) ? neighbors : new HashSet<Seat>();
    }

    /**
     * Checks if two seats are adjacent.
     *
     * @param a first seat
     * @param b second seat
     * @return true if they are neighbors in the graph
     */
    public boolean areAdjacent(Seat a, Seat b) {
        Set<Seat> neighbors = adjacency.get(a);
        return neighbors != null && neighbors.contains(b);
    }

    /** Returns the total number of seats in the graph. */
    public int size() {
        return adjacency.size();
    }

    /** Returns all seats in the graph. */
    public Set<Seat> getAllSeats() {
        return adjacency.keySet();
    }

    /** Returns the raw adjacency map. */
    public HashMap<Seat, Set<Seat>> getAdjacencyMap() {
        return adjacency;
    }
}
