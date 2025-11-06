package Triangle.CodeGenerator.LLVM;

import Triangle.AbstractSyntaxTrees.*;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Scaffold for the upcoming LLVM backend. The class exposes the shape of the request/response
 * objects so the rest of the compiler can start integrating while the visitor implementation is
 * developed.
 */
public final class LLVMGenerator {

  /**
   * Immutable settings used when requesting LLVM code generation.
   */
  public static final class Request {

    /**
     * Optimization presets passed to the LLVM toolchain.
     */
    public enum OptimizationLevel {
      NONE,
      O1,
      O2,
      O3
    }

    private final OptimizationLevel optimizationLevel;
    private final boolean emitObjectFile;
    private final boolean emitAssembly;

    private Request(OptimizationLevel optimizationLevel, boolean emitObjectFile,
        boolean emitAssembly) {
      this.optimizationLevel = optimizationLevel;
      this.emitObjectFile = emitObjectFile;
      this.emitAssembly = emitAssembly;
    }

    public static Request defaults() {
      return new Request(OptimizationLevel.NONE, false, false);
    }

    public OptimizationLevel optimizationLevel() {
      return optimizationLevel;
    }

    public boolean emitObjectFile() {
      return emitObjectFile;
    }

    public boolean emitAssembly() {
      return emitAssembly;
    }

    public Request withOptimizationLevel(OptimizationLevel level) {
      return new Request(level, emitObjectFile, emitAssembly);
    }

    public Request withEmitObjectFile(boolean value) {
      return new Request(optimizationLevel, value, emitAssembly);
    }

    public Request withEmitAssembly(boolean value) {
      return new Request(optimizationLevel, emitObjectFile, value);
    }
  }

  /**
   * Result of LLVM code generation. File path fields remain null when the corresponding artifact was
   * not requested.
   */
  public static final class Result {

    private final String irModule;
    private final String objectFilePath;
    private final String assemblyFilePath;

    private Result(String irModule, String objectFilePath, String assemblyFilePath) {
      this.irModule = irModule;
      this.objectFilePath = objectFilePath;
      this.assemblyFilePath = assemblyFilePath;
    }

    public static Result empty() {
      return new Result("", null, null);
    }

    public String irModule() {
      return irModule;
    }

    public String objectFilePath() {
      return objectFilePath;
    }

    public String assemblyFilePath() {
      return assemblyFilePath;
    }
  }

  public LLVMGenerator() {
  }

  public Result generate(Program program, Request request) {
    Objects.requireNonNull(program, "program");
    Request effectiveRequest = (request != null) ? request : Request.defaults();

    LlvmModuleGenerator generator = new LlvmModuleGenerator(effectiveRequest);
    String ir = generator.generate(program);
    return new Result(ir, null, null);
  }

  // --- Minimal LLVM backend implementation ---
  private static final class LlvmModuleGenerator {

    private final Request request;
    private final ModuleBuilder module;
    private final SymbolTable symbols = new SymbolTable();
    private final NameMangler mangler = new NameMangler();
    private final Builtins builtins;
    private final Map<FuncDeclaration, FunctionInfo> functionCache = new IdentityHashMap<FuncDeclaration, FunctionInfo>();

    LlvmModuleGenerator(Request request) {
      this.request = request;
      this.module = ModuleBuilder.hostDefaults("triangle");
      this.builtins = new Builtins(module);
      symbols.pushScope();
      builtins.register(symbols);
    }

    String generate(Program program) {
      module.addPrologue("source_filename = \"triangle\"");
      module.addPrologue("; Triangle LLVM backend");
      module.addPrologue("; optimization level: " + request.optimizationLevel());

      // Promote top-level var/const to globals before building main.
      collectTopLevelGlobals(program);

      // int main() { <compile commands>; return 0; }
      ModuleBuilder.FunctionBuilder mainBuilder = module.newFunction("define i32 @main()");
      FunctionContext mainCtx = new FunctionContext(mainBuilder);
      mainCtx.emitLabel("entry");

      symbols.pushScope();
      if (program.C != null) {
        compileCommand(program.C, mainCtx);
      }
      symbols.popScope();

      mainCtx.emit("ret i32 0");
      mainCtx.seal();

      symbols.popScope();
      return module.buildModule();
    }

    private void collectTopLevelGlobals(Program program) {
      // We scan the Program's command for leading Let declarations used as setup; for simplicity
      // we only promote simple VarDeclaration / ConstDeclaration at the outermost level (no nested lets).
      if (program == null || program.C == null) return;
      // A heuristic: if the top-level command starts with a LetCommand, harvest its declarations recursively.
      harvestGlobalsFromCommand(program.C);
    }

    private void harvestGlobalsFromCommand(Command command) {
      if (command instanceof LetCommand) {
        LetCommand let = (LetCommand) command;
        harvestGlobalsFromDeclaration(let.D);
        // Continue into inner command to catch chained lets.
        harvestGlobalsFromCommand(let.C);
      } else if (command instanceof SequentialCommand) {
        SequentialCommand seq = (SequentialCommand) command;
        harvestGlobalsFromCommand(seq.C1);
        harvestGlobalsFromCommand(seq.C2);
      }
    }

    private void harvestGlobalsFromDeclaration(Declaration decl) {
      if (decl instanceof SequentialDeclaration) {
        SequentialDeclaration seq = (SequentialDeclaration) decl;
        harvestGlobalsFromDeclaration(seq.D1);
        harvestGlobalsFromDeclaration(seq.D2);
        return;
      }
      if (decl instanceof ConstDeclaration) {
        ConstDeclaration c = (ConstDeclaration) decl;
        // We can only evaluate simple integer expressions at global init time for now.
        Integer initValue = tryEvaluateIntExpression(c.E);
        if (initValue != null) {
          String gName = globalName(c.I.spelling);
          module.addGlobal(gName + " = constant i32 " + initValue);
          symbols.define(new VariableSymbol(c.I.spelling, TypeInfo.INT, gName, true));
        }
        return;
      }
      if (decl instanceof VarDeclaration) {
        VarDeclaration v = (VarDeclaration) decl;
        // Default initialize to 0; only int supported globally for now.
        String gName = globalName(v.I.spelling);
        module.addGlobal(gName + " = global i32 0");
        symbols.define(new VariableSymbol(v.I.spelling, TypeInfo.INT, gName, false));
        return;
      }
      // Ignore other forms (functions stay local to codegen path, let blocks deeper not promoted)
    }

    private String globalName(String source) {
      return "@g$" + mangler.mangle(source);
    }

