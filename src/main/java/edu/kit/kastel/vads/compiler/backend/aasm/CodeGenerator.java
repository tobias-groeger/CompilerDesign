package edu.kit.kastel.vads.compiler.backend.aasm;

import edu.kit.kastel.vads.compiler.Constants;
import edu.kit.kastel.vads.compiler.backend.aasm.instructions.AbstractInstruction;
import edu.kit.kastel.vads.compiler.backend.aasm.rules.*;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.Node;

import java.util.*;

public class CodeGenerator {
    private static final List<Pattern> RULES =
            List.of(new SkipRule(), new BinaryOperatorPattern(), new ReturnOperation(), new ImmediateRule(), new NoRuleFoundSentinel());

    public List<AbstractInstruction> generateCode(List<IrGraph> program) {
        List<AbstractInstruction> instructions = new ArrayList<>();
        for (IrGraph graph : program) {
            AasmRegisterAllocator allocator = new AasmRegisterAllocator();
            instructions.addAll(generateForGraph(graph, allocator));
        }

        if (Constants.DEBUG) {
            System.out.println("AASM");
            for (int i = 0; i < instructions.size(); i++) {
                System.out.println((i + 1) + ": " + instructions.get(i));
            }
            System.out.println("===");
        }

        return instructions;
    }

    private List<AbstractInstruction> generateForGraph(IrGraph graph, AasmRegisterAllocator allocator) {
        List<AbstractInstruction> instructions = new ArrayList<>();
        Map<Node, Pattern.Gen> patterns = new HashMap<>();
        Set<Node> visited = new HashSet<>();
        scan(graph.endBlock(), instructions, patterns, visited, allocator);

        return instructions;
    }

    private void scan(Node node, List<AbstractInstruction> instructions, Map<Node, Pattern.Gen> patterns, Set<Node> visited,
                      AasmRegisterAllocator allocator) {
        if (!visited.add(node)) {
            return; // this node was already encountered
        }

        // maximal munch
        for (Pattern rule : RULES) {
            var result = rule.apply(node, patterns, allocator);
            if (result.isPresent()) {
                // this rule was applied, mark all nodes covered by it as visited
                visited.addAll(result.get());
                break; // don't check any more rules
            }
        }
        // the NoRuleFoundSentinel will ensure we will always apply a rule or throw

        // run recursively on all nodes before this one
        for (Node predecessor : node.predecessors()) {
            scan(predecessor, instructions, patterns, visited, allocator);
        }

        // add instructions required for this node
        Pattern.Gen codeGen = patterns.get(node);
        if (codeGen != null) {
            instructions.addAll(codeGen.code());
        }
    }

}
