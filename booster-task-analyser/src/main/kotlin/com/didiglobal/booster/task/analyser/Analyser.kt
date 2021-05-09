package com.didiglobal.booster.task.analyser

import com.didiglobal.booster.aapt2.metadata
import com.didiglobal.booster.cha.ClassHierarchy
import com.didiglobal.booster.cha.ClassSet
import com.didiglobal.booster.cha.JAVA_LANG_OBJECT
import com.didiglobal.booster.cha.flatten
import com.didiglobal.booster.cha.graph.CallGraph
import com.didiglobal.booster.cha.graph.dot.Digraph
import com.didiglobal.booster.kotlinx.NCPU
import com.didiglobal.booster.kotlinx.asIterable
import com.didiglobal.booster.kotlinx.blue
import com.didiglobal.booster.kotlinx.file
import com.didiglobal.booster.kotlinx.green
import com.didiglobal.booster.kotlinx.red
import com.didiglobal.booster.kotlinx.search
import com.didiglobal.booster.kotlinx.touch
import com.didiglobal.booster.kotlinx.yellow
import com.didiglobal.booster.transform.ArtifactManager
import com.didiglobal.booster.transform.asm.args
import com.didiglobal.booster.transform.asm.className
import com.didiglobal.booster.transform.asm.getValue
import com.didiglobal.booster.transform.asm.isAbstract
import com.didiglobal.booster.transform.asm.isAnnotation
import com.didiglobal.booster.transform.asm.isInterface
import com.didiglobal.booster.transform.asm.isInvisibleAnnotationPresent
import com.didiglobal.booster.transform.asm.isNative
import com.didiglobal.booster.transform.asm.isProtected
import com.didiglobal.booster.transform.asm.isPublic
import com.didiglobal.booster.transform.asm.isStatic
import com.didiglobal.booster.transform.util.ComponentHandler
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import java.io.File
import java.net.URL
import java.util.Stack
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.xml.parsers.SAXParserFactory
import kotlin.streams.toList

/**
 * @author johnsonlee
 */
