package edu.kit.kastel.vads.compiler.backend.aasm.rules;

import edu.kit.kastel.vads.compiler.backend.aasm.AasmRegisterAllocator;
import edu.kit.kastel.vads.compiler.backend.aasm.AbstractRegister;
import edu.kit.kastel.vads.compiler.backend.aasm.instructions.AbstractInstruction;
import edu.kit.kastel.vads.compiler.backend.aasm.instructions.BinaryInstruction;
import edu.kit.kastel.vads.compiler.ir.node.*;
import org.jspecify.annotations.Nullable;

import java.util.*;

import static edu.kit.kastel.vads.compiler.ir.util.NodeSupport.predecessorSkipProj;

public class BinaryOperatorPattern implements Pattern {
    private BinaryInstruction.Op mapOp(BinaryOperationNode node) {
        return switch (node) {
            case AddNode _ -> BinaryInstruction.Op.ADD;
            case DivNode _ -> BinaryInstruction.Op.DIV;
            case ModNode _ -> BinaryInstruction.Op.MOD;
            case MulNode _ -> BinaryInstruction.Op.MUL;
            case SubNode _ -> BinaryInstruction.Op.SUB;
        };
    }


    @Override
    public Optional<List<Node>> apply(Node node, Map<Node, Gen> patterns, AasmRegisterAllocator allocator) {
        if (node instanceof BinaryOperationNode binOp) {
            Node lhs = predecessorSkipProj(binOp, AddNode.LEFT);
            Node rhs = predecessorSkipProj(binOp, AddNode.RIGHT);

            patterns.put(binOp, new BinaryOperatorCodeGen(mapOp(binOp), lhs, rhs, patterns, allocator));
            return Optional.of(List.of(binOp));
        }

        return Optional.empty();
    }

    public static class BinaryOperatorCodeGen implements Gen {
        private final BinaryInstruction.Op op;
        private final Node lhs, rhs;
        private final Map<Node, Gen> generationRules;
        private final AasmRegisterAllocator allocator;
        private @Nullable AbstractRegister destinationRegister;

        private BinaryOperatorCodeGen(BinaryInstruction.Op op, Node lhs, Node rhs, Map<Node, Gen> generationRules,
                                      AasmRegisterAllocator allocator) {
            this.op = op;
            this.lhs = lhs;
            this.rhs = rhs;
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
            List<AbstractInstruction> instructions = new ArrayList<>();

            var lhsRule = this.generationRules.get(this.lhs);
            var rhsRule = this.generationRules.get(this.rhs);

            this.destinationRegister = this.allocator.allocate();
            instructions.add(new BinaryInstruction(this.op, this.destinationRegister, lhsRule.result(), rhsRule.result()));

            return instructions;
        }
    }
}
