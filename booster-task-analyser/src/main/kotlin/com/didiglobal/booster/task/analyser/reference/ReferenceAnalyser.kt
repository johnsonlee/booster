package com.didiglobal.booster.task.analyser.reference

import com.android.build.gradle.api.BaseVariant
import com.didiglobal.booster.cha.ClassSet
import com.didiglobal.booster.cha.asm.AsmClassFileParser
import com.didiglobal.booster.cha.asm.ClassReferenceAnalyser
import com.didiglobal.booster.gradle.getJars
import com.didiglobal.booster.gradle.getResolvedArtifactResults
import com.didiglobal.booster.graph.Graph
import com.didiglobal.booster.kotlinx.NCPU
import com.didiglobal.booster.kotlinx.green
import com.didiglobal.booster.kotlinx.yellow
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ReferenceAnalyser(
        private val project: Project,
        private val variant: BaseVariant?
) {

    private val upstreamClassSets = project.getResolvedArtifactResults(true, variant).associateWith {
        when (val id = it.id.componentIdentifier) {
            is ProjectComponentIdentifier -> project.rootProject.project(id.projectPath).classSets
            else -> ClassSet.from(it.file, AsmClassFileParser)
        }
    }

    /**
     * Returns the [ClassSet] of all variants, the key is the variant name
     */
    private val Project.classSets: ClassSet<ClassNode, AsmClassFileParser>
        get() = getJars(variant).map {
            ClassSet.from(it, AsmClassFileParser)
        }.let {
            ClassSet.of(it)
        }

    fun analyse(): Graph<ReferenceNode> {
        val executor = Executors.newFixedThreadPool(NCPU)
        val graphs = ConcurrentHashMap<String, Graph.Builder<ReferenceNode>>()

        try {
            val classes = project.classSets
            val index = AtomicInteger(0)
            val count = classes.size
            val analyser = ClassReferenceAnalyser()

            classes.map { klass ->
                val edge = { to: ReferenceNode ->
                    graphs.getOrPut(klass.name) {
                        Graph.Builder()
                    }.addEdge(ReferenceNode(this.project.name, klass.name), to)
                }
                executor.submit<Pair<ClassNode, Long>> {
                    val t0 = System.currentTimeMillis()
                    analyse(analyser.analyse(klass), edge)
                    klass to (System.currentTimeMillis() - t0)
                }
            }.forEach {
                val (klass, duration) = it.get()
                println("${green(String.format("%3d%%", index.incrementAndGet() * 100 / count))} Analyse class ${klass.name} in ${yellow(duration)} ms")
            }
        } finally {
            executor.shutdown()
            executor.awaitTermination(1, TimeUnit.MINUTES)
        }

        return graphs.entries.fold(Graph.Builder<ReferenceNode>()) { acc, (_, builder) ->
            builder.build().forEach { edge ->
                acc.addEdge(edge)
            }
            acc
        }.build()
    }

    private fun analyse(types: Iterable<Type>, edge: (ReferenceNode) -> Graph.Builder<ReferenceNode>) {
        types.forEach {
            findReference(it.internalName)?.let { (artifact, _) ->
                edge(ReferenceNode(artifact.id.componentIdentifier.displayName, it.internalName))
            }
        }
    }

    private fun findReference(owner: String) = upstreamClassSets.entries.find { (_, classSets) ->
        classSets.contains(owner)
    }

}
