package com.chrisallenclark.keepr.core.model

/**
 * An in-memory object graph with SwiftData's semantics: insert, fetch,
 * delete with each relationship's delete rule, and save.
 *
 * Saving hands the graph to whatever persistence is attached — Room on
 * Android, nothing in tests — through [onSave].
 */
class ModelContext {
    private val registry = LinkedHashSet<Model>()

    /** Called by [save]; the Android app attaches its Room writer here. */
    var onSave: ((ModelContext) -> Unit)? = null

    /** Bumped on every change, so observers know to recompute. */
    var changeCount: Long = 0
        private set

    fun insert(model: Model) {
        if (model.isDeleted) return
        if (model.modelContext === this && model in registry) return
        model.modelContext = this
        registry.add(model)
        changeCount++
        model.related().forEach { insert(it) }
    }

    fun delete(model: Model) {
        if (model.isDeleted || model !in registry) return
        model.isDeleted = true
        registry.remove(model)
        model.applyDeleteRules(this)
        model.modelContext = null
        changeCount++
    }

    /** `context.delete(model: T.self)` — every record of one type. */
    inline fun <reified T : Model> deleteAll() {
        fetch<T>().forEach { delete(it) }
    }

    inline fun <reified T : Model> fetch(): List<T> = all().filterIsInstance<T>()

    fun all(): List<Model> = registry.toList()

    fun contains(model: Model): Boolean = model in registry

    /** Marks a change made by mutating a record's properties. */
    fun touch() {
        changeCount++
    }

    fun save() {
        changeCount++
        onSave?.invoke(this)
    }
}