class Analyser(
        private val subject: String,
        private val providedClasspath: Collection<File>,
        private val compileClasspath: Collection<File>,
        private val artifacts: ArtifactManager,
        private val properties: Map<String, *> = emptyMap<String, Any>()
) {

    private val providedClasses = providedClasspath.map(ClassSet.Companion::from).flatten()
    private val compileClasses = compileClasspath.map(ClassSet.Companion::from).flatten()
    private val classes = providedClasses + compileClasses

    private val hierarchy = ClassHierarchy(classes)

    private val classesRunOnUiThread = ConcurrentHashMap<String, ClassNode>()
    private val classesRunOnMainThread = ConcurrentHashMap<String, ClassNode>()

    private val nodesRunOnUiThread = CopyOnWriteArraySet<CallGraph.Node>()
    private val nodesRunOnMainThread = CopyOnWriteArraySet<CallGraph.Node>()

    private val blacklist = URL(properties[PROPERTY_BLACKLIST]?.toString() ?: VALUE_BLACKLIST).openStream().bufferedReader().use {
        it.readLines().filter(String::isNotBlank).map(CallGraph.Node.Companion::valueOf).toSet()
    }

    private val whitelist = URL(properties[PROPERTY_WHITELIST]?.toString() ?: VALUE_WHITELIST).openStream().bufferedReader().use {
        it.readLines().filter(String::isNotBlank).map(CallGraph.Node.Companion::valueOf).toSet()
    }

    private val mainThreadAnnotations: Set<String> by lazy {
        MAIN_THREAD_ANNOTATIONS.filter(classes::contains).map(::descriptor).toSet()
    }

    private val uiThreadAnnotations: Set<String> by lazy {
        UI_THREAD_ANNOTATIONS.filter(classes::contains).map(::descriptor).toSet()
    }

    constructor(
            subject: String,
            platform: File,
            compileClasspath: Collection<File>,
            artifacts: ArtifactManager,
            properties: Map<String, *> = emptyMap<String, Any>()
    ) : this(subject, platform.bootClasspath, compileClasspath, artifacts, properties)

    fun analyse(output: File) {
        this.classes.load().use {
            val global = buildGlobalCallGraph()
            this.renderCallGraph(global, output)
            val stripped = global.strip(blacklist)
            this.renderCallGraph(stripped, output)
            val inferred = global.infer(blacklist)
            this.renderCallGraph(inferred, output)
            this.hierarchy.unresolvedClasses.forEach {
                println("Unresolved class ${red(it.replace('/', '.'))}")
            }
        }
    }

    private fun buildGlobalCallGraph(): CallGraph {
        val globalBuilder = CallGraph.Builder().setTitle(subject).apply {
            loadEntryPoints()
        }
        val provideClasses = this.providedClasses.toList()
        val compileClasses = this.compileClasses.parallelStream().filter(ClassNode::isInclude).toList()
        val index = AtomicInteger(0)
        val count = provideClasses.size + compileClasses.size
        val executor = Executors.newFixedThreadPool(NCPU)

        try {
            mapOf(
                    provideClasses to true,
                    compileClasses to false
            ).map { (classes, provided) ->
                classes.map {
                    executor.submit {
                        val t0 = System.currentTimeMillis()
                        it.analyse(globalBuilder, provided)
                        println("${green(String.format("%3d%%", index.incrementAndGet() * 100 / count))} Analyse class ${it.className} in ${yellow(System.currentTimeMillis() - t0)} ms")
                    }
                }
            }.flatten().forEach {
                it.get()
            }
        } finally {
            executor.shutdown()
            executor.awaitTermination(1L, TimeUnit.HOURS)
        }

        return globalBuilder.build()
    }

    private fun CallGraph.Builder.loadEntryPoints() {
        val executor = Executors.newFixedThreadPool(NCPU)

        try {
            (loadMainThreadEntryPoints(executor) + loadUiThreadEntryPoints(executor)).forEach {
                it.get()
            }
        } finally {
            executor.shutdown()
            executor.awaitTermination(1L, TimeUnit.HOURS)
        }
    }

    /**
     * Find main thread entry point by parsing AndroidManifest.xml
     */
    private fun CallGraph.Builder.loadMainThreadEntryPoints(executor: ExecutorService): List<Future<*>> {
        val handler = artifacts.get(ArtifactManager.MERGED_MANIFESTS).map { manifest ->
            ComponentHandler().also { handler ->
                SAXParserFactory.newInstance().newSAXParser().parse(manifest, handler)
            }
        }.fold(ComponentHandler()) { acc, i ->
            acc.applications += i.applications
            acc.activities += i.activities
            acc.services += i.services
            acc.receivers += i.receivers
            acc.providers += i.providers
            acc
        }

        return arrayOf(
                "android/app/Application" to handler.applications,
                "android/app/Activity" to handler.activities,
                "android/app/Service" to handler.services,
                "android/content/BroadcastReceiver" to handler.receivers,
                "android/content/ContentProvider" to handler.providers
        ).map {
            executor.submit(Callable<Triple<ClassNode, Collection<String>, Collection<MethodNode>>> {
                val clazz = hierarchy[it.first] ?: throw ClassNotFoundException(it.first.replace('/', '.'))
                Triple(clazz, it.second.map { it.replace('.', '/') }, clazz.methods.filter(MethodNode::isEntryPoint))
            })
        }.map { future ->
            val (clazz, components, entryPoints) = future.get()

            classesRunOnMainThread[clazz.name] = clazz

            components.map { component ->
                executor.submit {
                    println("Loading main thread entry points from $component ...")

                    val nodes = entryPoints.map {
                        CallGraph.Node(clazz.name, it.name, it.desc)
                    }

                    hierarchy[component]?.run {
                        classesRunOnMainThread[component] = this
                    }
                    addEdges(CallGraph.ROOT, nodes)
                    nodesRunOnMainThread += nodes
                }
            }
        }.flatten()
    }

    /**
     * Find custom View as UI thread entry point by parsing layout xml
     */
    private fun CallGraph.Builder.loadUiThreadEntryPoints(executor: ExecutorService): List<Future<*>> {
        // Load platform widgets
        val widgets = providedClasspath.find {
            it.name == "android.jar"
        }?.parentFile?.file("data", "widgets.txt")?.readLines()?.filter {
            it.startsWith("W")
        }?.map {
            it.substring(1, it.indexOf(' '))
        }?.toSet() ?: emptySet()

        val visit: (File) -> Set<String> = { xml ->
            val handler = LayoutHandler()
            SAXParserFactory.newInstance().newSAXParser().parse(xml, handler)
            handler.views
        }

        return artifacts.get(ArtifactManager.MERGED_RES).search {
            it.name.startsWith("layout_") && it.name.endsWith(".xml.flat")
        }.map { flat ->
            executor.submit {
                val header = flat.metadata
                val xml = header.sourceFile

                println("Parsing ${header.resourcePath} ...")

                nodesRunOnUiThread += visit(xml).filter {
                    '.' in it || it in widgets // ignore system widgets
                }.map { tag ->
                    val desc = tag.replace('.', '/')
                    val clazz = hierarchy[desc]

                    if (null == clazz) {
                        println(red("Unresolved class ${tag}: ${header.resourceName} -> ${header.sourcePath}"))
                        emptyList()
                    } else {
                        classesRunOnUiThread[desc] = clazz

                        hierarchy.getSuperClasses(clazz).filter {
                            it.name != JAVA_LANG_OBJECT
                        }.forEach {
                            classesRunOnUiThread[it.name] = it
                        }

                        val nodes = clazz.methods.filter(MethodNode::isEntryPoint).map { m ->
                            CallGraph.Node(clazz.name, m.name, m.desc)
                        }

                        addEdges(CallGraph.ROOT, nodes)
                        nodes
                    }
                }.flatten()
            }
        }
    }

    private fun ClassNode.analyse(globalBuilder: CallGraph.Builder, provided: Boolean = false) {
        val isClassRunOnUiThread = isRunOnUiThread()
        val isClassRunOnMainThread = isRunOnMainThread()
        val isClassRunOnUiOrMainThread = isClassRunOnMainThread || isClassRunOnUiThread

        if (isClassRunOnUiOrMainThread) {
            innerClasses?.forEach { inner ->
                hierarchy[inner.name]?.let {
                    when {
                        isClassRunOnMainThread -> classesRunOnMainThread
                        else -> classesRunOnUiThread
                    }[inner.name] = it
                }
            }
        }

        methods.forEach { method ->
            val isMethodRunUiOrMainThread = isClassRunOnUiOrMainThread
                    || method.isRunOnUiThread(this)
                    || method.isRunOnMainThread(this)
                    || method.isSubscribeOnMainThread()

            if (isMethodRunUiOrMainThread) {
                val node = CallGraph.Node(name, method.name, method.desc)

                if (isClassRunOnMainThread) {
                    nodesRunOnMainThread += node
                }

                if (isClassRunOnUiThread) {
                    nodesRunOnUiThread += node
                }

                globalBuilder.addEdge(CallGraph.ROOT, node)
            }

            // construct call graph by scanning INVOKE* instructions
            method.takeIf { !provided }
                    ?.instructions
                    ?.iterator()
                    ?.asIterable()
                    ?.filterIsInstance(MethodInsnNode::class.java)
                    ?.forEach { invoke ->
                        val to = CallGraph.Node(invoke.owner, invoke.name, invoke.desc)
                        val from = CallGraph.Node(name, method.name, method.desc)

                        // break circular invocation
                        if (!globalBuilder.hasEdge(to, from)) {
                            globalBuilder.addEdge(from, to)
                        }
                    }
        }
    }

    private fun CallGraph.strip(terminators: Collection<CallGraph.Node>): CallGraph {
        val stripped = CallGraph.Builder().setTitle("${title}-stripped")
        val stack = Stack<CallGraph.Node>().apply {
            this.addAll(terminators)
        }

        while (stack.isNotEmpty()) {
            val to = stack.pop()
            this[null, to].forEach { from ->
                stripped.addEdge(from, to)
                if (!stripped.hasEdge(to, from)) {
                    stack.add(from)
                }
            }
        }

        return stripped.build()
    }

    private fun CallGraph.infer(terminators: Collection<CallGraph.Node>): CallGraph {
        val inferred = CallGraph.Builder().setTitle("${title}-inferred")
        val stack = Stack<CallGraph.Node>().apply {
            this.addAll(terminators)
        }
        // infer the caller of the node by using the super class or interface for reachability checking
        val inferFrom: (CallGraph.Node, String) -> Collection<CallGraph.Node> = { to, type ->
            val node = CallGraph.Node(type, to.name, to.desc)
            val isRunOnMainOrUiThread = hierarchy[type]?.let { klass ->
                klass.isRunOnMainThread() || klass.isRunOnUiThread() || klass.methods?.find {
                    it.name == to.name && it.desc == to.desc
                }?.let { method ->
                    method.isRunOnMainThread(klass) || method.isRunOnUiThread(klass) || method.isSubscribeOnMainThread()
                } == true
            }
            if (isRunOnMainOrUiThread == true) {
                inferred.addEdge(CallGraph.ROOT, node)
            }
            this[null, node]
        }

        while (stack.isNotEmpty()) {
            val to = stack.pop()
            val from = this[null, to].takeIf {
                it.isNotEmpty()
            } ?: hierarchy[to.type]?.superName?.takeIf { it != JAVA_LANG_OBJECT }?.let {
                inferFrom(to, it)
            } ?: hierarchy[to.type]?.interfaces?.map {
                inferFrom(to, it)
            }?.flatten()

            from?.forEach {
                inferred.addEdge(it, to)
                if (!inferred.hasEdge(to, it)) {
                    stack.add(it)
                }
            }
        }

        return inferred.build()
    }

    /**
     * Rendering call graph as individual dot format
     */
    private fun renderCallGraph(graph: CallGraph, output: File) {
        File(output, "${graph.title}.dot").also {
            println(it)
        }.touch().printWriter().use { printer ->
            graph.print(printer, Digraph.LR::render)
        }
    }

    /**
     * Check if this class is run on main thread
     */
    private fun ClassNode.isRunOnMainThread() = isRunOnThread(mainThreadAnnotations, classesRunOnMainThread)

    /**
     * Check if this class is run on UI thread
     */
    private fun ClassNode.isRunOnUiThread() = isRunOnThread(uiThreadAnnotations, classesRunOnUiThread)

    private fun ClassNode.isRunOnThread(annotations: Set<String>, classesRunOnThread: MutableMap<String, ClassNode>): Boolean {
        return isInvisibleAnnotationPresent(annotations)
                || classesRunOnThread.containsKey(name)
                || superName?.takeIf { it != JAVA_LANG_OBJECT }?.let { hierarchy[it] }?.isRunOnThread(annotations, classesRunOnThread) == true
                || interfaces?.any { hierarchy[it]?.isRunOnThread(annotations, classesRunOnThread) == true } == true
    }

    /**
     * Check if this method is run on main thread
     */
    private fun MethodNode.isRunOnMainThread(clazz: ClassNode) = isRunOnThread(clazz, mainThreadAnnotations, nodesRunOnMainThread)

    /**
     * Check if this method is run on UI thread
     */
    private fun MethodNode.isRunOnUiThread(clazz: ClassNode) = isRunOnThread(clazz, uiThreadAnnotations, nodesRunOnUiThread)

    private fun MethodNode.isRunOnThread(clazz: ClassNode, annotations: Set<String>, nodesRunOnThread: Set<CallGraph.Node>): Boolean {
        if (this.isInvisibleAnnotationPresent(annotations)) {
            return true
        }

        return nodesRunOnThread.any {
            it.name == this.name && it.args == this.args && hierarchy.isInheritFrom(clazz, it.type)
        }
    }

    private infix fun CallGraph.Node.matches(apis: Collection<CallGraph.Node>) = apis.contains(this) || apis.any {
        // only match type, name and args because of covariant return type is partially allowed since JDK 1.5
        // (overridden method can have different return type in sub-type)
        this.name == it.name && this.args == it.args && hierarchy.isInheritFrom(this.type, it.type)
    }
}