    private Integer tryEvaluateIntExpression(Expression e) {
      if (e instanceof IntegerExpression) {
        IntegerExpression ie = (IntegerExpression) e;
        if (ie.IL != null) {
          try { return Integer.parseInt(ie.IL.spelling); } catch (NumberFormatException ex) { return null; }
        }
        return 0;
      }
      return null; // Non-constant or unsupported expression
    }

    private void compileCommand(Command command, FunctionContext ctx) {
      if (command == null || command instanceof EmptyCommand) {
        return;
      }
      if (command instanceof SequentialCommand) {
        SequentialCommand seq = (SequentialCommand) command;
        compileCommand(seq.C1, ctx);
        compileCommand(seq.C2, ctx);
        return;
      }
      if (command instanceof LetCommand) {
        LetCommand let = (LetCommand) command;
        symbols.pushScope();
        compileDeclaration(let.D, ctx);
        compileCommand(let.C, ctx);
        symbols.popScope();
        return;
      }
      if (command instanceof CallCommand) {
        CallCommand call = (CallCommand) command;
        compileCall(ctx, call.I, call.APS, false);
        return;
      }
      if (command instanceof AssignCommand) {
        // V := E
        AssignCommand assign = (AssignCommand) command;
        ValueRef value = compileExpression(assign.E, ctx);
        VariableSymbol target = resolveVariable(assign.V);
        // For now only simple variables (no field/array selectors yet)
        ctx.emit("store " + value.type.llvm + " " + value.ref + ", " + value.type.llvm + "* " + target.pointer);
        return;
      }
      if (command instanceof IfCommand) {
        IfCommand ic = (IfCommand) command;
        // Compile condition (treat non‑zero as true). For now we assume integer conditions.
        ValueRef condVal = compileExpression(ic.E, ctx);
        String zeroCmp = ctx.newTemp();
        ctx.emit(zeroCmp + " = icmp ne " + condVal.type.llvm + " " + condVal.ref + ", 0");
        String thenLabel = ctx.newLabel("if.then");
        String elseLabel = ctx.newLabel("if.else");
        String endLabel = ctx.newLabel("if.end");
        boolean hasElse = ic.C2 != null && !(ic.C2 instanceof EmptyCommand);
        ctx.emit("br i1 " + zeroCmp + ", label %" + thenLabel + ", label %" + (hasElse ? elseLabel : endLabel));
        // then block
        ctx.emitLabel(thenLabel);
        compileCommand(ic.C1, ctx);
        ctx.emit("br label %" + endLabel);
        // else block
        if (hasElse) {
          ctx.emitLabel(elseLabel);
            compileCommand(ic.C2, ctx);
            ctx.emit("br label %" + endLabel);
        }
        // end
        ctx.emitLabel(endLabel);
        return;
      }
      if (command instanceof WhileCommand) {
        WhileCommand wc = (WhileCommand) command;
        String condLabel = ctx.newLabel("while.cond");
        String bodyLabel = ctx.newLabel("while.body");
        String endLabel = ctx.newLabel("while.end");
        // jump to condition first
        ctx.emit("br label %" + condLabel);
        ctx.emitLabel(condLabel);
        ValueRef condVal = compileExpression(wc.E, ctx);
        String cmp = ctx.newTemp();
        ctx.emit(cmp + " = icmp ne " + condVal.type.llvm + " " + condVal.ref + ", 0");
        ctx.emit("br i1 " + cmp + ", label %" + bodyLabel + ", label %" + endLabel);
        ctx.emitLabel(bodyLabel);
        compileCommand(wc.C, ctx);
        ctx.emit("br label %" + condLabel);
        ctx.emitLabel(endLabel);
        return;
      }
      throw new UnsupportedOperationException("Unsupported command: " + command.getClass().getSimpleName());
    }

    private VariableSymbol resolveVariable(Vname V) {
      // Only simple identifier vnames supported right now.
      if (V instanceof SimpleVname) {
        SimpleVname sv = (SimpleVname) V;
        Symbol sym = symbols.lookup(sv.I.spelling);
        if (sym instanceof VariableSymbol) {
          return (VariableSymbol) sym;
        }
        throw new IllegalStateException("'" + sv.I.spelling + "' no es una variable");
      }
      throw new UnsupportedOperationException("Solo variables simples soportadas por ahora");
    }

    private void compileDeclaration(Declaration declaration, FunctionContext ctx) {
      if (declaration == null) {
        return;
      }
      if (declaration instanceof SequentialDeclaration) {
        SequentialDeclaration seq = (SequentialDeclaration) declaration;
        compileDeclaration(seq.D1, ctx);
        compileDeclaration(seq.D2, ctx);
        return;
      }
      if (declaration instanceof ConstDeclaration) {
        ConstDeclaration c = (ConstDeclaration) declaration;
        ValueRef value = compileExpression(c.E, ctx);
        String ptr = ctx.createAlloca(value.type);
        ctx.emit("store " + value.type.llvm + " " + value.ref + ", " + value.type.llvm + "* " + ptr);
        symbols.define(new VariableSymbol(c.I.spelling, value.type, ptr, true));
        return;
      }
      if (declaration instanceof VarDeclaration) {
        VarDeclaration v = (VarDeclaration) declaration;
        TypeInfo type = typeInfoFor(v.T);
        String ptr = ctx.createAlloca(type);
        ctx.emit("store " + type.llvm + " " + defaultValue(type) + ", " + type.llvm + "* " + ptr);
        symbols.define(new VariableSymbol(v.I.spelling, type, ptr, false));
        return;
      }
      if (declaration instanceof FuncDeclaration) {
        FuncDeclaration f = (FuncDeclaration) declaration;
        FunctionInfo info = ensureFunctionInfo(f);
        symbols.define(new FunctionSymbol(f.I.spelling, info));
        defineFunctionBody(info, f);
        return;
      }
      // Other declaration forms are not supported yet
      throw new UnsupportedOperationException("Unsupported declaration: " + declaration.getClass().getSimpleName());
    }

    private FunctionInfo ensureFunctionInfo(FuncDeclaration decl) {
      FunctionInfo cached = functionCache.get(decl);
      if (cached != null) {
        return cached;
      }
      String base = (decl.I != null) ? decl.I.spelling : "anon";
      String llvmName = "@triangle$" + mangler.mangle(base);
      TypeInfo ret = typeInfoFor(decl.T);
      List<ParameterSpec> params = collectFormalParameters(decl.FPS);
      List<TypeInfo> paramTypes = new ArrayList<TypeInfo>();
      for (ParameterSpec p : params) {
        paramTypes.add(p.type);
      }
      FunctionInfo info = new FunctionInfo(base, llvmName, ret, paramTypes, params, false, decl);
      functionCache.put(decl, info);
      return info;
    }

