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
import Triangle.CodeGenerator.LLVM.NativeToolchain;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Objects;



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

    public boolean hasLlvmModule() {
        return lastLlvmModule != null && !lastLlvmModule.isEmpty();
    }

    public void saveLlvmModuleTo(File target) throws IOException {
        Objects.requireNonNull(target, "target");
        if (!hasLlvmModule()) {
            throw new IllegalStateException("No LLVM module has been generated");
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create directory " + parent);
        }
        FileWriter writer = new FileWriter(target);
        try {
            writer.write(lastLlvmModule);
        } finally {
            writer.close();
        }
        lastLlvmOutputPath = target.getAbsolutePath();
        System.out.println("LLVM IR written to " + lastLlvmOutputPath);
    }

    public String getLastNativeExecutablePath() {
        return lastNativeExecutablePath;
    }

    public void clearNativeArtifacts() {
        lastNativeExecutablePath = null;
    }

    public boolean hasNativeExecutable() {
        if (lastNativeExecutablePath == null || lastNativeExecutablePath.isEmpty()) {
            return false;
        }
        File executable = new File(lastNativeExecutablePath);
        return executable.isFile();
    }

    public NativeToolchain.Result compileNativeExecutable()
        throws IOException, InterruptedException {
        if (!hasLlvmModule()) {
            throw new IllegalStateException("No LLVM module has been generated");
        }
        File irFile = ensureLlvmModuleOnDisk();
        File nativeFile = resolveNativeExecutableFile(irFile);
        NativeToolchain.Request request = NativeToolchain.Request
            .newBuilder(irFile.toPath(), nativeFile.toPath())
            .setOptimizationLevel(llvmRequest.optimizationLevel())
            .build();
        NativeToolchain.Result result = NativeToolchain.compileToNative(request);
        if (result.isSuccess()) {
            lastNativeExecutablePath = nativeFile.getAbsolutePath();
        }
        return result;
    }

    public NativeToolchain.Result compileNativeAssemblyTo(File target)
        throws IOException, InterruptedException {
        if (!hasLlvmModule()) {
            throw new IllegalStateException("No LLVM module has been generated");
        }
        if (target == null) {
            throw new IllegalArgumentException("target");
        }
        File irFile = ensureLlvmModuleOnDisk();
        File outFile = ensureFileWithExtension(target, defaultAssemblyExtension());
        NativeToolchain.Request request = NativeToolchain.Request
            .newBuilder(irFile.toPath(), outFile.toPath())
            .setOptimizationLevel(llvmRequest.optimizationLevel())
            .build();
        NativeToolchain.Result result = NativeToolchain.compileToAssembly(request);
        if (result.isSuccess()) {
            lastNativeAssemblyPath = outFile.getAbsolutePath();
        }
        return result;
    }

    public NativeToolchain.Result compileNativeObjectTo(File target)
        throws IOException, InterruptedException {
        if (!hasLlvmModule()) {
            throw new IllegalStateException("No LLVM module has been generated");
        }
        if (target == null) {
            throw new IllegalArgumentException("target");
        }
        File irFile = ensureLlvmModuleOnDisk();
        File outFile = ensureFileWithExtension(target, defaultObjectExtension());
        NativeToolchain.Request request = NativeToolchain.Request
            .newBuilder(irFile.toPath(), outFile.toPath())
            .setOptimizationLevel(llvmRequest.optimizationLevel())
            .build();
        NativeToolchain.Result result = NativeToolchain.compileToObject(request);
        if (result.isSuccess()) {
            lastNativeObjectPath = outFile.getAbsolutePath();
        }
        return result;
    }

    public String getLastNativeObjectPath() {
        return lastNativeObjectPath;
    }

    public String getLastNativeAssemblyPath() {
        return lastNativeAssemblyPath;
    }

    public File getNativeExecutableFile() {
        return (lastNativeExecutablePath != null) ? new File(lastNativeExecutablePath) : null;
    }

    private File ensureLlvmModuleOnDisk() throws IOException {
        if (!hasLlvmModule()) {
            throw new IllegalStateException("No LLVM module has been generated");
        }
        if (lastLlvmOutputPath == null || lastLlvmOutputPath.isEmpty()) {
            throw new IOException("No target path available for the LLVM module");
        }
        File irFile = new File(lastLlvmOutputPath);
        saveLlvmModuleTo(irFile);
        return irFile;
    }

    private File resolveNativeExecutableFile(File irFile) throws IOException {
        if (irFile == null) {
            throw new IllegalArgumentException("irFile");
        }
        String targetPath = lastNativeExecutablePath;
        if (targetPath == null || targetPath.isEmpty()) {
            targetPath = replaceExtension(irFile.getAbsolutePath(), defaultNativeExtension());
        }
        File executable = new File(targetPath);
        File parent = executable.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create directory " + parent);
        }
        return executable;
    }

    private String defaultNativeExtension() {
        return isWindows() ? ".exe" : "";
    }

    private String defaultAssemblyExtension() {
        // Clang uses .s for assembly files on all platforms by default
        return ".s";
    }

    private String defaultObjectExtension() {
        return isWindows() ? ".obj" : ".o";
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase().contains("win");
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc=" Attributes ">
    private Program rootAST;        // The Root Abstract Syntax Tree.    
    private IDEReporter report;     // Our ErrorReporter class.
    private boolean emitLlvm;
    private LLVMGenerator.Request llvmRequest = LLVMGenerator.Request.defaults();
    private String lastLlvmModule;
    private String lastLlvmOutputPath;
    private String lastNativeExecutablePath;
    private String lastNativeAssemblyPath;
    private String lastNativeObjectPath;
    // </editor-fold>

    private void generateLlvmModule(String sourceName, Program program) {
        try {
            LLVMGenerator generator = new LLVMGenerator();
            LLVMGenerator.Result result = generator.generate(program, llvmRequest);
            lastLlvmModule = result.irModule();
            if (lastLlvmModule == null) {
                lastLlvmModule = "";
            }
            File suggestedPath = new File(replaceExtension(sourceName, ".ll"));
            lastLlvmOutputPath = suggestedPath.getAbsolutePath();
            lastNativeExecutablePath = null;
            System.out.println("LLVM IR module generated. Use Save LLVM to export it.");
        } catch (RuntimeException ex) {
            System.out.println("Failed to generate LLVM IR: " + ex.getMessage());
            lastLlvmModule = null;
            lastLlvmOutputPath = null;
            lastNativeExecutablePath = null;
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

    private static File ensureFileWithExtension(File target, String extension) throws IOException {
        File selected = target;
        if (selected == null) {
            throw new IllegalArgumentException("target");
        }
        String name = selected.getName().toLowerCase();
        if (!name.endsWith(extension)) {
            selected = new File(selected.getParentFile(), selected.getName() + extension);
        }
        File parent = selected.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create directory " + parent);
        }
        return selected;
    }

    // Optimization level control for UI dialog
    public String getOptimizationLevelName() {
        return String.valueOf(llvmRequest.optimizationLevel());
    }

    public void setOptimizationLevelByName(String name) {
        if (name == null || name.isEmpty()) return;
        try {
            Triangle.CodeGenerator.LLVM.LLVMGenerator.Request.OptimizationLevel lvl = Triangle.CodeGenerator.LLVM.LLVMGenerator.Request.OptimizationLevel.valueOf(name);
            this.llvmRequest = llvmRequest.withOptimizationLevel(lvl);
        } catch (IllegalArgumentException ignored) {
            // keep previous level if parsing fails
        }
    }
}
