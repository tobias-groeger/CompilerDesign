package edu.kit.kastel.vads.compiler.backend.x86_64;

import edu.kit.kastel.vads.compiler.Constants;
import edu.kit.kastel.vads.compiler.backend.aasm.AbstractImmediate;
import edu.kit.kastel.vads.compiler.backend.aasm.AbstractRegister;
import edu.kit.kastel.vads.compiler.backend.aasm.instructions.*;
import edu.kit.kastel.vads.compiler.backend.optimization.Optimization;
import edu.kit.kastel.vads.compiler.backend.optimization.ValuePropagation;
import edu.kit.kastel.vads.compiler.backend.x86_64.analysis.FactDatabase;
import edu.kit.kastel.vads.compiler.backend.x86_64.analysis.LivenessAnalysis;
import edu.kit.kastel.vads.compiler.backend.x86_64.regalloc.*;
import edu.kit.kastel.vads.compiler.ir.IrGraph;

import java.util.List;

public class CodeGenerator {

    private static final List<Optimization> OPTIMIZATIONS = List.of(new ValuePropagation());

    public String generateCode(List<IrGraph> program) {
        var aasmGenerator = new edu.kit.kastel.vads.compiler.backend.aasm.CodeGenerator();
        var aasm = aasmGenerator.generateCode(program);

        boolean changes;
        do {
            changes = false;
            for (var i : OPTIMIZATIONS) {
                changes |= i.optimize(aasm);
            }
        } while (changes);

        if (Constants.DEBUG) {
            System.out.println("Optimized AASM");
            for (int i = 0; i < aasm.size(); i++) {
                System.out.println((i + 1) + ": " + aasm.get(i));
            }
            System.out.println("===");
        }

        FactDatabase database = new FactDatabase(aasm.size());
        LivenessAnalysis liveness = new LivenessAnalysis(database);
        liveness.analyse(aasm);

        InterferenceGraph interferenceGraph = new InterferenceGraphGenerator().generateGraph(aasm, database);
        GraphColoringRegisterAllocator allocator = new GraphColoringRegisterAllocator(aasm, interferenceGraph);

        StringBuilder builder = new StringBuilder();
        builder.append("""
                               .global main
                               .global _main
                               .text
                               
                               main:
                               call _main
                               
                               # move the return value into the first argument for the syscall
                               movq %rax, %rdi
                               # move the exit syscall number into rax
                               movq $0x3C, %rax
                               syscall
                               
                               _main:
                                 # start of generated assembly
                               
                               """);

        for (var i : aasm) {
            emitCode(builder, i, allocator);
        }

        return builder.toString();
    }

    private static String mapOp(BinaryInstruction.Op op) {
        return switch (op) {
            case BinaryInstruction.Op.ADD -> "addl";
            case BinaryInstruction.Op.DIV -> "idivl";
            case BinaryInstruction.Op.MOD -> "idivl";
            case BinaryInstruction.Op.MUL -> "mull";
            case BinaryInstruction.Op.SUB -> "subl";
        };
    }

