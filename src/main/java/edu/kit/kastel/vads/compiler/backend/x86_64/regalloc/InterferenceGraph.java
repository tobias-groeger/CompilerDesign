package edu.kit.kastel.vads.compiler.backend.x86_64.regalloc;

import edu.kit.kastel.vads.compiler.backend.aasm.AbstractRegister;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;

public class InterferenceGraph extends DefaultUndirectedGraph<AbstractRegister, DefaultEdge> {
    public InterferenceGraph() {
        super(DefaultEdge.class);
    }
}
