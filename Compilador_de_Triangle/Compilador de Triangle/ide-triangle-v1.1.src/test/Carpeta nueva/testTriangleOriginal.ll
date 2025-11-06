; ModuleID = 'triangle'
source_filename = "triangle"
; Triangle LLVM backend
; optimization level: NONE

@.str.0 = private unnamed_addr constant [4 x i8] c"%d\0A\00"
@.str.1 = private unnamed_addr constant [3 x i8] c"%d\00"

declare i32 @printf(i8*, ...)
declare i32 @scanf(i8*, ...)
declare i32 @getchar()
declare i32 @putchar(i32)

define i32 @triangle$aplicar(i32 (i32)* %arg0, i32 %arg1) {
entry:
  %t0 = alloca i32 (i32)*
  store i32 (i32)* %arg0, i32 (i32)** %t0
  %t1 = alloca i32
  store i32 %arg1, i32* %t1
  %t2 = load i32 (i32)*, i32 (i32)** %t0
  %t3 = load i32, i32* %t1
  %t4 = call i32 %t2(i32 %t3)
  ret i32 %t4
}
define i32 @triangle$doble(i32 %arg0) {
entry:
  %t0 = alloca i32
  store i32 %arg0, i32* %t0
  %t1 = load i32, i32* %t0
  %t2 = mul i32 %t1, 2
  ret i32 %t2
}
define i32 @triangle$cuadrado(i32 %arg0) {
entry:
  %t0 = alloca i32
  store i32 %arg0, i32* %t0
  %t1 = load i32, i32* %t0
  %t2 = load i32, i32* %t0
  %t3 = mul i32 %t1, %t2
  ret i32 %t3
}
define i32 @main() {
entry:
  %t0 = call i32 @triangle$aplicar(i32 (i32)* @triangle$doble, i32 5)
  %t1 = alloca i32
  store i32 %t0, i32* %t1
  %t2 = call i32 @triangle$aplicar(i32 (i32)* @triangle$cuadrado, i32 5)
  %t3 = alloca i32
  store i32 %t2, i32* %t3
  %t4 = load i32, i32* %t1
  %t5 = getelementptr inbounds [4 x i8], [4 x i8]* @.str.0, i32 0, i32 0
  call i32 @printf(i8* %t5, i32 %t4)
  %t6 = load i32, i32* %t3
  %t7 = getelementptr inbounds [4 x i8], [4 x i8]* @.str.0, i32 0, i32 0
  call i32 @printf(i8* %t7, i32 %t6)
  ret i32 0
}
