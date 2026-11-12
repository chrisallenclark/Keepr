package com.aulorvy.keepr.core.domain

// Translated from the catalogue in Keepr/DesignSystem/SymbolPicker.swift, so
// the Android symbol picker offers the same symbols, in the same order, found
// by the same words. Names are SF Symbol names, as stored; the app maps each
// one to its drawable through design/sf-symbols.tsv.

/**
 * The SF Symbols offered for a group or a relationship type.
 *
 * A hand-checked subset rather than the whole symbol library, weighted towards
 * what this app is for. Nobody categorizing their business contacts needs a
 * snowflake, a cat and a tent; they need something that reads as *client*,
 * *past client*, *trainer*, *partner*, *meal prep*.
 */
object SymbolCatalog {

    /**
     * One symbol, plus the words someone would actually type looking for it.
     * Searching by filename is useless: nobody hunting for a client icon types
     * "checkmark.seal".
     */
    data class Symbol(val name: String, val keywords: String = "") {
        val id: String get() = name

        /** Literal and case-sensitive, as in Swift; [visibleCategories] lowercases the query first. */
        fun matches(query: String): Boolean = name.contains(query) || keywords.contains(query)
    }

    data class Category(val title: String, val symbols: List<Symbol>) {
        val id: String get() = title
    }

    /** VoiceOver reads "person.2" as a filename. Spacing the components out makes it a phrase. */
    fun readableName(symbolName: String): String = symbolName.replace(".", " ")

    /**
     * What the picker shows for a search: the whole catalogue for an empty
     * query, otherwise each category cut down to its matches, empty ones dropped.
     */
    fun visibleCategories(query: String): List<Category> {
        val trimmed = query.trimmingWhitespacesAndNewlines().lowercase()
        if (trimmed.isEmpty()) return catalogue
        return catalogue.mapNotNull { category ->
            val matches = category.symbols.filter { it.matches(trimmed) }
            if (matches.isEmpty()) null else Category(category.title, matches)
        }
    }

