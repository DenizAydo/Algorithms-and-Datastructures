package p3.graph;

import p3.SetUtils;

import java.util.*;
import java.util.stream.IntStream;

/**
 * An implementation of an immutable {@link Graph} that uses an {@link AdjacencyMatrix} to store the graph.
 * @param <N> the type of the nodes in this graph.
 */
public class AdjacencyGraph<N> implements Graph<N> {

    /**
     * The adjacency matrix that stores the graph.
     */
    private final AdjacencyMatrix matrix;

    /**
     * A map from nodes to their indices in the adjacency matrix.
     * Every node in the graph is mapped to a distinct index in the range [0, {@link #matrix}.size() -1].
     * This map is the inverse of {@link #indexNodes}.
     */
    private final Map<N, Integer> nodeIndices = new HashMap<>();

    /**
     * A map from indices in the adjacency matrix to the nodes they represent.
     * Every index in the range [0, {@link #matrix}.size() -1] is mapped to a distinct node in the graph.
     * This map is the inverse of {@link #nodeIndices}.
     */
    private final Map<Integer, N> indexNodes = new HashMap<>();

    /**
     * The nodes in this graph.
     */
    private final Set<N> nodes;

    /**
     * The edges in this graph.
     */
    private final Set<Edge<N>> edges;

    /**
     * Constructs a new {@link AdjacencyGraph} with the given nodes and edges.
     * @param nodes the nodes in the graph.
     * @param edges the edges in the graph.
     */
    public AdjacencyGraph(Set<N> nodes, Set<Edge<N>> edges) { // 2/2 points
        matrix = new AdjacencyMatrix(nodes.size());
        this.nodes = SetUtils.immutableCopyOf(nodes);
        this.edges = SetUtils.immutableCopyOf(edges);

        int idx = 0;
        for(N node : nodes) {
            indexNodes.put(idx, node);
            nodeIndices.put(node, idx);
            idx++;
        }

        for(Edge<N> edge : edges) {
            int a = nodeIndices.get(edge.a());
            int b = nodeIndices.get(edge.b());
            int weight = edge.weight();
            matrix.addEdge(a, b, weight);
        }

    }

    @Override
    public Set<N> getNodes() {
        return nodes;
    }

    @Override
    public Set<Edge<N>> getEdges() {
        return edges;
    }

    @Override
    public Set<Edge<N>> getAdjacentEdges(N node) { // 2/2 points
        Set<Edge<N>> edgeSet = new HashSet<>();

        int[] edgeWeightArray = matrix.getAdjacent(nodeIndices.get(node));
        for(int i = 0; i < edgeWeightArray.length; i++) {
            if(edgeWeightArray[i] != 0) {
                N b = indexNodes.get(i);
                Edge<N> edge = new EdgeImpl<>(node,b, edgeWeightArray[i]);
                edgeSet.add(edge);
            }
        }

        return edgeSet;
    }

    @Override
    public MutableGraph<N> toMutableGraph() {
        return MutableGraph.of(nodes, edges);
    }

    @Override
    public Graph<N> toGraph() {
        return this;
    }
}
