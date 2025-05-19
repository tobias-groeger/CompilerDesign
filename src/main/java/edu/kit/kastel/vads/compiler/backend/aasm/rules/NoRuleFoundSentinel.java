package edu.kit.kastel.vads.compiler.backend.aasm.rules;

import edu.kit.kastel.vads.compiler.backend.aasm.AasmRegisterAllocator;
import edu.kit.kastel.vads.compiler.ir.node.Node;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class NoRuleFoundSentinel implements Pattern {

    @Override
    public Optional<List<Node>> apply(Node node, Map<Node, Gen> generationRules, AasmRegisterAllocator allocator) {
        throw new UnsupportedOperationException("No rule to translate this node found!: " + node);
    }

}
