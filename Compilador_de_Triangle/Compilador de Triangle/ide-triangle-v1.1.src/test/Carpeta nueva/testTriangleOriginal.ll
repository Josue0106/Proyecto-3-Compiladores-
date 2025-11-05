; ModuleID = 'triangle'
source_filename = "triangle"
; LLVM backend WIP: no executable semantics yet
; optimization level: NONE
; resumen AST
; AST Triangle:
;   comandos:
;     let
;       func aplicar(func f(const x : Integer) : Integer, const x : Integer) : Integer
;         = f(x)
;       fin func
;       func doble(const x : Integer) : Integer
;         = x * 2
;       fin func
;       func cuadrado(const x : Integer) : Integer
;         = x * x
;       fin func
;       const resultado1 = aplicar(func doble, 5)
;       const resultado2 = aplicar(func cuadrado, 5)
;     in
;       llamar putint(resultado1)
;       llamar putint(resultado2)
;       skip
;     fin let

@.str.0 = private unnamed_addr constant [526 x i8] c"LLVM backend en desarrollo\0AAST Triangle:\0A  comandos:\0A    let\0A      func aplicar(func f(const x : Integer) : Integer, const x : Integer) : Integer\0A        = f(x)\0A      fin func\0A      func doble(const x : Integer) : Integer\0A        = x * 2\0A      fin func\0A      func cuadrado(const x : Integer) : Integer\0A        = x * x\0A      fin func\0A      const resultado1 = aplicar(func doble, 5)\0A      const resultado2 = aplicar(func cuadrado, 5)\0A    in\0A      llamar putint(resultado1)\0A      llamar putint(resultado2)\0A      skip\0A    fin let\00"

declare i32 @puts(i8*)

define i32 @main() {
entry:
  %msg_ptr = getelementptr inbounds [526 x i8], [526 x i8]* @.str.0, i32 0, i32 0
  %call = call i32 @puts(i8* %msg_ptr)
  ret i32 0
}