    private void emitCode(StringBuilder b, AbstractInstruction instruction, RegisterAllocator allocator) {
        switch (instruction) {
            // binary instruction with special registers (mul, div, mod)
            case BinaryInstruction(BinaryInstruction.Op op, var destIn, AbstractRegister lhsIn, AbstractRegister rhsIn) when
                    op == BinaryInstruction.Op.MUL || op == BinaryInstruction.Op.DIV || op == BinaryInstruction.Op.MOD -> {

                ActualRegister lhs = unspill(allocator.registerOf(lhsIn), b, 0, true);
                ActualRegister rhs = unspill(allocator.registerOf(rhsIn), b, 1, true);
                ActualRegister dest = unspill(allocator.registerOf(destIn), b, 2, false);

                if (!lhs.equals(ActualRegister.eax())) {
                    b.append("movl ").append(lhs).append(", %eax\n");
                }
                if (rhs.equals(ActualRegister.edx())) {
                    // rhs is in edx (will be overwritten by cltd): save it and write it back after
                    b.append("movl %edx, ").append(ActualRegister.spare(1)).append("\n");
                    b.append("cltd\n");
                    b.append(mapOp(op)).append(" ").append(ActualRegister.spare(1)).append("\n");
                    b.append("movl ").append(ActualRegister.spare(1)).append(", %edx\n");
                } else {
                    b.append("cltd\n");
                    b.append(mapOp(op)).append(" ").append(rhs).append("\n");
                }

                if ((op == BinaryInstruction.Op.MUL || op == BinaryInstruction.Op.DIV) && !dest.equals(ActualRegister.eax())) {
                    // mul places lower bits of result in eax, div has quotient in eax
                    b.append("movl %eax, ").append(dest).append("\n");
                } else if (op == BinaryInstruction.Op.MOD && !dest.equals(ActualRegister.edx())) {
                    // div has remainder in edx
                    b.append("movl %edx, ").append(dest).append("\n");
                }

                respill(allocator.registerOf(lhsIn), b, 0);
                respill(allocator.registerOf(rhsIn), b, 1);
                respill(allocator.registerOf(destIn), b, 2);
            }
            // binary instruction with lhs and rhs from a register
            case BinaryInstruction(BinaryInstruction.Op op, var destIn, AbstractRegister lhsIn, AbstractRegister rhsIn) when
                    op == BinaryInstruction.Op.ADD || op == BinaryInstruction.Op.SUB -> {

                ActualRegister x = unspill(allocator.registerOf(lhsIn), b, 0, true);
                ActualRegister y = unspill(allocator.registerOf(rhsIn), b, 1, true);
                ActualRegister d = unspill(allocator.registerOf(destIn), b, 2, false);

                if (d.equals(x) && d.equals(y)) {
                    // d <- d x d
                    b.append(mapOp(op)).append(" ").append(d).append(", ").append(d).append("\n");
                } else if (d.equals(x)) {
                    // d <- d x %
                    b.append(mapOp(op)).append(" ").append(y).append(", ").append(d).append("\n");
                } else if (d.equals(y) && op == BinaryInstruction.Op.SUB) {
                    // d <- % x d

                    // for subtraction: 'sub src dst' <==> dst := dst - src
                    // if dst == rhs, we have to first save rhs -> spare
                    b.append("movl ").append(d).append(", ").append(ActualRegister.spare(1)).append("\n");
                    b.append("movl ").append(x).append(", ").append(d).append("\n");
                    b.append(mapOp(op)).append(" ").append(ActualRegister.spare(1)).append(", ").append(d).append("\n");
                } else if (d.equals(y) && op == BinaryInstruction.Op.ADD) {
                    // d <- % + d
                    b.append(mapOp(op)).append(" ").append(x).append(", ").append(d).append("\n");
                } else {
                    b.append("movl ").append(x).append(", ").append(d).append("\n");
                    b.append(mapOp(op)).append(" ").append(y).append(", ").append(d).append("\n");
                }

                respill(allocator.registerOf(lhsIn), b, 0);
                respill(allocator.registerOf(rhsIn), b, 1);
                respill(allocator.registerOf(destIn), b, 2);
            } // binary instruction with lhs from a register
            case BinaryInstruction(BinaryInstruction.Op op, var destIn, AbstractRegister lhsIn, AbstractImmediate rhs) when
                    op == BinaryInstruction.Op.ADD || op == BinaryInstruction.Op.SUB -> {
                ActualRegister x = unspill(allocator.registerOf(lhsIn), b, 0, true);
                ActualRegister d = unspill(allocator.registerOf(destIn), b, 1, false);

                if (d.equals(x)) {
                    // for subtraction: 'sub src dst' <==> dst := dst - src
                    b.append(mapOp(op)).append(" $").append(rhs.value()).append(", ").append(d).append("\n");
                } else {
                    b.append("movl ").append(x).append(", ").append(d).append("\n");
                    b.append(mapOp(op)).append(" $").append(rhs.value()).append(", ").append(d).append("\n");
                }

                respill(allocator.registerOf(lhsIn), b, 0);
                respill(allocator.registerOf(destIn), b, 1);
            }
            // binary instruction with rhs from a register
            case BinaryInstruction(BinaryInstruction.Op op, var destIn, AbstractImmediate lhs, AbstractRegister rhsIn) when
                    op == BinaryInstruction.Op.ADD || op == BinaryInstruction.Op.SUB -> {
                ActualRegister y = unspill(allocator.registerOf(rhsIn), b, 0, true);
                ActualRegister d = unspill(allocator.registerOf(destIn), b, 1, false);

                if (d.equals(y) && op == BinaryInstruction.Op.SUB) {
                    // for subtraction: 'sub src dst' <==> dst := dst - src
                    // if dst == rhs, we have to first save rhs -> spare
                    b.append("movl ").append(d).append(", ").append(ActualRegister.spare(2)).append("\n");
                    b.append("movl $").append(lhs).append(", ").append(d).append("\n");
                    b.append(mapOp(op)).append(" ").append(ActualRegister.spare(2)).append(", ").append(d).append("\n");
                } else {
                    b.append("movl $").append(lhs).append(", ").append(d).append("\n");
                    b.append(mapOp(op)).append(" ").append(y).append(", ").append(d).append("\n");
                }

                respill(allocator.registerOf(rhsIn), b, 0);
                respill(allocator.registerOf(destIn), b, 1);
            }
            // binary instruction with two immediates
            case BinaryInstruction(BinaryInstruction.Op op, var destIn, AbstractImmediate lhs, AbstractImmediate rhs) when
                    op == BinaryInstruction.Op.ADD || op == BinaryInstruction.Op.SUB -> {
                ActualRegister dest = unspill(allocator.registerOf(destIn), b, 0, false);

                // for subtraction: 'sub src dst' <==> dst := dst - src
                b.append("movl $").append(lhs.value()).append(", ").append(dest).append("\n");
                b.append(mapOp(op)).append(" $").append(rhs.value()).append(", ").append(dest).append("\n");

                respill(allocator.registerOf(destIn), b, 0);
            }
            case MoveInstruction(var destIn, AbstractRegister srcIn) -> {
                ActualRegister src = unspill(allocator.registerOf(srcIn), b, 0, false);
                ActualRegister dest = unspill(allocator.registerOf(destIn), b, 1, false);

                b.append("movl ").append(src).append(", ").append(dest).append("\n");

                respill(allocator.registerOf(srcIn), b, 0);
                respill(allocator.registerOf(destIn), b, 1);
            }
            case MoveInstruction(var destIn, AbstractImmediate imm) -> {
                ActualRegister dest = unspill(allocator.registerOf(destIn), b, 0, false);

                b.append("movl $").append(imm.value()).append(", ").append(dest).append("\n");

                respill(allocator.registerOf(destIn), b, 0);
            }
            case NopInstruction _ -> {
            }
            case ReturnInstruction _ -> b.append("ret\n");
            default -> throw new UnsupportedOperationException("No instructions for " + instruction + " available!");
        }
    }

    private ActualRegister unspill(ActualRegister reg, StringBuilder b, int index, boolean load) {
        int address = reg.spillIndex();
        if (address < 0) {
            return reg;
        }
        address += 1;

        ActualRegister spillRegister = ActualRegister.spare(index);
        if (load) b.append("movl ").append(-4 * address).append("(%rsp), ").append(spillRegister).append("\n");

        return spillRegister;
    }

    private void respill(ActualRegister reg, StringBuilder b, int index) {
        int address = reg.spillIndex();
        if (address < 0) {
            return;
        }
        address += 1;

        ActualRegister spillRegister = ActualRegister.spare(index);
        b.append("movl ").append(spillRegister).append(", ").append(-4 * address).append("(%rsp)\n");
    }
}
