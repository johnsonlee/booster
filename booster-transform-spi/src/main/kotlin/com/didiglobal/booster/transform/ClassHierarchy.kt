package com.didiglobal.booster.transform

/**
 * Represents the class hierarchy
 *
 * @author johnsonlee
 */
interface ClassHierarchy : Iterable<String> {

    /**
     * Check if this hierarchy a class with the specified name
     */
    operator fun contains(name: String): Boolean

    /**
     * Check if class [child] is inherit from class/interface [parent]
     */
    fun isInheritFrom(child: String, parent: String): Boolean

    /**
     * Check if type [child] is inherit from interface [parent]
     */
    fun isInheritFromInterface(child: String, parent: String): Boolean

    /**
     * Check if type [child] is inherit from class [parent]
     */
    fun isInheritFromClass(child: String, parent: String): Boolean

    /**
     * Returns the parent class and ancestor classes of class [clazz]
     */
    fun getSuperClasses(clazz: String): Set<String>

}