    private List<ParameterSpec> collectFormalParameters(FormalParameterSequence seq) {
      List<ParameterSpec> specs = new ArrayList<ParameterSpec>();
      collectFormalParameters(seq, specs);
      return specs;
    }

    private void collectFormalParameters(FormalParameterSequence seq, List<ParameterSpec> out) {
      if (seq == null || seq instanceof EmptyFormalParameterSequence) {
        return;
      }
      if (seq instanceof SingleFormalParameterSequence) {
        SingleFormalParameterSequence s = (SingleFormalParameterSequence) seq;
        out.add(parameterSpec(s.FP));
        return;
      }
      if (seq instanceof MultipleFormalParameterSequence) {
        MultipleFormalParameterSequence m = (MultipleFormalParameterSequence) seq;
        out.add(parameterSpec(m.FP));
        collectFormalParameters(m.FPS, out);
        return;
      }
      throw new UnsupportedOperationException("Unsupported parameter sequence: " + seq.getClass().getSimpleName());
    }

    private ParameterSpec parameterSpec(FormalParameter fp) {
      if (fp instanceof ConstFormalParameter) {
        ConstFormalParameter c = (ConstFormalParameter) fp;
        TypeInfo type = typeInfoFor(c.T);
        return new ParameterSpec(c.I.spelling, type);
      }
      if (fp instanceof FuncFormalParameter) {
        FuncFormalParameter f = (FuncFormalParameter) fp;
        List<ParameterSpec> nested = collectFormalParameters(f.FPS);
        List<TypeInfo> nestedTypes = new ArrayList<TypeInfo>();
        for (ParameterSpec s : nested) {
          nestedTypes.add(s.type);
        }
        TypeInfo ret = typeInfoFor(f.T);
        TypeInfo type = TypeInfo.functionPointer(ret, nestedTypes);
        return new ParameterSpec(f.I.spelling, type);
      }
      throw new UnsupportedOperationException("Unsupported formal parameter: " + fp.getClass().getSimpleName());
    }

    private void defineFunctionBody(FunctionInfo info, FuncDeclaration decl) {
      if (info.defined) {
        return;
      }
      StringBuilder sig = new StringBuilder();
      sig.append("define ").append(info.returnType.llvm).append(" ")
         .append(info.llvmName).append("(");
      for (int i = 0; i < info.parameters.size(); i++) {
        if (i > 0) sig.append(", ");
        sig.append(info.parameters.get(i).type.llvm).append(" %arg").append(i);
      }
      sig.append(")");

      ModuleBuilder.FunctionBuilder fn = module.newFunction(sig.toString());
      FunctionContext ctx = new FunctionContext(fn);
      ctx.emitLabel("entry");

      symbols.pushScope();
      for (int i = 0; i < info.parameters.size(); i++) {
        ParameterSpec p = info.parameters.get(i);
        String ptr = ctx.createAlloca(p.type);
        ctx.emit("store " + p.type.llvm + " %arg" + i + ", " + p.type.llvm + "* " + ptr);
        symbols.define(new VariableSymbol(p.name, p.type, ptr, true));
      }

      ValueRef result = compileExpression(decl.E, ctx);
      ctx.emit("ret " + info.returnType.llvm + " " + result.ref);

      symbols.popScope();
      ctx.seal();
      info.defined = true;
    }

    private ValueRef compileExpression(Expression e, FunctionContext ctx) {
      if (e instanceof IntegerExpression) {
        IntegerExpression ie = (IntegerExpression) e;
        int value = 0;
        if (ie.IL != null) {
          try { value = Integer.parseInt(ie.IL.spelling); } catch (NumberFormatException ex) {
            throw new IllegalStateException("Invalid integer literal: " + ie.IL.spelling, ex);
          }
        }
        return new ValueRef(TypeInfo.INT, Integer.toString(value));
      }
      if (e instanceof VnameExpression) {
        VnameExpression ve = (VnameExpression) e;
        return loadVname(ve.V, ctx);
      }
      if (e instanceof BinaryExpression) {
        BinaryExpression be = (BinaryExpression) e;
        ValueRef left = compileExpression(be.E1, ctx);
        ValueRef right = compileExpression(be.E2, ctx);
        return emitBinary(be.O, left, right, ctx);
      }
      if (e instanceof CallExpression) {
        CallExpression ce = (CallExpression) e;
        return compileCall(ctx, ce.I, ce.APS, true);
      }
      if (e instanceof LetExpression) {
        LetExpression le = (LetExpression) e;
        symbols.pushScope();
        compileDeclaration(le.D, ctx);
        ValueRef v = compileExpression(le.E, ctx);
        symbols.popScope();
        return v;
      }
      throw new UnsupportedOperationException("Unsupported expression: " + e.getClass().getSimpleName());
    }

    private ValueRef emitBinary(Operator op, ValueRef left, ValueRef right, FunctionContext ctx) {
      String o = (op != null) ? op.spelling : "";
      // Arithmetic
      if ("+".equals(o) || "-".equals(o) || "*".equals(o) || "/".equals(o)) {
        String instr;
        if ("+".equals(o)) instr = "add";
        else if ("-".equals(o)) instr = "sub";
        else if ("*".equals(o)) instr = "mul";
        else instr = "sdiv"; // '/'
        String t = ctx.newTemp();
        ctx.emit(t + " = " + instr + " " + left.type.llvm + " " + left.ref + ", " + right.ref);
        return new ValueRef(left.type, t);
      }
      // Relational -> produce i32 0/1 for now (Triangle booleans still lowered to int)
      if ("<".equals(o) || "<=".equals(o) || ">".equals(o) || ">=".equals(o) || "=".equals(o) || "!=".equals(o)) {
        String pred;
        if ("<".equals(o)) pred = "slt";
        else if ("<=".equals(o)) pred = "sle";
        else if (">".equals(o)) pred = "sgt";
        else if (">=".equals(o)) pred = "sge";
        else if ("=".equals(o)) pred = "eq";
        else pred = "ne";
        String boolTemp = ctx.newTemp();
        ctx.emit(boolTemp + " = icmp " + pred + " " + left.type.llvm + " " + left.ref + ", " + right.ref);
        // Extend i1 -> i32 so the rest of pipeline treats booleans as ints
        String ext = ctx.newTemp();
        ctx.emit(ext + " = zext i1 " + boolTemp + " to i32");
        return new ValueRef(TypeInfo.INT, ext);
      }
      throw new UnsupportedOperationException("Unsupported binary operator: " + o);
    }

