package Triangle.CodeGenerator.LLVM;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal utility for assembling LLVM IR modules. The builder keeps track of textual sections and
 * takes care of deduplicating string literals, so the forthcoming visitor can focus on traversing
 * the AST instead of manual string management.
 */
final class ModuleBuilder {

  private final String moduleName;
  private final String targetTriple;
  private final String dataLayout;
  private final List<String> prologue = new ArrayList<>();
  private final List<String> globals = new ArrayList<>();
  private final List<String> declarations = new ArrayList<>();
  private final List<String> functions = new ArrayList<>();
  private final List<String> metadata = new ArrayList<>();
  private final Map<String, InternedString> stringPool = new LinkedHashMap<>();
  private int stringId = 0;

  private ModuleBuilder(String moduleName, String targetTriple, String dataLayout) {
    this.moduleName = (moduleName != null) ? moduleName : "triangle-module";
    this.targetTriple = (targetTriple != null) ? targetTriple : "";
    this.dataLayout = (dataLayout != null) ? dataLayout : "";
  }

  static ModuleBuilder hostDefaults(String moduleName) {
    return new ModuleBuilder(moduleName, /*targetTriple=*/"", /*dataLayout=*/"");
  }

  ModuleBuilder addPrologue(String line) {
    prologue.add(line);
    return this;
  }

  @SuppressWarnings("unused")
  ModuleBuilder addGlobal(String definition) {
    globals.add(definition);
    return this;
  }

  @SuppressWarnings("unused")
  ModuleBuilder addMetadata(String entry) {
    metadata.add(entry);
    return this;
  }

  ModuleBuilder addDeclaration(String declaration) {
    declarations.add(declaration);
    return this;
  }

  FunctionBuilder newFunction(String signature) {
    return new FunctionBuilder(signature);
  }

  InternedString internStringLiteral(String literal) {
    InternedString existing = stringPool.get(literal);
    if (existing != null) {
      return existing;
    }
    String symbol = "@.str." + stringId++;
    String escaped = escapeForIr(literal);
    int elementCount = literal.length() + 1;
    String definition = symbol + " = private unnamed_addr constant [" + elementCount
        + " x i8] c\"" + escaped + "\\00\"";
    globals.add(definition);
    InternedString interned = new InternedString(symbol, elementCount);
    stringPool.put(literal, interned);
    return interned;
  }

  String buildModule() {
    StringBuilder out = new StringBuilder();
    out.append("; ModuleID = '").append(moduleName).append("'\n");
    if (!targetTriple.isEmpty()) {
      out.append("target triple = \"").append(targetTriple).append("\"\n");
    }
    if (!dataLayout.isEmpty()) {
      out.append("target datalayout = \"").append(dataLayout).append("\"\n");
    }
    for (String line : prologue) {
      out.append(line).append('\n');
    }
    if (!globals.isEmpty()) {
      out.append('\n');
      for (String global : globals) {
        out.append(global).append('\n');
      }
    }
    if (!declarations.isEmpty()) {
      out.append('\n');
      for (String declaration : declarations) {
        out.append(declaration).append('\n');
      }
    }
    if (!functions.isEmpty()) {
      out.append('\n');
      for (String function : functions) {
        out.append(function).append('\n');
      }
    }
    if (!metadata.isEmpty()) {
      out.append('\n');
      for (String entry : metadata) {
        out.append(entry).append('\n');
      }
    }
    return out.toString();
  }

  private static String escapeForIr(String literal) {
    StringBuilder builder = new StringBuilder(literal.length());
    for (int i = 0; i < literal.length(); i++) {
      char c = literal.charAt(i);
      if (c == '\\' || c == '\"') {
        builder.append('\\').append(c);
      } else if (c >= 32 && c < 127) {
        builder.append(c);
      } else {
        builder.append(String.format("\\%02X", (int) c));
      }
    }
    return builder.toString();
  }

  final class FunctionBuilder {

    private final String signature;
    private final List<String> body = new ArrayList<>();
    private boolean sealed = false;

    private FunctionBuilder(String signature) {
      if (signature == null) {
        throw new IllegalArgumentException("signature");
      }
      this.signature = signature;
    }

    public FunctionBuilder emit(String line) {
      if (sealed) {
        throw new IllegalStateException("Function already sealed");
      }
      body.add("  " + line);
      return this;
    }

    public FunctionBuilder emitLabel(String label) {
      if (sealed) {
        throw new IllegalStateException("Function already sealed");
      }
      body.add(label + ":");
      return this;
    }

    public void seal() {
      if (sealed) {
        return;
      }
      StringBuilder fn = new StringBuilder();
      fn.append(signature).append(" {\n");
      for (String line : body) {
        fn.append(line).append('\n');
      }
      fn.append('}');
      functions.add(fn.toString());
      sealed = true;
    }
  }

  static final class InternedString {

    private final String symbol;
    private final int elementCount;

    private InternedString(String symbol, int elementCount) {
      this.symbol = symbol;
      this.elementCount = elementCount;
    }

    public String symbol() {
      return symbol;
    }

    public int elementCount() {
      return elementCount;
    }

    public String arrayType() {
      return "[" + elementCount + " x i8]";
    }
  }
}
