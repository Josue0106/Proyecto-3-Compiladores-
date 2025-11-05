package Triangle.CodeGenerator.LLVM;

import Triangle.AbstractSyntaxTrees.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    Request effectiveRequest = request != null ? request : Request.defaults();

    ModuleBuilder module = ModuleBuilder.hostDefaults("triangle");
    module.addPrologue("source_filename = \"triangle\"");
    module.addPrologue("; LLVM backend WIP: no executable semantics yet");
    module.addPrologue("; optimization level: " + effectiveRequest.optimizationLevel());
    if (effectiveRequest.emitObjectFile() || effectiveRequest.emitAssembly()) {
      module.addPrologue("; TODO: object/assembly emission pending");
    }

    AstSummary.Summary summary = AstSummary.summarize(program);
    module.addPrologue("; resumen AST");
    for (String line : summary.commentLines()) {
      module.addPrologue("; " + line);
    }

    ModuleBuilder.InternedString banner = module.internStringLiteral(
        "LLVM backend en desarrollo\n" + summary.banner());
    module.addDeclaration("declare i32 @puts(i8*)");

    ModuleBuilder.FunctionBuilder main = module.newFunction("define i32 @main()");
    main.emitLabel("entry");
    String bannerArrayType = banner.arrayType();
    main.emit("%msg_ptr = getelementptr inbounds " + bannerArrayType + ", " + bannerArrayType
        + "* " + banner.symbol() + ", i32 0, i32 0");
    main.emit("%call = call i32 @puts(i8* %msg_ptr)");
    main.emit("ret i32 0");
    main.seal();

    return new Result(module.buildModule(), null, null);
  }

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