    private ValueRef compileCall(FunctionContext ctx, Identifier id, ActualParameterSequence aps, boolean expectResult) {
      String name = (id != null) ? id.spelling : "";
      if (builtins.isBuiltin(name)) {
        List<ValueRef> args = collectActualParameters(aps, ctx);
        return builtins.emit(name, args, expectResult, ctx);
      }
      CallableTarget target = resolveCallable(name, ctx);
      List<ValueRef> args = collectActualParameters(aps, ctx);
      StringBuilder call = new StringBuilder();
      if (!target.info.returnType.isVoid()) {
        String t = ctx.newTemp();
        call.append(t).append(" = ");
        call.append("call ").append(target.info.returnType.llvm).append(" ")
            .append(target.pointer).append("(");
        appendArguments(call, args);
        call.append(")");
        ctx.emit(call.toString());
        return new ValueRef(target.info.returnType, t);
      }
      call.append("call ").append(target.info.returnType.llvm).append(" ")
          .append(target.pointer).append("(");
      appendArguments(call, args);
      call.append(")");
      ctx.emit(call.toString());
      return ValueRef.VOID;
    }

    private void appendArguments(StringBuilder call, List<ValueRef> args) {
      for (int i = 0; i < args.size(); i++) {
        if (i > 0) call.append(", ");
        ValueRef a = args.get(i);
        call.append(a.type.llvm).append(" ").append(a.ref);
      }
    }

    private CallableTarget resolveCallable(String name, FunctionContext ctx) {
      Symbol sym = symbols.lookup(name);
      if (sym instanceof FunctionSymbol) {
        FunctionSymbol fs = (FunctionSymbol) sym;
        return new CallableTarget(fs.info, fs.info.llvmName);
      }
      if (sym instanceof VariableSymbol) {
        VariableSymbol vs = (VariableSymbol) sym;
        if (!vs.type.isFunctionPointer()) {
          throw new IllegalStateException("Symbol '" + name + "' is not callable");
        }
        ValueRef ptr = loadPointer(vs, ctx);
        return new CallableTarget(FunctionInfo.forPointer(vs.type), ptr.ref);
      }
      throw new IllegalStateException("Unknown function: " + name);
    }

    private List<ValueRef> collectActualParameters(ActualParameterSequence seq, FunctionContext ctx) {
      List<ValueRef> values = new ArrayList<ValueRef>();
      collectActualParameters(seq, values, ctx);
      return values;
    }

    private void collectActualParameters(ActualParameterSequence seq, List<ValueRef> out, FunctionContext ctx) {
      if (seq == null || seq instanceof EmptyActualParameterSequence) {
        return;
      }
      if (seq instanceof SingleActualParameterSequence) {
        SingleActualParameterSequence s = (SingleActualParameterSequence) seq;
        out.add(compileActualParameter(s.AP, ctx));
        return;
      }
      if (seq instanceof MultipleActualParameterSequence) {
        MultipleActualParameterSequence m = (MultipleActualParameterSequence) seq;
        out.add(compileActualParameter(m.AP, ctx));
        collectActualParameters(m.APS, out, ctx);
        return;
      }
      throw new UnsupportedOperationException("Unsupported actual parameter sequence: " + seq.getClass().getSimpleName());
    }

    private ValueRef compileActualParameter(ActualParameter ap, FunctionContext ctx) {
      if (ap instanceof ConstActualParameter) {
        ConstActualParameter c = (ConstActualParameter) ap;
        return compileExpression(c.E, ctx);
      }
      if (ap instanceof FuncActualParameter) {
        FuncActualParameter f = (FuncActualParameter) ap;
        return resolveFunctionPointer(f.I);
      }
      throw new UnsupportedOperationException("Unsupported actual parameter: " + ap.getClass().getSimpleName());
    }

    private ValueRef resolveFunctionPointer(Identifier id) {
      String name = (id != null) ? id.spelling : "";
      Symbol sym = symbols.lookup(name);
      if (sym instanceof FunctionSymbol) {
        FunctionSymbol fs = (FunctionSymbol) sym;
        return new ValueRef(fs.info.pointerType(), fs.info.llvmName);
      }
      if (sym instanceof VariableSymbol) {
        VariableSymbol vs = (VariableSymbol) sym;
        if (!vs.type.isFunctionPointer()) {
          throw new IllegalStateException("Symbol '" + name + "' is not a function");
        }
        throw new UnsupportedOperationException("Passing function variables is not yet supported");
      }
      throw new IllegalStateException("Unknown function: " + name);
    }

    private ValueRef loadVname(Vname v, FunctionContext ctx) {
      if (v instanceof SimpleVname) {
        SimpleVname s = (SimpleVname) v;
        String name = (s.I != null) ? s.I.spelling : "";
        Symbol sym = symbols.lookup(name);
        if (sym instanceof VariableSymbol) {
          return loadVariable((VariableSymbol) sym, ctx);
        }
        if (sym instanceof FunctionSymbol) {
          FunctionSymbol fs = (FunctionSymbol) sym;
          return new ValueRef(fs.info.pointerType(), fs.info.llvmName);
        }
        throw new IllegalStateException("Unknown identifier: " + name);
      }
      throw new UnsupportedOperationException("Unsupported vname: " + v.getClass().getSimpleName());
    }

    private ValueRef loadVariable(VariableSymbol var, FunctionContext ctx) {
      String t = ctx.newTemp();
      ctx.emit(t + " = load " + var.type.llvm + ", " + var.type.llvm + "* " + var.pointer);
      return new ValueRef(var.type, t);
    }

    private ValueRef loadPointer(VariableSymbol var, FunctionContext ctx) {
      String t = ctx.newTemp();
      ctx.emit(t + " = load " + var.type.llvm + ", " + var.type.llvm + "* " + var.pointer);
      return new ValueRef(var.type, t);
    }

    private TypeInfo typeInfoFor(TypeDenoter t) {
      if (t == null || t instanceof IntTypeDenoter) return TypeInfo.INT;
      if (t instanceof CharTypeDenoter) return TypeInfo.CHAR;
      if (t instanceof BoolTypeDenoter) return TypeInfo.BOOL;
      throw new UnsupportedOperationException("Unsupported type denoter: " + (t != null ? t.getClass().getSimpleName() : "null"));
    }

    private String defaultValue(TypeInfo t) {
      if (t == TypeInfo.INT || t == TypeInfo.BOOL || t == TypeInfo.CHAR) return "0";
      throw new UnsupportedOperationException("Unsupported default value for type: " + t);
    }
  }

  private static final class FunctionContext {
    private final ModuleBuilder.FunctionBuilder builder;
    private int tempCounter = 0;
    private int labelCounter = 0;

