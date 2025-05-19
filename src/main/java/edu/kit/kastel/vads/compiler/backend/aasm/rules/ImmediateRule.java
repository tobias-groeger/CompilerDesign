package edu.kit.kastel.vads.compiler.backend.aasm.rules;

import edu.kit.kastel.vads.compiler.backend.aasm.AasmRegisterAllocator;
import edu.kit.kastel.vads.compiler.backend.aasm.AbstractImmediate;
import edu.kit.kastel.vads.compiler.backend.aasm.AbstractRegister;
import edu.kit.kastel.vads.compiler.backend.aasm.instructions.AbstractInstruction;
import edu.kit.kastel.vads.compiler.backend.aasm.instructions.MoveInstruction;
import edu.kit.kastel.vads.compiler.ir.node.ConstIntNode;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ImmediateRule implements Pattern {
    @Override
    public Optional<List<Node>> apply(Node node, Map<Node, Gen> patterns, AasmRegisterAllocator allocator) {
        if (node instanceof ConstIntNode imm) {
            patterns.put(imm, new ImmediateCodeGen(imm.value(), patterns, allocator));
            return Optional.of(List.of(imm));
        }

        return Optional.empty();
    }

    public static class ImmediateCodeGen implements Gen {
        private final int value;
        private final Map<Node, Gen> generationRules;
        private final AasmRegisterAllocator allocator;
        private @Nullable AbstractRegister destinationRegister;

        private ImmediateCodeGen(int value, Map<Node, Gen> generationRules, AasmRegisterAllocator allocator) {
            this.value = value;
            this.generationRules = generationRules;
            this.allocator = allocator;
        }

        @Override
        public AbstractRegister result() {
            Objects.requireNonNull(destinationRegister);
            return destinationRegister;
        }

        @Override
        public List<AbstractInstruction> code() {
            this.destinationRegister = this.allocator.allocate();
            return List.of(new MoveInstruction(destinationRegister, new AbstractImmediate(this.value)));
        }
    }
}