    /**
     * Ordered by how often this app's users reach for them, not how Apple files
     * them. Anything ambiguous was left out rather than guessed at.
     */
    val catalogue: List<Category> = listOf(
        Category(
            "Clients & Deals",
            listOf(
                Symbol("checkmark.seal", "client current active signed confirmed"),
                Symbol("person.crop.circle.badge.checkmark", "client active confirmed member"),
                Symbol("sparkles", "potential prospect lead new opportunity"),
                Symbol("flame", "lead hot warm urgent priority"),
                Symbol("target", "prospect goal pipeline aim"),
                Symbol("clock.arrow.circlepath", "past former previous lapsed alumni win back"),
                Symbol("person.badge.clock", "past former pending waiting"),
                Symbol("person.badge.plus", "new candidate recruit signup prospect"),
                Symbol("dollarsign.circle", "money paid revenue invoice paying"),
                Symbol("creditcard", "payment billing subscription"),
                Symbol("banknote", "money cash revenue paid"),
                Symbol("cart", "sale purchase order buyer customer"),
                Symbol("bag", "sale customer buyer retail"),
                Symbol("percent", "discount commission rate deal"),
                Symbol("chart.line.uptrend.xyaxis", "growth investor revenue results progress"),
                Symbol("signature", "contract signed agreement deal close"),
                Symbol("megaphone", "marketing promotion outreach campaign"),
                Symbol("star", "vip best favourite favorite top"),
            ),
        ),
        Category(
            "Business & Work",
            listOf(
                Symbol("briefcase", "work business professional job company"),
                Symbol("building.2", "company office business corporate employer"),
                Symbol("building.columns", "bank institution finance legal"),
                Symbol("person.2", "partner colleague coworker connection two"),
                Symbol("person.2.circle", "colleague team coworker peers"),
                Symbol("person.3", "team staff crew group employees"),
                Symbol("network", "connection networking contacts web referral"),
                Symbol("link", "connection referral linked introduced"),
                Symbol("arrow.triangle.branch", "referral introduced source pipeline"),
                Symbol("laptopcomputer", "work remote tech online"),
                Symbol("doc.text", "contract paperwork proposal document"),
                Symbol("folder", "project account file client folder"),
                Symbol("calendar", "schedule booking appointment recurring"),
                Symbol("clock", "schedule time hourly session"),
                Symbol("chart.bar", "results numbers performance report"),
                Symbol("chart.pie", "share split breakdown equity"),
                Symbol("lightbulb", "advisor idea mentor consultant"),
                Symbol("graduationcap", "mentor coach teacher student education"),
                Symbol("crown", "vip executive ceo founder owner boss"),
                Symbol("trophy", "top best winner champion success"),
                Symbol("rosette", "award certified qualified accredited"),
                Symbol("key", "landlord access owner property"),
                Symbol("wrench.and.screwdriver", "contractor trades service repair vendor"),
                Symbol("shippingbox", "vendor supplier delivery product"),
                Symbol("gearshape", "operations service ops technical"),
                Symbol("globe", "international remote online web"),
                Symbol("airplane", "travel remote out of town"),
            ),
        ),
        Category(
            "Training & Fitness",
            listOf(
                Symbol("dumbbell", "training gym lifting weights personal trainer client"),
                Symbol("figure.strengthtraining.traditional", "training gym lifting weights trainer"),
                Symbol("figure.run", "running cardio gym fitness client"),
                Symbol("figure.walk", "walking steps mobility"),
                Symbol("figure.yoga", "yoga stretch mobility class"),
                Symbol("figure.mind.and.body", "wellness recovery mindset coaching"),
                Symbol("figure.pool.swim", "swim pool aquatics"),
                Symbol("figure.basketball", "sport basketball league team"),
                Symbol("sportscourt", "gym court facility club team"),
                Symbol("stopwatch", "session timing interval conditioning"),
                Symbol("bolt.heart", "conditioning cardio energy health"),
                Symbol("waveform.path.ecg", "health progress metrics results"),
                Symbol("scalemass", "weight progress body measurement"),
                Symbol("heart", "health wellness personal care"),
            ),
        ),
        Category(
            "Food & Meal Prep",
            listOf(
                Symbol("fork.knife", "meal prep food nutrition dinner client"),
                Symbol("takeoutbag.and.cup.and.straw", "meal prep delivery takeout order food"),
                Symbol("basket", "grocery shopping meal prep order"),
                Symbol("carrot", "nutrition food vegetables diet meal"),
                Symbol("leaf", "nutrition healthy plant diet"),
                Symbol("cup.and.saucer", "coffee meeting catch up chat"),
                Symbol("wineglass", "drinks dinner social client entertaining"),
                Symbol("birthday.cake", "birthday celebration anniversary"),
            ),
        ),
        Category(
            "People & Personal",
            listOf(
                Symbol("person", "person individual acquaintance someone"),
                Symbol("person.crop.circle", "profile contact individual"),
                Symbol("person.text.rectangle", "contact card details record"),
                Symbol("hand.wave", "friend hello greeting casual"),
                Symbol("face.smiling", "friend friendly casual social"),
                Symbol("hands.sparkles", "thanks gratitude helper supporter"),
                Symbol("house", "family home household personal"),
                Symbol("figure.2.and.child.holdinghands", "family kids parents household"),
                Symbol("figure.and.child.holdinghands", "family parent child"),
                Symbol("gift", "gift birthday thank you present"),
                Symbol("moon.stars", "night out evening social bar"),
                Symbol("music.note", "music band hobby social"),
                Symbol("ticket", "event concert game invite"),
                Symbol("camera", "photography creative hobby"),
                Symbol("book", "reading study book club"),
                Symbol("car", "commute local drive neighbour"),
                Symbol("mappin.and.ellipse", "place location venue local"),
                Symbol("building", "neighbourhood building apartment local"),
            ),
        ),
        Category(
            "Marks & Status",
            listOf(
                Symbol("tag", "tag label category general"),
                Symbol("bookmark", "saved shortlist keep"),
                Symbol("flag", "flagged important follow up"),
                Symbol("pin", "pinned priority important"),
                Symbol("checkmark.circle", "done complete confirmed active"),
                Symbol("exclamationmark.circle", "urgent attention issue"),
                Symbol("questionmark.circle", "unknown unsure to decide uncategorized"),
                Symbol("hourglass", "waiting pending slow stalled"),
                Symbol("archivebox", "archived inactive dormant cold"),
                Symbol("bell", "reminder follow up notify"),
                Symbol("hand.thumbsup", "good positive approved happy"),
                Symbol("shield", "trusted protected private"),
                Symbol("lock", "private confidential sensitive"),
                Symbol("eye", "watching monitor keep an eye"),
                Symbol("seal", "official verified certified"),
                Symbol("circle.hexagongrid", "both mixed general other"),
                Symbol("square.grid.2x2", "group set collection"),
            ),
        ),
    )
}
