# LLVM Backend Roadmap

This plan aligns the Triangle compiler with the Proyecto 3 specification for LLVM support. It captures objectives, workstreams, deliverables, and near-term actions so the codebase and documentation evolve together.

## Objectives

- Deliver an LLVM-based code generation path that produces runnable native binaries for Triangle programs.
- Integrate the LLVM toolchain within the existing IDE experience without regressing TAM functionality.
- Provide the scaffolding necessary for optimizations and runtime support to be added incrementally.

## Guiding Principles

- Keep the checker and existing AST shapes unchanged; layer LLVM metadata via auxiliary structures.
- Add new components in parallel to the TAM encoder so both backends remain selectable.
- Record every new executable or configuration requirement inside the documentation as soon as it becomes available.

## Workstreams

### 1. Toolchain Enablement
- [ ] Detect `clang`, `opt`, and `llc`; surface actionable diagnostics if binaries are missing.
- [ ] Model command-line templates for IR emission, optimization, assembly, and linking.
- [ ] Define environment variables and IDE preferences used to override tool locations.

### 2. IR Generation Core
- [ ] Extend the contextual environment with LLVM storage metadata (globals, locals, temporaries).
- [ ] Implement the `LLVMGenerator` visitor to translate the core Triangle constructs into SSA IR.
- [ ] Introduce a module builder that owns type caches, string literals, and global declarations.

### 3. Runtime and Linking
- [ ] Author C helpers for `readInt`, `writeInt`, `readChar`, `writeChar`, and structure the build artifacts.
- [ ] Automate linking by composing IR, helper bitcode, and optional user libraries.
- [ ] Document cross-platform nuances (Windows vs. Unix toolchains).

### 4. IDE Workflow
- [ ] Add a dedicated LLVM output console with streaming feedback.
- [ ] Provide menu actions for "Compile to LLVM", "Save LLVM IR", and "Run Native".
- [ ] Capture optimization flags in a dialog and feed them to the generator request object.

### 5. Quality and Regression Control
- [ ] Establish regression samples covering arithmetic, control flow, I/O, and strings.
- [ ] Set up automated smoke tests that invoke the LLVM pipeline via ant or gradle.
- [ ] Track open issues and risk mitigations directly from this roadmap.

## Milestones

| Milestone | Target Outcome | Exit Criteria |
| --- | --- | --- |
| M1 – Toolchain Discovery | Tool paths configurable and validated within IDE. | UI surface exists; missing tools trigger IDE error dialog; documentation updated with setup steps. |
| M2 – Minimal IR Emission | Triangle subset compiles to valid LLVM IR. | `LLVMGenerator` supports expressions, let/if/while, read/write; IR passes `llvm-as` validation. |
| M3 – Native Execution | Generated binaries run via the IDE. | Runtime helpers linked; IDE exposes run/save actions; sample programs execute correctly. |
| M4 – Optimization & Polish | Users can opt into optimization passes. | Optimization dialog wired; presets applied end-to-end; smoke tests updated. |

## Interfaces and Artifacts

- `Triangle.CodeGenerator.LLVM.LLVMGenerator` – backend entry point returning IR and file paths.
- `Triangle.CodeGenerator.LLVM.ModuleBuilder` (planned) – constructs modules, manages globals and string tables.
- `Triangle.docs.llvm-toolchain.md` (planned) – installation and configuration guide for LLVM 16+.
- Runtime helper sources under `Triangle/runtime/llvm/` (planned) – C implementations and build scripts.

## Immediate Next Actions

1. Finalize the generator request/result API (stub now in place) and wire it into the compiler driver behind a feature flag.
2. Draft the toolchain setup guide and reference it from IDE dialogs.
3. Define regression samples and expected outputs to accompany each milestone.

Use this roadmap as the single source of truth. Update progress indicators and references as milestones land.
