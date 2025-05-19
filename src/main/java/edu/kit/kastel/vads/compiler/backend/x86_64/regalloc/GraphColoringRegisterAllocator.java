package edu.kit.kastel.vads.compiler.backend.x86_64.regalloc;

import edu.kit.kastel.vads.compiler.Constants;
import edu.kit.kastel.vads.compiler.backend.aasm.AbstractRegister;
import edu.kit.kastel.vads.compiler.backend.aasm.instructions.AbstractInstruction;
import edu.kit.kastel.vads.compiler.backend.aasm.instructions.BinaryInstruction;
import org.jgrapht.alg.util.NeighborCache;
import org.jgrapht.graph.DefaultEdge;

import java.util.*;
import java.util.stream.Collectors;

public class GraphColoringRegisterAllocator implements RegisterAllocator {
    List<AbstractInstruction> aasm;
    private final InterferenceGraph graph;
    private final NeighborCache<AbstractRegister, DefaultEdge> graphCache;

    private final Map<AbstractRegister, ActualRegister> registers = new HashMap<>();
    private final Map<AbstractRegister, Set<ActualRegister>> taint = new HashMap<>();
    private final Map<AbstractRegister, ActualRegister> requestedRegister = new HashMap<>();

    public GraphColoringRegisterAllocator(List<AbstractInstruction> aasm, InterferenceGraph graph) {
        this.aasm = aasm;
        this.graph = graph;
        graphCache = new NeighborCache<>(graph);

        List<AbstractRegister> simplicialOrdering = calculateSimplicialOrdering();
        allocateRegisters(simplicialOrdering);
    }

    private List<AbstractRegister> calculateSimplicialOrdering() {
        Map<AbstractRegister, Integer> W = new HashMap<>();
        List<AbstractRegister> simplicialOrdering = new ArrayList<>();
        for (var i : this.graph.vertexSet()) {
            W.put(i, 0);
        }

        // pre colour all instructions that require special registers
        simplicialOrdering.add(AbstractRegister.ret());
        W.remove(AbstractRegister.ret());
        registers.put(AbstractRegister.ret(), ActualRegister.eax());

        for (AbstractInstruction inst : this.aasm) {
            switch (inst) {
                case BinaryInstruction(BinaryInstruction.Op op, var dest, var lhs, _) when op == BinaryInstruction.Op.MUL ||
                                                                                           op == BinaryInstruction.Op.DIV ||
                                                                                           op == BinaryInstruction.Op.MOD -> {
                    if (W.remove(dest) != null) {
                        simplicialOrdering.add(dest);
                    }
                    if (op == BinaryInstruction.Op.MUL || op == BinaryInstruction.Op.DIV) {
                        // mul places lower bits of result in eax, div has quotient in eax
                        requestedRegister.putIfAbsent(dest, ActualRegister.eax());
                    } else {
                        // div has remainder in edx
                        requestedRegister.putIfAbsent(dest, ActualRegister.edx());
                    }

                    if (lhs instanceof AbstractRegister lhsReg) {
                        if (W.remove(lhsReg) != null) {
                            simplicialOrdering.add(lhsReg);
                        }
                        requestedRegister.putIfAbsent(lhsReg, ActualRegister.eax());
                        taint.computeIfAbsent(lhsReg, _ -> new HashSet<>()).addAll(Set.of(ActualRegister.eax(), ActualRegister.edx()));
                    } else {
                        throw new UnsupportedOperationException("MUL, DIV and MOD do not support immediate lhs operand");
                    }
                }
                default -> {
                }
            }
        }

        // adjust weights of precolored neighbors
        for (var reg : simplicialOrdering) {
            for (var u : graphCache.neighborsOf(reg)) {
                if (W.get(u) != null) {
                    W.compute(u, (AbstractRegister _, Integer i) -> i + 1);
                }
            }
        }

        while (!W.isEmpty()) {
            AbstractRegister v = W.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElseThrow();
            simplicialOrdering.add(v);
            for (var u : graphCache.neighborsOf(v)) {
                if (W.get(u) != null) {
                    W.compute(u, (AbstractRegister _, Integer i) -> i + 1);
                }
            }
            W.remove(v);
        }

        if (Constants.DEBUG) {
            System.out.println("Simplicial ordering");
            for (var i : simplicialOrdering) {
                System.out.println(i);
            }
            System.out.println("===");
        }

        return simplicialOrdering;
    }

    private void allocateRegisters(List<AbstractRegister> simplicialOrdering) {
        for (var reg : simplicialOrdering) {
            // already assigned
            if (registers.containsKey(reg)) continue;

            Set<ActualRegister> usedInNeighborhood = new TreeSet<>();
            for (var n : graphCache.neighborsOf(reg)) {
                if (registers.containsKey(n)) usedInNeighborhood.add(registers.get(n));
                usedInNeighborhood.addAll(taint.getOrDefault(n, Set.of()));
            }

            ActualRegister requested = requestedRegister.get(reg);
            if (requested != null && !usedInNeighborhood.contains(requested)) {
                registers.put(reg, requested);
                continue;
            }

            int available = 0;
            boolean assigned = false;
            for (var used : usedInNeighborhood) {
                if (used.id() > available) {
                    assigned = true;
                    registers.put(reg, new ActualRegister(available));

                    break;
                }
                available++;
            }
            if (!assigned) {
                registers.put(reg, new ActualRegister(available));
            }
        }

        if (Constants.DEBUG) {
            System.out.println("Registers");
            for (var entry : registers.entrySet()) {
                System.out.println(entry.getKey() + " => " + entry.getValue() + "  (taint=" +
                                   taint.getOrDefault(entry.getKey(), Set.of()).stream().map(ActualRegister::toString)
                                           .collect(Collectors.joining(", ")) + ")");
            }
            System.out.println("===");
        }
    }

    @Override
    public ActualRegister registerOf(AbstractRegister register) {
        return registers.get(register);
    }

}
