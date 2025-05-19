package edu.kit.kastel.vads.compiler;

import edu.kit.kastel.vads.compiler.backend.x86_64.CodeGenerator;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.SsaTranslation;
import edu.kit.kastel.vads.compiler.ir.optimize.LocalValueNumbering;
import edu.kit.kastel.vads.compiler.ir.util.YCompPrinter;
import edu.kit.kastel.vads.compiler.lexer.Lexer;
import edu.kit.kastel.vads.compiler.parser.ParseException;
import edu.kit.kastel.vads.compiler.parser.Parser;
import edu.kit.kastel.vads.compiler.parser.TokenSource;
import edu.kit.kastel.vads.compiler.parser.ast.FunctionTree;
import edu.kit.kastel.vads.compiler.parser.ast.ProgramTree;
import edu.kit.kastel.vads.compiler.semantic.SemanticAnalysis;
import edu.kit.kastel.vads.compiler.semantic.SemanticException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Invalid arguments: Expected one input file and one output file");
            System.exit(3);
        }
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        ProgramTree program = lexAndParse(input);
        try {
            new SemanticAnalysis(program).analyze();
        } catch (SemanticException e) {
            e.printStackTrace();
            System.exit(7);
            return;
        }
        List<IrGraph> graphs = new ArrayList<>();
        for (FunctionTree function : program.topLevelTrees()) {
            SsaTranslation translation = new SsaTranslation(function, new LocalValueNumbering());
            graphs.add(translation.translate());
        }

        if ("vcg".equals(System.getenv("DUMP_GRAPHS")) || "vcg".equals(System.getProperty("dumpGraphs"))) {
            Path tmp = output.toAbsolutePath().resolveSibling("graphs");
            Files.createDirectory(tmp);
            for (IrGraph graph : graphs) {
                dumpGraph(graph, tmp, "before-codegen");
            }
        }

        // TODO: generate assembly and invoke gcc instead of generating abstract assembly
        String generatedAssembly = new CodeGenerator().generateCode(graphs);
        if (Constants.DEBUG) {
            System.out.println(generatedAssembly);
        }

        try {
            Process assembler =
                    new ProcessBuilder("gcc", "-o", output.toAbsolutePath().toString(), "-x", "assembler", "-").redirectErrorStream(true)
                            .start();
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(assembler.getOutputStream()))) {
                // write the generated assembly to stdin of gcc
                writer.write(generatedAssembly);
                writer.flush();
            }

            int exitCode = assembler.waitFor();
            if (exitCode != 0) {
                // when assembler fails, read its output and print it to stderr
                System.err.println("Error during assembler phase:");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(assembler.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.err.println(line);
                    }
                }
                System.exit(4);
            }

        } catch (UnsupportedOperationException | IOException | InterruptedException e) {
            // some error occurred during assembling
            e.printStackTrace();
            System.exit(4);
        }
    }

    private static ProgramTree lexAndParse(Path input) throws IOException {
        try {
            Lexer lexer = Lexer.forString(Files.readString(input));
            TokenSource tokenSource = new TokenSource(lexer);
            Parser parser = new Parser(tokenSource);
            return parser.parseProgram();
        } catch (ParseException e) {
            e.printStackTrace();
            System.exit(42);
            throw new AssertionError("unreachable");
        }
    }

    private static void dumpGraph(IrGraph graph, Path path, String key) throws IOException {
        Files.writeString(path.resolve(graph.name() + "-" + key + ".vcg"), YCompPrinter.print(graph));
    }
}
