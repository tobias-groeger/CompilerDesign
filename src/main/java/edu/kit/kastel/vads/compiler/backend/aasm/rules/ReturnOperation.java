package edu.kit.kastel.vads.compiler.backend.aasm.rules;

import edu.kit.kastel.vads.compiler.backend.aasm.AasmRegisterAllocator;
import edu.kit.kastel.vads.compiler.backend.aasm.AbstractRegister;
import edu.kit.kastel.vads.compiler.backend.aasm.instructions.AbstractInstruction;
import edu.kit.kastel.vads.compiler.backend.aasm.instructions.MoveInstruction;
import edu.kit.kastel.vads.compiler.backend.aasm.instructions.ReturnInstruction;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import edu.kit.kastel.vads.compiler.ir.node.ReturnNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static edu.kit.kastel.vads.compiler.ir.util.NodeSupport.predecessorSkipProj;

public class ReturnOperation implements Pattern {
    @Override
    public Optional<List<Node>> apply(Node node, Map<Node, Gen> patterns, AasmRegisterAllocator allocator) {
        if (node instanceof ReturnNode ret) {
            Node src = predecessorSkipProj(ret, ReturnNode.RESULT);
            patterns.put(ret, new ReturnCodeGen(src, patterns));
            return Optional.of(List.of(ret));
        }

        return Optional.empty();
    }

    public static class ReturnCodeGen implements Gen {
        private final Node src;
        private final Map<Node, Gen> generationRules;

        private ReturnCodeGen(Node src, Map<Node, Gen> generationRules) {
            this.src = src;
            this.generationRules = generationRules;
        }

        @Override
        public AbstractRegister result() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AbstractInstruction> code() {
            return List.of(new MoveInstruction(AbstractRegister.ret(), generationRules.get(src).result()), new ReturnInstruction());
        }
    }
}
