package edu.kit.kastel.vads.compiler.backend.x86_64.regalloc;

import edu.kit.kastel.vads.compiler.backend.aasm.AbstractRegister;
import edu.kit.kastel.vads.compiler.backend.aasm.instructions.AbstractInstruction;
import edu.kit.kastel.vads.compiler.backend.aasm.instructions.BinaryInstruction;
import edu.kit.kastel.vads.compiler.backend.aasm.instructions.MoveInstruction;
import edu.kit.kastel.vads.compiler.backend.x86_64.analysis.FactDatabase;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InterferenceGraphGenerator {

    public InterferenceGraph generateGraph(List<AbstractInstruction> instructions, FactDatabase database) {
        InterferenceGraph interferenceGraph = new InterferenceGraph();
        Set<AbstractRegister> visited = new HashSet<>();

        for (int line = instructions.size() - 1; line >= 0; line--) {
            AbstractInstruction inst = instructions.get(line);
            for (var reg : inst.allRegisters()) {
                if (visited.add(reg)) {
                    interferenceGraph.addVertex(reg);
                }
            }

            switch (inst) {
                case BinaryInstruction(var op, var t, var s1, var s2) -> {
                    for (var t_i : database.getLiveInAt(line + 1)) {
                        if (t != t_i) {
                            interferenceGraph.addEdge(t, t_i);
                        }
                    }
                }
                case MoveInstruction(var t, var s) -> {
                    for (var t_i : database.getLiveInAt(line + 1)) {
                        if (t != t_i && s != t_i) {
                            interferenceGraph.addEdge(t, t_i);
                        }
                    }
                }
                default -> {
                }
            }

        }

        return interferenceGraph;
    }
}
