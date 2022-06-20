package com.didiglobal.booster.cha.asm

import com.didiglobal.booster.graph.Graph
import org.objectweb.asm.Opcodes

class HierarchyAnalyser(private val asm: Int = Opcodes.ASM7) {

    /**
     * Build a hierarchy graph which each edge is from child class to parent class or interface
     */
    fun analyse(classSet: AsmClassSet): Graph<ClassNode> {
        return classSet.fold(Graph.Builder<ClassNode>()) { builder, clazz ->
            clazz.superName?.let { superName ->
                builder.addEdge(ClassNode(clazz.name), ClassNode(superName))
            }
            clazz.interfaces.forEach { interfaceName ->
                builder.addEdge(ClassNode(clazz.name), ClassNode(interfaceName))
            }
            builder
        }.build()
    }

}