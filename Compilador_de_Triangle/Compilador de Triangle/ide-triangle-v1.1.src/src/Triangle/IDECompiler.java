/*
 * IDE-Triangle v1.0
 * Compiler.java 
 *
 * Version para curso Compiladores 2025
 */

package Triangle;

import Triangle.CodeGenerator.Frame;
import java.awt.event.ActionListener;
import Triangle.SyntacticAnalyzer.SourceFile;
import Triangle.SyntacticAnalyzer.Scanner;
import Triangle.AbstractSyntaxTrees.Program;
import Triangle.SyntacticAnalyzer.Parser;
import Triangle.ContextualAnalyzer.Checker;
import Triangle.CodeGenerator.Encoder;
import Triangle.CodeGenerator.LLVM.LLVMGenerator;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;



/** 
 * This is merely a reimplementation of the Triangle.Compiler class. We need
 * to get to the ASTs in order to draw them in the IDE without modifying the
 * original Triangle code.
 *
 * @author Luis Leopoldo Perez <luiperpe@ns.isi.ulatina.ac.cr>
 */
public class IDECompiler {

    // <editor-fold defaultstate="collapsed" desc=" Methods ">
    /**
     * Creates a new instance of IDECompiler.
     *
     */
    public IDECompiler() {
    }
    
    /**
     * Particularly the same compileProgram method from the Triangle.Compiler
     * class.
     * @param sourceName Path to the source file.
     * @return True if compilation was succesful.
     */
    public boolean compileProgram(String sourceName) {
        System.out.println("********** " +
                           "Triangle Compiler (IDE-Triangle 1.0)" +
                           " **********");
        
        System.out.println("Syntactic Analysis ...");
        SourceFile source = new SourceFile(sourceName);
        Scanner scanner = new Scanner(source);
        report = new IDEReporter();
        Parser parser = new Parser(scanner, report);
        boolean success = false;
        lastLlvmModule = null;
        lastLlvmOutputPath = null;
        
        rootAST = parser.parseProgram();
        if (report.numErrors == 0) {
            System.out.println("Contextual Analysis ...");
            Checker checker = new Checker(report);
            checker.check(rootAST);
            if (report.numErrors == 0) {
                System.out.println("Code Generation ...");
                Encoder encoder = new Encoder(report);
                encoder.encodeRun(rootAST, false);
                
                if (report.numErrors == 0) {
                    encoder.saveObjectProgram(replaceExtension(sourceName, ".tam"));
                    if (emitLlvm) {
                        generateLlvmModule(sourceName, rootAST);
                    }
                    success = true;
                }
            }
        }

        if (success)
            System.out.println("Compilation was successful.");
        else
            System.out.println("Compilation was unsuccessful.");
        
        return(success);
    }
      
    /**
     * Returns the line number where the first error is.
     * @return Line number.
     */
    public int getErrorPosition() {
        return(report.getFirstErrorPosition());
    }
        
    /**
     * Returns the root Abstract Syntax Tree.
     * @return Program AST (root).
     */
    public Program getAST() {
        return(rootAST);
    }

    public void setEmitLlvm(boolean emit) {
        this.emitLlvm = emit;
    }

    public boolean isEmitLlvm() {
        return emitLlvm;
    }

    public String getLlvmModule() {
        return lastLlvmModule;
    }

    public String getLlvmOutputPath() {
        return lastLlvmOutputPath;
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc=" Attributes ">
    private Program rootAST;        // The Root Abstract Syntax Tree.    
    private IDEReporter report;     // Our ErrorReporter class.
    private boolean emitLlvm;
    private LLVMGenerator.Request llvmRequest = LLVMGenerator.Request.defaults();
    private String lastLlvmModule;
    private String lastLlvmOutputPath;
    // </editor-fold>

    private void generateLlvmModule(String sourceName, Program program) {
        try {
            LLVMGenerator generator = new LLVMGenerator();
            LLVMGenerator.Result result = generator.generate(program, llvmRequest);
            lastLlvmModule = result.irModule();
            if (lastLlvmModule == null) {
                lastLlvmModule = "";
            }
            String outputPath = replaceExtension(sourceName, ".ll");
            File outputFile = new File(outputPath);
            FileWriter writer = new FileWriter(outputFile);
            try {
                writer.write(lastLlvmModule);
            } finally {
                writer.close();
            }
            lastLlvmOutputPath = outputFile.getAbsolutePath();
            System.out.println("LLVM IR written to " + lastLlvmOutputPath);
        } catch (IOException ex) {
            System.out.println("Failed to write LLVM IR: " + ex.getMessage());
            lastLlvmModule = null;
            lastLlvmOutputPath = null;
        } catch (RuntimeException ex) {
            System.out.println("Failed to generate LLVM IR: " + ex.getMessage());
            lastLlvmModule = null;
            lastLlvmOutputPath = null;
        }
    }

    private String replaceExtension(String sourceName, String newExtension) {
        if (sourceName == null) {
            return newExtension;
        }
        int dot = sourceName.lastIndexOf('.');
        if (dot >= 0) {
            return sourceName.substring(0, dot) + newExtension;
        }
        return sourceName + newExtension;
    }
}
