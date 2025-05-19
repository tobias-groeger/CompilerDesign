package edu.kit.kastel.vads.compiler.backend.x86_64.analysis;

import edu.kit.kastel.vads.compiler.Constants;
import edu.kit.kastel.vads.compiler.backend.aasm.AbstractRegister;
import edu.kit.kastel.vads.compiler.backend.aasm.instructions.*;

import java.util.List;

public class LivenessAnalysis {
    private final FactDatabase database;

    public LivenessAnalysis(FactDatabase database) {
        this.database = database;
    }

    public void analyse(List<AbstractInstruction> instructions) {
        boolean changes;
        do {
            changes = runSinglePass(instructions);
        } while (changes);

        if (Constants.DEBUG) {
            System.out.println("Liveness Database");
            for (int i = 0; i < database.size(); i++) {
                System.out.println((i + 1) + ": " + database.getLiveInAt(i));
            }
            System.out.println("===");
        }
    }

    private boolean runSinglePass(List<AbstractInstruction> instructions) {
        boolean anyChanges = false;

        for (int line = instructions.size() - 1; line >= 0; line--) {
            boolean changed;
            do {
                changed = switch (instructions.get(line)) {
                    case BinaryInstruction(var op, var x, var y, var z) -> {
                        boolean mod = false;

                        // L1
                        if (y instanceof AbstractRegister yReg) mod |= database.setLive(line, yReg);
                        if (z instanceof AbstractRegister zReg) mod |= database.setLive(line, zReg);

                        // L2
                        for (var u : database.getLiveInAt(line + 1)) {
                            if (!x.equals(u)) {
                                mod |= database.setLive(line, u);
                            }
                        }

                        yield mod;
                    }

                    // L3
                    case ReturnInstruction() -> database.setLive(line, AbstractRegister.ret());

                    // L4
                    case MoveInstruction(var x, var c) -> {
                        boolean mod = false;
                        for (var u : database.getLiveInAt(line + 1)) {
                            if (!x.equals(u)) {
                                mod |= database.setLive(line, u);
                            }
                        }

                        if (c instanceof AbstractRegister cReg) mod |= database.setLive(line, cReg);

                        yield mod;
                    }

                    // propagate live-in variables through nop instructions
                    case NopInstruction() -> {
                        boolean mod = false;
                        for (var u : database.getLiveInAt(line + 1)) {
                            mod |= database.setLive(line, u);
                        }
                        yield mod;
                    }

                };
                if (changed) anyChanges = true;

            } while (changed);
        }

        return anyChanges;
    }

}