    FunctionContext(ModuleBuilder.FunctionBuilder builder) { this.builder = builder; }
    void emitLabel(String label) { builder.emitLabel(label); }
    void emit(String line) { builder.emit(line); }
    String newTemp() { return "%t" + (tempCounter++); }
    String newLabel(String prefix) { return prefix + "." + (labelCounter++); }
    String createAlloca(TypeInfo type) { String p = newTemp(); emit(p + " = alloca " + type.llvm); return p; }
    ValueRef constantPointer(ModuleBuilder.InternedString lit) {
      String t = newTemp();
      emit(t + " = getelementptr inbounds " + lit.arrayType() + ", " + lit.arrayType() + "* " + lit.symbol() + ", i32 0, i32 0");
      return new ValueRef(TypeInfo.I8_PTR, t);
    }
    void seal() { builder.seal(); }
  }

  private static final class CallableTarget { final FunctionInfo info; final String pointer; CallableTarget(FunctionInfo i, String p){info=i;pointer=p;} }
  private static final class ValueRef { static final ValueRef VOID = new ValueRef(TypeInfo.VOID, "void"); final TypeInfo type; final String ref; ValueRef(TypeInfo t,String r){type=t;ref=r;} }
  private static final class ParameterSpec { final String name; final TypeInfo type; ParameterSpec(String n, TypeInfo t){ this.name = n; this.type = t; } }

  private static final class FunctionInfo {
    final String sourceName; final String llvmName; final TypeInfo returnType; final List<TypeInfo> parameterTypes; final List<ParameterSpec> parameters; final boolean builtin; final FuncDeclaration owner; boolean defined;
    FunctionInfo(String s, String l, TypeInfo r, List<TypeInfo> pts, List<ParameterSpec> ps, boolean b, FuncDeclaration o){sourceName=s;llvmName=l;returnType=r;parameterTypes=pts;parameters=ps;builtin=b;owner=o;}
    TypeInfo pointerType(){ return TypeInfo.functionPointer(returnType, parameterTypes); }
    static FunctionInfo forPointer(TypeInfo t){ return new FunctionInfo("<pointer>", "", t.returnType, t.parameterTypes, Collections.<ParameterSpec>emptyList(), false, null); }
  }

  private static final class TypeInfo {
    static final TypeInfo INT = new TypeInfo("i32");
    static final TypeInfo CHAR = new TypeInfo("i8");
    static final TypeInfo BOOL = new TypeInfo("i1");
    static final TypeInfo VOID = new TypeInfo("void");
    static final TypeInfo I8_PTR = new TypeInfo("i8*");
    final String llvm; final TypeInfo returnType; final List<TypeInfo> parameterTypes;
    private TypeInfo(String l){ this(l, null, Collections.<TypeInfo>emptyList()); }
    private TypeInfo(String l, TypeInfo r, List<TypeInfo> ps){ llvm=l; returnType=r; parameterTypes=ps; }
    static TypeInfo functionPointer(TypeInfo r, List<TypeInfo> ps){ StringBuilder sb=new StringBuilder(); sb.append(r.llvm).append(" ("); for(int i=0;i<ps.size();i++){ if(i>0) sb.append(", "); sb.append(ps.get(i).llvm);} sb.append(")*"); return new TypeInfo(sb.toString(), r, ps); }
    boolean isFunctionPointer(){ return returnType != null; }
    boolean isVoid(){ return this == VOID; }
  }

  private static final class Builtins {
    private final ModuleBuilder module;
    private final ModuleBuilder.InternedString intPrintFormat;
    private final ModuleBuilder.InternedString intScanFormat;
    private final FunctionInfo putIntInfo;
    private final FunctionInfo getIntInfo;
    private final FunctionInfo getCharInfo;
    private final FunctionInfo putCharInfo;

    Builtins(ModuleBuilder m){
      module = m;
  // Use real newline in the Java literal; ModuleBuilder will encode it as \0A for LLVM IR
  intPrintFormat = module.internStringLiteral("%d\n");
      intScanFormat = module.internStringLiteral("%d");
      module.addDeclaration("declare i32 @printf(i8*, ...)");
      module.addDeclaration("declare i32 @scanf(i8*, ...)");
      module.addDeclaration("declare i32 @getchar()");
      module.addDeclaration("declare i32 @putchar(i32)");
      putIntInfo = new FunctionInfo("putint", "@triangle$builtin.putint", TypeInfo.VOID, Collections.<TypeInfo>singletonList(TypeInfo.INT), Collections.<ParameterSpec>emptyList(), true, null);
      getIntInfo = new FunctionInfo("getint", "@triangle$builtin.getint", TypeInfo.INT, Collections.<TypeInfo>emptyList(), Collections.<ParameterSpec>emptyList(), true, null);
      getCharInfo = new FunctionInfo("getchar", "@triangle$builtin.getchar", TypeInfo.INT, Collections.<TypeInfo>emptyList(), Collections.<ParameterSpec>emptyList(), true, null);
      putCharInfo = new FunctionInfo("putchar", "@triangle$builtin.putchar", TypeInfo.VOID, Collections.<TypeInfo>singletonList(TypeInfo.INT), Collections.<ParameterSpec>emptyList(), true, null);
    }

    void register(SymbolTable symbols){
      symbols.define(new FunctionSymbol("putint", putIntInfo));
      symbols.define(new FunctionSymbol("getint", getIntInfo));
      symbols.define(new FunctionSymbol("getchar", getCharInfo));
      symbols.define(new FunctionSymbol("putchar", putCharInfo));
    }

    boolean isBuiltin(String name){
      return "putint".equals(name) || "getint".equals(name) || "getchar".equals(name) || "putchar".equals(name);
    }

    ValueRef emit(String name, List<ValueRef> args, boolean expectResult, FunctionContext ctx){
      if ("putint".equals(name)) {
        if (args.size() != 1) throw new IllegalStateException("putint expects exactly one argument");
        ValueRef a = args.get(0);
        ValueRef fmt = ctx.constantPointer(intPrintFormat);
        ctx.emit("call i32 @printf(i8* " + fmt.ref + ", " + a.type.llvm + " " + a.ref + ")");
        return ValueRef.VOID;
      }
      if ("getint".equals(name)) {
        if (!expectResult) throw new IllegalStateException("getint must be used in an expression");
        if (!args.isEmpty()) throw new IllegalStateException("getint takes no arguments");
        String ptr = ctx.createAlloca(TypeInfo.INT);
        ValueRef fmt = ctx.constantPointer(intScanFormat);
        ctx.emit("call i32 @scanf(i8* " + fmt.ref + ", i32* " + ptr + ")");
        String loaded = ctx.newTemp();
        ctx.emit(loaded + " = load i32, i32* " + ptr);
        return new ValueRef(TypeInfo.INT, loaded);
      }
      if ("getchar".equals(name)) {
        if (!expectResult) throw new IllegalStateException("getchar must be used in an expression");
        if (!args.isEmpty()) throw new IllegalStateException("getchar takes no arguments");
        String t = ctx.newTemp();
        ctx.emit(t + " = call i32 @getchar()");
        return new ValueRef(TypeInfo.INT, t);
      }
      if ("putchar".equals(name)) {
        if (args.size() != 1) throw new IllegalStateException("putchar expects exactly one argument");
        ValueRef a = args.get(0);
        ctx.emit("call i32 @putchar(i32 " + a.ref + ")");
        return ValueRef.VOID;
      }
      throw new UnsupportedOperationException("Unknown builtin: " + name);
    }
  }

