package edu.kit.kastel.vads.compiler.backend.aasm.rules;

import edu.kit.kastel.vads.compiler.backend.aasm.AasmRegisterAllocator;
import edu.kit.kastel.vads.compiler.ir.node.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SkipRule implements Pattern {
    @Override
    public Optional<List<Node>> apply(Node node, Map<Node, Gen> generationRules, AasmRegisterAllocator allocator) {
        return switch (node) {
            case Block _, ProjNode _, StartNode _, Phi _ -> Optional.of(List.of());
            default -> Optional.empty();
        };
    }
}
