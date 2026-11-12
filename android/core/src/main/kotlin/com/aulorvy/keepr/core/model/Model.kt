package com.aulorvy.keepr.core.model

import java.util.UUID

/**
 * Base of the nine records. Identity is by reference, as with SwiftData
 * models; [id] is the stable UUID that's stored and, later, synced.
 */
abstract class Model {
    abstract val id: UUID

    /** The context this record lives in, if it has been inserted. */
    var modelContext: ModelContext? = null
        internal set

    /** True once deleted from its context. */
    var isDeleted: Boolean = false
        internal set

    /** Every record this one points at, so inserting one inserts its graph. */
    internal abstract fun related(): List<Model>

    /** Cuts this record's relationships, applying the delete rules. */
    internal abstract fun applyDeleteRules(context: ModelContext)
}

/**
 * Keeps both ends of a to-one / to-many relationship in step, as SwiftData
 * does for `@Relationship(inverse:)`. Moving a child between parents removes
 * it from the old parent's list, and a record joined to one that lives in a
 * context is inserted into that context too.
 */
internal fun <C : Model, P : Model> C.relink(old: P?, new: P?, children: (P) -> MutableList<C>) {
    if (old === new) return
    old?.let { parent -> children(parent).removeAll { it === this } }
    new?.let { parent ->
        val list = children(parent)
        if (list.none { it === this }) list.add(this)
        adopt(this, parent)
    }
}

/** Replaces a to-many list from the parent's side, via each child's setter. */
internal fun <C : Model> replaceChildren(current: List<C>, next: List<C>, detach: (C) -> Unit, attach: (C) -> Unit) {
    current.filter { old -> next.none { it === old } }.forEach(detach)
    next.filter { new -> current.none { it === new } }.forEach(attach)
}

/** Many-to-many: replaces one side's list and mirrors it on the other. */
internal fun <A : Model, B : Model> A.replaceMany(
    mine: MutableList<B>,
    next: List<B>,
    theirs: (B) -> MutableList<A>,
) {
    val unique = next.fold(mutableListOf<B>()) { acc, item -> if (acc.none { it === item }) acc.add(item); acc }
    mine.filter { old -> unique.none { it === old } }.forEach { removed -> theirs(removed).removeAll { it === this } }
    unique.filter { new -> mine.none { it === new } }.forEach { added ->
        val list = theirs(added)
        if (list.none { it === this }) list.add(this)
        adopt(this, added)
    }
    mine.clear()
    mine.addAll(unique)
}

internal fun adopt(a: Model, b: Model) {
    val context = a.modelContext ?: b.modelContext ?: return
    if (a.modelContext == null && !a.isDeleted) context.insert(a)
    if (b.modelContext == null && !b.isDeleted) context.insert(b)
}

internal fun <T> MutableList<T>.removeIdentity(item: T) {
    removeAll { it === item }
}
