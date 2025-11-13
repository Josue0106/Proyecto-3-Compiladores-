	.def	@feat.00;
	.scl	3;
	.type	0;
	.endef
	.globl	@feat.00
@feat.00 = 0
	.file	"triangle"
	.def	triangle$aplicar;
	.scl	2;
	.type	32;
	.endef
	.text
	.globl	triangle$aplicar                # -- Begin function triangle$aplicar
	.p2align	4
triangle$aplicar:                       # @"triangle$aplicar"
.seh_proc triangle$aplicar
# %bb.0:                                # %entry
	subq	$56, %rsp
	.seh_stackalloc 56
	.seh_endprologue
	movq	%rcx, 48(%rsp)
	movl	%edx, 44(%rsp)
	movq	48(%rsp), %rax
	movl	44(%rsp), %ecx
	callq	*%rax
	nop
	.seh_startepilogue
	addq	$56, %rsp
	.seh_endepilogue
	retq
	.seh_endproc
                                        # -- End function
	.def	triangle$doble;
	.scl	2;
	.type	32;
	.endef
	.globl	triangle$doble                  # -- Begin function triangle$doble
	.p2align	4
triangle$doble:                         # @"triangle$doble"
.seh_proc triangle$doble
# %bb.0:                                # %entry
	pushq	%rax
	.seh_stackalloc 8
	.seh_endprologue
	movl	%ecx, 4(%rsp)
	movl	4(%rsp), %eax
	shll	%eax
	.seh_startepilogue
	popq	%rcx
	.seh_endepilogue
	retq
	.seh_endproc
                                        # -- End function
	.def	triangle$cuadrado;
	.scl	2;
	.type	32;
	.endef
	.globl	triangle$cuadrado               # -- Begin function triangle$cuadrado
	.p2align	4
triangle$cuadrado:                      # @"triangle$cuadrado"
.seh_proc triangle$cuadrado
# %bb.0:                                # %entry
	pushq	%rax
	.seh_stackalloc 8
	.seh_endprologue
	movl	%ecx, 4(%rsp)
	movl	4(%rsp), %eax
	imull	4(%rsp), %eax
	.seh_startepilogue
	popq	%rcx
	.seh_endepilogue
	retq
	.seh_endproc
                                        # -- End function
	.def	main;
	.scl	2;
	.type	32;
	.endef
	.globl	main                            # -- Begin function main
	.p2align	4
main:                                   # @main
.seh_proc main
# %bb.0:                                # %entry
	subq	$40, %rsp
	.seh_stackalloc 40
	.seh_endprologue
	leaq	triangle$doble(%rip), %rcx
	movl	$5, %edx
	callq	triangle$aplicar
	movl	%eax, 36(%rsp)
	leaq	triangle$cuadrado(%rip), %rcx
	movl	$5, %edx
	callq	triangle$aplicar
	movl	%eax, 32(%rsp)
	movl	36(%rsp), %edx
	leaq	.L.str.0(%rip), %rcx
	callq	printf
	movl	32(%rsp), %edx
	leaq	.L.str.0(%rip), %rcx
	callq	printf
	xorl	%eax, %eax
	.seh_startepilogue
	addq	$40, %rsp
	.seh_endepilogue
	retq
	.seh_endproc
                                        # -- End function
	.section	.rdata,"dr"
.L.str.0:                               # @.str.0
	.asciz	"%d\n"

.L.str.1:                               # @.str.1
	.asciz	"%d"

	.addrsig
	.addrsig_sym printf
	.addrsig_sym triangle$aplicar
	.addrsig_sym triangle$doble
	.addrsig_sym triangle$cuadrado
