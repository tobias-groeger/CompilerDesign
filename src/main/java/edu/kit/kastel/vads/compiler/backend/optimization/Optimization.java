package edu.kit.kastel.vads.compiler.backend.optimization;

import edu.kit.kastel.vads.compiler.backend.aasm.instructions.AbstractInstruction;

import java.util.List;

public interface Optimization {

    /**
     * Run this optimization and return whether something changed.
     *
     * @param instructions the instructions to optimize
     * @return true, iff something was optimized, false otherwise
     */
    boolean optimize(List<AbstractInstruction> instructions);

}
