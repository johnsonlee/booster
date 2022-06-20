package com.didiglobal.booster.cha.asm

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.TypePath
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import org.objectweb.asm.tree.ClassNode

class ClassReferenceAnalyser(private val asm: Int = Opcodes.ASM7) {

    fun analyse(klass: ClassNode): Set<Type> {
        val references = sortedSetOf<Type>()
        val av = AnnotationAnalyser(references)
        val sv = SignatureAnalyser(references)
        val fv = FieldAnalyser(av, references)
        val mv = MethodAnalyser(av, sv, references)
        klass.accept(ClassAnalyser(klass, av, fv, mv, sv, references))
        return references
    }

    private inner class ClassAnalyser(
            cv: ClassVisitor,
            private val av: AnnotationVisitor,
            private val fv: FieldVisitor,
            private val mv: MethodVisitor,
            private val sv: SignatureVisitor,
            private val references: MutableSet<Type>
    ) : ClassVisitor(asm, cv) {

        override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String>?) {
            superName?.let {
                references += Type.getObjectType(it)
            }
            interfaces?.forEach {
                references += Type.getObjectType(it)
            }
            signature?.let(::SignatureReader)?.accept(sv)
            super.visit(version, access, name, signature, superName, interfaces)
        }

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
            references += Type.getType(descriptor)
            return av
        }

        override fun visitTypeAnnotation(typeRef: Int, typePath: TypePath, descriptor: String, visible: Boolean): AnnotationVisitor {
            references += Type.getType(descriptor)
            return av
        }

        override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor {
            references += Type.getType(descriptor)
            if (value is Type) {
                references += value
            }
            signature?.let(::SignatureReader)?.acceptType(sv)
            return fv
        }

        override fun visitMethod(access: Int, name: String?, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor {
            references += Type.getArgumentTypes(descriptor)
            references += Type.getReturnType(descriptor)
            signature?.let(::SignatureReader)?.accept(sv)
            exceptions?.forEach {
                references += Type.getObjectType(it)
            }
            return mv
        }

    }

    private inner class AnnotationAnalyser(
            private val references: MutableSet<Type>
    ) : AnnotationVisitor(asm) {

        override fun visit(name: String?, value: Any?) {
            if (value is Type) {
                references += value
            }
        }

        override fun visitEnum(name: String?, descriptor: String?, value: String?) {
            descriptor?.let {
                references += Type.getType(it)
            }
        }

        override fun visitAnnotation(name: String?, descriptor: String?): AnnotationVisitor {
            descriptor?.let {
                references += Type.getType(it)
            }
            return this
        }

        override fun visitArray(name: String?): AnnotationVisitor = this
    }

    private inner class FieldAnalyser(
            private val av: AnnotationVisitor,
            private val references: MutableSet<Type>
    ) : FieldVisitor(asm) {

        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor {
            descriptor?.let {
                references += Type.getType(it)
            }
            return av
        }

        override fun visitTypeAnnotation(typeRef: Int, typePath: TypePath?, descriptor: String?, visible: Boolean): AnnotationVisitor {
            descriptor?.let {
                references += Type.getType(it)
            }
            return av
        }

    }

    private inner class MethodAnalyser(
            private val av: AnnotationVisitor,
            private val sv: SignatureAnalyser,
            private val references: MutableSet<Type>
    ) : MethodVisitor(asm) {

        override fun visitAnnotationDefault(): AnnotationVisitor = av

        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor {
            descriptor?.let {
                references += Type.getType(it)
            }
            return av
        }

        override fun visitTypeAnnotation(typeRef: Int, typePath: TypePath?, descriptor: String?, visible: Boolean): AnnotationVisitor {
            descriptor?.let {
                references += Type.getType(it)
            }
            return av
        }

        override fun visitParameterAnnotation(parameter: Int, descriptor: String?, visible: Boolean): AnnotationVisitor {
            descriptor?.let {
                references += Type.getType(it)
            }
            return av
        }

        override fun visitTypeInsn(opcode: Int, type: String?) {
            type?.let {
                references += Type.getObjectType(it)
            }
        }

        override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) {
            owner?.let {
                references += Type.getObjectType(it)
            }
            descriptor?.let {
                references += Type.getType(it)
            }
        }

        override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, descriptor: String?, isInterface: Boolean) {
            owner?.let {
                references += Type.getObjectType(it)
            }
            descriptor?.let {
                references += Type.getReturnType(it)
                references += Type.getArgumentTypes(it)
            }
        }

        override fun visitInvokeDynamicInsn(name: String?, descriptor: String?, bootstrapMethodHandle: Handle?, vararg bootstrapMethodArguments: Any?) {
            descriptor?.let {
                references += Type.getReturnType(it)
                references += Type.getArgumentTypes(it)
            }
        }

        override fun visitLdcInsn(value: Any?) {
            if (value is Type) {
                references += value
            }
        }

        override fun visitMultiANewArrayInsn(descriptor: String?, numDimensions: Int) {
            descriptor?.let {
                references += Type.getType(it)
            }
        }

        override fun visitInsnAnnotation(typeRef: Int, typePath: TypePath?, descriptor: String?, visible: Boolean): AnnotationVisitor {
            descriptor?.let {
                references += Type.getType(it)
            }
            return av
        }

        override fun visitTryCatchBlock(start: Label?, end: Label?, handler: Label?, type: String?) {
            type?.let {
                references += Type.getObjectType(it)
            }
        }

        override fun visitTryCatchAnnotation(typeRef: Int, typePath: TypePath?, descriptor: String?, visible: Boolean): AnnotationVisitor {
            descriptor?.let {
                references += Type.getType(it)
            }
            return av
        }

        override fun visitLocalVariable(name: String?, descriptor: String?, signature: String?, start: Label?, end: Label?, index: Int) {
            descriptor?.let {
                references += Type.getType(it)
            }
            signature?.let(::SignatureReader)?.acceptType(sv)
        }

        override fun visitLocalVariableAnnotation(typeRef: Int, typePath: TypePath?, start: Array<out Label>?, end: Array<out Label>?, index: IntArray?, descriptor: String?, visible: Boolean): AnnotationVisitor {
            descriptor?.let {
                references += Type.getType(it)
            }
            return av
        }
    }

    private inner class SignatureAnalyser(
            private val references: MutableSet<Type>
    ) : SignatureVisitor(asm) {

        override fun visitClassType(name: String) {
            references += Type.getObjectType(name)
            super.visitClassType(name)
        }

    }

}