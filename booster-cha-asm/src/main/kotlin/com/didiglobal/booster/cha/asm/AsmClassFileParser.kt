package com.didiglobal.booster.cha.asm

import com.didiglobal.booster.cha.ClassFileParser
import com.didiglobal.booster.transform.asm.asClassNode
import org.objectweb.asm.tree.ClassNode
import java.io.InputStream

object AsmClassFileParser : ClassFileParser<ClassNode> {

    override fun parse(input: InputStream): ClassNode = input.asClassNode()

    override fun getAccessFlags(classNode: ClassNode): Int = classNode.access

    override fun getInterfaces(classNode: ClassNode): Array<String> = classNode.interfaces.toTypedArray()

    override fun getSuperName(classNode: ClassNode): String? = classNode.superName

    override fun getClassName(classNode: ClassNode): String = classNode.name

}