  private static final class SymbolTable {
    private final Deque<Map<String, Symbol>> scopes = new ArrayDeque< Map<String, Symbol> >();
    void pushScope(){ scopes.push(new LinkedHashMap<String, Symbol>()); }
    void popScope(){ scopes.pop(); }
    void define(Symbol s){ Map<String, Symbol> cur = scopes.peek(); if (cur == null) throw new IllegalStateException("No active scope"); if (cur.containsKey(s.name)) throw new IllegalStateException("Duplicate symbol in scope: "+s.name); cur.put(s.name, s); }
    Symbol lookup(String name){ for (Map<String, Symbol> scope : scopes){ Symbol s = scope.get(name); if (s != null) return s; } return null; }
  }

  private abstract static class Symbol { final String name; Symbol(String n){ name=n; } }
  private static final class VariableSymbol extends Symbol { final TypeInfo type; final String pointer; final boolean constant; VariableSymbol(String n, TypeInfo t, String p, boolean c){ super(n); type=t; pointer=p; constant=c; } }
  private static final class FunctionSymbol extends Symbol { final FunctionInfo info; FunctionSymbol(String n, FunctionInfo i){ super(n); info=i; } }
  private static final class NameMangler { private final Map<String,Integer> counters = new LinkedHashMap<String,Integer>(); String mangle(String base){ Integer c = counters.get(base); int v = (c==null)?0:c.intValue(); counters.put(base, Integer.valueOf(v+1)); return (v==0)?base:(base+"$"+v); } }

  private static final class AstSummary {

    static Summary summarize(Program program) {
      AstSummary builder = new AstSummary();
      builder.appendLine("AST Triangle:");
      builder.indent++;
      if (program != null && program.C != null) {
        builder.appendLine("comandos:");
        builder.indent++;
        builder.describeCommand(program.C);
        builder.indent--;
      } else {
        builder.appendLine("(programa vacío)");
      }
      builder.indent--;
      return builder.toSummary();
    }

    private final List<String> lines = new ArrayList<String>();
    private int indent = 0;

    private void describeCommand(Command command) {
      if (command == null) {
        appendLine("(comando nulo)");
        return;
      }
      if (command instanceof SequentialCommand) {
        SequentialCommand sequential = (SequentialCommand) command;
        describeCommand(sequential.C1);
        describeCommand(sequential.C2);
      } else if (command instanceof AssignCommand) {
        AssignCommand assign = (AssignCommand) command;
        appendLine(describeVname(assign.V) + " := " + describeExpression(assign.E));
      } else if (command instanceof CallCommand) {
        CallCommand call = (CallCommand) command;
        List<String> args = new ArrayList<String>();
        collectActualParameters(call.APS, args);
        appendLine("llamar " + safeIdentifier(call.I) + "(" + joinCommaSeparated(args) + ")");
      } else if (command instanceof WhileCommand) {
        WhileCommand whileCommand = (WhileCommand) command;
        appendLine("mientras " + describeExpression(whileCommand.E) + " hacer");
        indent++;
        describeCommand(whileCommand.C);
        indent--;
        appendLine("fin mientras");
      } else if (command instanceof IfCommand) {
        IfCommand ifCommand = (IfCommand) command;
        appendLine("si " + describeExpression(ifCommand.E));
        indent++;
        describeCommand(ifCommand.C1);
        indent--;
        if (ifCommand.C2 != null && !(ifCommand.C2 instanceof EmptyCommand)) {
          appendLine("sino");
          indent++;
          describeCommand(ifCommand.C2);
          indent--;
        }
        appendLine("fin si");
      } else if (command instanceof LetCommand) {
        LetCommand letCommand = (LetCommand) command;
        appendLine("let");
        indent++;
        describeDeclaration(letCommand.D);
        indent--;
        appendLine("in");
        indent++;
        describeCommand(letCommand.C);
        indent--;
        appendLine("fin let");
      } else if (command instanceof EmptyCommand) {
        appendLine("skip");
      } else {
        appendLine("comando " + command.getClass().getSimpleName());
      }
    }

    private void describeDeclaration(Declaration declaration) {
      if (declaration == null) {
        appendLine("(declaración nula)");
        return;
      }
      if (declaration instanceof SequentialDeclaration) {
        SequentialDeclaration sequential = (SequentialDeclaration) declaration;
        describeDeclaration(sequential.D1);
        describeDeclaration(sequential.D2);
      } else if (declaration instanceof ConstDeclaration) {
        ConstDeclaration constDeclaration = (ConstDeclaration) declaration;
        appendLine("const " + safeIdentifier(constDeclaration.I) + " = "
            + describeExpression(constDeclaration.E));
      } else if (declaration instanceof VarDeclaration) {
        VarDeclaration varDeclaration = (VarDeclaration) declaration;
        appendLine("var " + safeIdentifier(varDeclaration.I) + " : "
            + describeType(varDeclaration.T));
      } else if (declaration instanceof TypeDeclaration) {
        TypeDeclaration typeDeclaration = (TypeDeclaration) declaration;
        appendLine("type " + safeIdentifier(typeDeclaration.I) + " = "
            + describeType(typeDeclaration.T));
      } else if (declaration instanceof FuncDeclaration) {
        FuncDeclaration funcDeclaration = (FuncDeclaration) declaration;
        appendLine("func " + safeIdentifier(funcDeclaration.I) + "("
            + describeFormalParameters(funcDeclaration.FPS) + ") : "
            + describeType(funcDeclaration.T));
        indent++;
        appendLine("= " + describeExpression(funcDeclaration.E));
        indent--;
        appendLine("fin func");
      } else if (declaration instanceof ProcDeclaration) {
        ProcDeclaration procDeclaration = (ProcDeclaration) declaration;
        appendLine("proc " + safeIdentifier(procDeclaration.I) + "("
            + describeFormalParameters(procDeclaration.FPS) + ")");
        indent++;
        describeCommand(procDeclaration.C);
        indent--;
        appendLine("fin proc");
      } else if (declaration instanceof UnaryOperatorDeclaration) {
        UnaryOperatorDeclaration unary = (UnaryOperatorDeclaration) declaration;
        appendLine("operator " + safeOperator(unary.O) + "(" + describeType(unary.ARG)
            + ") -> " + describeType(unary.RES));
      } else if (declaration instanceof BinaryOperatorDeclaration) {
        BinaryOperatorDeclaration binary = (BinaryOperatorDeclaration) declaration;
        appendLine("operator " + safeOperator(binary.O) + "(" + describeType(binary.ARG1)
            + ", " + describeType(binary.ARG2) + ") -> " + describeType(binary.RES));
      } else {
        appendLine("decl " + declaration.getClass().getSimpleName());
      }
    }