private val File.bootClasspath: Collection<File>
    get() = listOf(resolve("android.jar"), resolve("optional").resolve("org.apache.http.legacy.jar"))

internal fun MethodNode.isSubscribeOnMainThread(): Boolean {
    return visibleAnnotations
            ?.find { it.desc == EVENTBUS_SUBSCRIBE }
            ?.getValue<Array<String>>("threadMode")
            ?.contentEquals(arrayOf(EVENTBUS_THREAD_MODE, "MAIN")) ?: false
}

/**
 * Excludes classes with conditions:
 *
 * - class in the ignore list
 * - annotation classes
 * - class has no methods containing any *invoke* instruction
 */
private val ClassNode.isInclude: Boolean
    get() = !(EXCLUDES matches name || isAnnotation || ((isInterface || isAbstract) && methods.none { !it.isAbstract }))

private val MethodNode.isEntryPoint: Boolean
    get() = (isPublic || isProtected) && !isNative && !isStatic

private fun descriptor(name: String) = "L${name};"

private const val EVENTBUS_SUBSCRIBE = "Lorg/greenrobot/eventbus/Subscribe;"
private const val EVENTBUS_THREAD_MODE = "Lorg/greenrobot/eventbus/ThreadMode;"

private val PROPERTY_PREFIX = Build.ARTIFACT.replace('-', '.')

private val PROPERTY_BLACKLIST = "$PROPERTY_PREFIX.blacklist"

private val PROPERTY_WHITELIST = "$PROPERTY_PREFIX.whitelist"

internal val VALUE_BLACKLIST = Analyser::class.java.classLoader.getResource("blacklist.txt")!!.toString()

internal val VALUE_WHITELIST = Analyser::class.java.classLoader.getResource("whitelist.txt")!!.toString()

