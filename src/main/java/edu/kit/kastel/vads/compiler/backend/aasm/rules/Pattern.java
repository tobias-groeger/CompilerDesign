package edu.kit.kastel.vads.compiler.backend.aasm.rules;

import edu.kit.kastel.vads.compiler.backend.aasm.AasmRegisterAllocator;
import edu.kit.kastel.vads.compiler.backend.aasm.AbstractRegister;
import edu.kit.kastel.vads.compiler.backend.aasm.instructions.AbstractInstruction;
import edu.kit.kastel.vads.compiler.ir.node.Node;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface Pattern {

    /**
     * Check whether this pattern can be applied to the subtree rooted at node.
     *
     * @param node      the node at which to apply this pattern
     * @param patterns  a map from each node to its @ref Gen that can generate its result
     * @param allocator a class which can be used to get unique temporary registers
     * @return a list of all nodes covered by this pattern or an empty optional if it can't be applied
     */
    Optional<List<Node>> apply(Node node, Map<Node, Gen> patterns, AasmRegisterAllocator allocator);

    interface Gen {
        /**
         * Get the register in which the result of this code generation pattern has been written.
         * Only valid after {@link #code} has been executed.
         *
         * @return the result register
         */
        AbstractRegister result();

        /**
         * Generate the required instructions for this code generation pattern to put the result into {@link #result}.
         *
         * @return the list of required instructions
         */
        List<AbstractInstruction> code();
    }

}