    private String describeFormalParameters(FormalParameterSequence sequence) {
      List<String> params = new ArrayList<String>();
      collectFormalParameters(sequence, params);
      return joinCommaSeparated(params);
    }

    private void collectFormalParameters(FormalParameterSequence sequence, List<String> out) {
      if (sequence == null || sequence instanceof EmptyFormalParameterSequence) {
        return;
      }
      if (sequence instanceof SingleFormalParameterSequence) {
        SingleFormalParameterSequence single = (SingleFormalParameterSequence) sequence;
        out.add(describeFormalParameter(single.FP));
      } else if (sequence instanceof MultipleFormalParameterSequence) {
        MultipleFormalParameterSequence multiple = (MultipleFormalParameterSequence) sequence;
        out.add(describeFormalParameter(multiple.FP));
        collectFormalParameters(multiple.FPS, out);
      }
    }

    private String describeFormalParameter(FormalParameter parameter) {
      if (parameter == null) {
        return "?";
      }
      if (parameter instanceof ConstFormalParameter) {
        ConstFormalParameter constParam = (ConstFormalParameter) parameter;
        return "const " + safeIdentifier(constParam.I) + " : " + describeType(constParam.T);
      }
      if (parameter instanceof VarFormalParameter) {
        VarFormalParameter varParam = (VarFormalParameter) parameter;
        return "var " + safeIdentifier(varParam.I) + " : " + describeType(varParam.T);
      }
      if (parameter instanceof ProcFormalParameter) {
        ProcFormalParameter procParam = (ProcFormalParameter) parameter;
        return "proc " + safeIdentifier(procParam.I) + "(" + describeFormalParameters(procParam.FPS)
            + ")";
      }
      if (parameter instanceof FuncFormalParameter) {
        FuncFormalParameter funcParam = (FuncFormalParameter) parameter;
        return "func " + safeIdentifier(funcParam.I) + "(" + describeFormalParameters(funcParam.FPS)
            + ") : " + describeType(funcParam.T);
      }
      return parameter.getClass().getSimpleName();
    }

    private void collectActualParameters(ActualParameterSequence sequence, List<String> out) {
      if (sequence == null || sequence instanceof EmptyActualParameterSequence) {
        return;
      }
      if (sequence instanceof SingleActualParameterSequence) {
        SingleActualParameterSequence single = (SingleActualParameterSequence) sequence;
        out.add(describeActualParameter(single.AP));
      } else if (sequence instanceof MultipleActualParameterSequence) {
        MultipleActualParameterSequence multiple = (MultipleActualParameterSequence) sequence;
        out.add(describeActualParameter(multiple.AP));
        collectActualParameters(multiple.APS, out);
      }
    }

    private String describeActualParameter(ActualParameter parameter) {
      if (parameter == null) {
        return "?";
      }
      if (parameter instanceof ConstActualParameter) {
        return describeExpression(((ConstActualParameter) parameter).E);
      }
      if (parameter instanceof VarActualParameter) {
        return "var " + describeVname(((VarActualParameter) parameter).V);
      }
      if (parameter instanceof ProcActualParameter) {
        return "proc " + safeIdentifier(((ProcActualParameter) parameter).I);
      }
      if (parameter instanceof FuncActualParameter) {
        return "func " + safeIdentifier(((FuncActualParameter) parameter).I);
      }
      return parameter.getClass().getSimpleName();
    }

    private String describeExpression(Expression expression) {
      if (expression == null) {
        return "?";
      }
      if (expression instanceof IntegerExpression) {
        IntegerExpression intExpr = (IntegerExpression) expression;
        return (intExpr.IL != null) ? intExpr.IL.spelling : "0";
      }
      if (expression instanceof CharacterExpression) {
        CharacterExpression charExpr = (CharacterExpression) expression;
        return (charExpr.CL != null) ? charExpr.CL.spelling : "'?'";
      }
      if (expression instanceof VnameExpression) {
        return describeVname(((VnameExpression) expression).V);
      }
      if (expression instanceof BinaryExpression) {
        BinaryExpression binary = (BinaryExpression) expression;
        return describeExpression(binary.E1) + " " + safeOperator(binary.O) + " "
            + describeExpression(binary.E2);
      }
      if (expression instanceof UnaryExpression) {
        UnaryExpression unary = (UnaryExpression) expression;
        return safeOperator(unary.O) + describeExpression(unary.E);
      }
      if (expression instanceof CallExpression) {
        CallExpression callExpr = (CallExpression) expression;
        List<String> args = new ArrayList<String>();
        collectActualParameters(callExpr.APS, args);
        return safeIdentifier(callExpr.I) + "(" + joinCommaSeparated(args) + ")";
      }
      if (expression instanceof LetExpression) {
        LetExpression letExpr = (LetExpression) expression;
        return "let {" + describeDeclarationInline(letExpr.D) + "} in "
            + describeExpression(letExpr.E);
      }
      if (expression instanceof IfExpression) {
        IfExpression ifExpr = (IfExpression) expression;
        return "if " + describeExpression(ifExpr.E1) + " then "
            + describeExpression(ifExpr.E2) + " else " + describeExpression(ifExpr.E3);
      }
      if (expression instanceof ArrayExpression) {
        ArrayExpression arrayExpr = (ArrayExpression) expression;
        return describeArrayAggregate(arrayExpr.AA);
      }
      if (expression instanceof RecordExpression) {
        RecordExpression recordExpr = (RecordExpression) expression;
        return describeRecordAggregate(recordExpr.RA);
      }
      if (expression instanceof EmptyExpression) {
        return "()";
      }
      return expression.getClass().getSimpleName();
    }

    private String describeVname(Vname vname) {
      if (vname == null) {
        return "?";
      }
      if (vname instanceof SimpleVname) {
        return safeIdentifier(((SimpleVname) vname).I);
      }
      if (vname instanceof DotVname) {
        DotVname dot = (DotVname) vname;
        return describeVname(dot.V) + "." + safeIdentifier(dot.I);
      }
      if (vname instanceof SubscriptVname) {
        SubscriptVname subscript = (SubscriptVname) vname;
        return describeVname(subscript.V) + "[" + describeExpression(subscript.E) + "]";
      }
      return vname.getClass().getSimpleName();
    }

    private String describeDeclarationInline(Declaration declaration) {
      if (declaration == null) {
        return "?";
      }
      if (declaration instanceof SequentialDeclaration) {
        SequentialDeclaration sequential = (SequentialDeclaration) declaration;
        return describeDeclarationInline(sequential.D1) + "; "
            + describeDeclarationInline(sequential.D2);
      }
      if (declaration instanceof ConstDeclaration) {
        ConstDeclaration constDeclaration = (ConstDeclaration) declaration;
        return "const " + safeIdentifier(constDeclaration.I);
      }
      if (declaration instanceof VarDeclaration) {
        VarDeclaration varDeclaration = (VarDeclaration) declaration;
        return "var " + safeIdentifier(varDeclaration.I);
      }
      if (declaration instanceof TypeDeclaration) {
        TypeDeclaration typeDeclaration = (TypeDeclaration) declaration;
        return "type " + safeIdentifier(typeDeclaration.I);
      }
      if (declaration instanceof FuncDeclaration) {
        FuncDeclaration funcDeclaration = (FuncDeclaration) declaration;
        return "func " + safeIdentifier(funcDeclaration.I);
      }
      if (declaration instanceof ProcDeclaration) {
        ProcDeclaration procDeclaration = (ProcDeclaration) declaration;
        return "proc " + safeIdentifier(procDeclaration.I);
      }
      return declaration.getClass().getSimpleName();
    }

    private String describeArrayAggregate(ArrayAggregate aggregate) {
      List<String> elements = new ArrayList<String>();
      collectArrayElements(aggregate, elements);
      return "[" + joinCommaSeparated(elements) + "]";
    }

    private void collectArrayElements(ArrayAggregate aggregate, List<String> out) {
      if (aggregate == null) {
        return;
      }
      if (aggregate instanceof SingleArrayAggregate) {
        out.add(describeExpression(((SingleArrayAggregate) aggregate).E));
      } else if (aggregate instanceof MultipleArrayAggregate) {
        MultipleArrayAggregate multiple = (MultipleArrayAggregate) aggregate;
        out.add(describeExpression(multiple.E));
        collectArrayElements(multiple.AA, out);
      }
    }

    private String describeRecordAggregate(RecordAggregate aggregate) {
      List<String> fields = new ArrayList<String>();
      collectRecordFields(aggregate, fields);
      return "{" + joinCommaSeparated(fields) + "}";
    }

    private void collectRecordFields(RecordAggregate aggregate, List<String> out) {
      if (aggregate == null) {
        return;
      }
      if (aggregate instanceof SingleRecordAggregate) {
        SingleRecordAggregate single = (SingleRecordAggregate) aggregate;
        out.add(safeIdentifier(single.I) + " = " + describeExpression(single.E));
      } else if (aggregate instanceof MultipleRecordAggregate) {
        MultipleRecordAggregate multiple = (MultipleRecordAggregate) aggregate;
        out.add(safeIdentifier(multiple.I) + " = " + describeExpression(multiple.E));
        collectRecordFields(multiple.RA, out);
      }
    }

    private String describeType(TypeDenoter type) {
      if (type == null) {
        return "?";
      }
      if (type instanceof IntTypeDenoter) {
        return "Integer";
      }
      if (type instanceof BoolTypeDenoter) {
        return "Boolean";
      }
      if (type instanceof CharTypeDenoter) {
        return "Char";
      }
      if (type instanceof SimpleTypeDenoter) {
        return safeIdentifier(((SimpleTypeDenoter) type).I);
      }
      if (type instanceof ArrayTypeDenoter) {
        ArrayTypeDenoter arrayType = (ArrayTypeDenoter) type;
        String length = (arrayType.IL != null) ? arrayType.IL.spelling : "?";
        return "array[" + length + "] of " + describeType(arrayType.T);
      }
      if (type instanceof RecordTypeDenoter) {
        RecordTypeDenoter recordType = (RecordTypeDenoter) type;
        return "{" + describeFieldType(recordType.FT) + "}";
      }
      if (type instanceof AnyTypeDenoter) {
        return "Any";
      }
      if (type instanceof ErrorTypeDenoter) {
        return "Error";
      }
      return type.getClass().getSimpleName();
    }

    private String describeFieldType(FieldTypeDenoter field) {
      if (field == null) {
        return "?";
      }
      if (field instanceof SingleFieldTypeDenoter) {
        SingleFieldTypeDenoter single = (SingleFieldTypeDenoter) field;
        return safeIdentifier(single.I) + ": " + describeType(single.T);
      }
      if (field instanceof MultipleFieldTypeDenoter) {
        MultipleFieldTypeDenoter multiple = (MultipleFieldTypeDenoter) field;
        return safeIdentifier(multiple.I) + ": " + describeType(multiple.T) + "; "
            + describeFieldType(multiple.FT);
      }
      return field.getClass().getSimpleName();
    }

    private void appendLine(String text) {
      StringBuilder line = new StringBuilder();
      for (int i = 0; i < indent; i++) {
        line.append("  ");
      }
      line.append(text);
      lines.add(line.toString());
    }

    private Summary toSummary() {
      if (lines.isEmpty()) {
        lines.add("(programa vacío)");
      }
      List<String> copy = new ArrayList<String>(lines);
      StringBuilder banner = new StringBuilder();
      for (int i = 0; i < copy.size(); i++) {
        if (i > 0) {
          banner.append('\n');
        }
        banner.append(copy.get(i));
      }
      return new Summary(Collections.unmodifiableList(copy), banner.toString());
    }

    private String safeIdentifier(Identifier identifier) {
      return (identifier != null) ? identifier.spelling : "?";
    }

    private String safeOperator(Operator operator) {
      return (operator != null) ? operator.spelling : "?";
    }

    private String joinCommaSeparated(List<String> values) {
      if (values.isEmpty()) {
        return "";
      }
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < values.size(); i++) {
        if (i > 0) {
          builder.append(", ");
        }
        builder.append(values.get(i));
      }
      return builder.toString();
    }

    static final class Summary {

      private final List<String> commentLines;
      private final String banner;

      Summary(List<String> commentLines, String banner) {
        this.commentLines = commentLines;
        this.banner = banner;
      }

      List<String> commentLines() {
        return commentLines;
      }

      String banner() {
        return banner;
      }
    }
  }
}
