package com.chrisallenclark.keepr.core.persistence

import com.chrisallenclark.keepr.core.model.ConversationNote
import com.chrisallenclark.keepr.core.model.FollowUp
import com.chrisallenclark.keepr.core.model.Interaction
import com.chrisallenclark.keepr.core.model.InteractionKind
import com.chrisallenclark.keepr.core.model.KeeprCalendar
import com.chrisallenclark.keepr.core.model.Memory
import com.chrisallenclark.keepr.core.model.MemoryCategory
import com.chrisallenclark.keepr.core.model.ModelContext
import com.chrisallenclark.keepr.core.model.NoteSource
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.PersonGroup
import com.chrisallenclark.keepr.core.model.PersonLink
import com.chrisallenclark.keepr.core.model.Priority
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.model.RelationshipTag
import com.chrisallenclark.keepr.core.model.TagKind
import java.time.Instant

/**
 * The demo relationships, word for word from Keepr/Models/SampleData.swift,
 * so Android screenshots compare line for line with the iOS ones.
 *
 * Every record is flagged [Person.isSampleData] / [PersonGroup.isSampleData]
 * and removable on its own. On Android the screenshot ("demo") mode goes
 * further and loads these into a separate in-memory database, so sample data
 * and the user's own never share a store.
 */
object SampleData {

    fun isLoaded(context: ModelContext): Boolean = context.fetch<Person>().any { it.isSampleData }

    fun remove(context: ModelContext) {
        context.fetch<Person>().filter { it.isSampleData }.forEach { context.delete(it) }
        context.fetch<PersonGroup>().filter { it.isSampleData }.forEach { context.delete(it) }
        context.save()
    }

    fun load(
        context: ModelContext,
        defaults: Preferences,
        now: Instant = Instant.now(),
        calendar: KeeprCalendar = KeeprCalendar.current(),
    ) {
        if (isLoaded(context)) return
        KeeprStore.seedIfNeeded(context, defaults)

        fun daysAgo(days: Int): Instant = calendar.addingDays(-days, now)
        fun daysAhead(days: Int): Instant = calendar.startOfDay(calendar.addingDays(days, now))
        fun tag(name: String, kind: TagKind): RelationshipTag = KeeprStore.tag(name, kind, context)
        fun setCadence(name: String, kind: TagKind, days: Int) {
            tag(name, kind).cadenceDays = days
        }

        setCadence("Current Client", TagKind.BUSINESS, 14)
        setCadence("Potential Client", TagKind.BUSINESS, 30)
        setCadence("Business Partner", TagKind.BUSINESS, 60)
        setCadence("Close Friend", TagKind.PERSONAL, 45)

        // Business — the potential client from the gym
        val jake = Person(
            givenName = "Jake",
            familyName = "Martinez",
            context = RelationshipContext.BUSINESS,
            company = "Martinez Roofing",
            jobTitle = "Owner",
            phoneNumbers = listOf("(561) 555-0142"),
            emailAddresses = listOf("jake@martinezroofing.com"),
            priority = Priority.HIGH,
            howWeMet = "Met at Life Time on the gym floor",
            createdAt = daysAgo(6),
            isSampleData = true,
        )
        context.insert(jake)
        addNote(
            "Consultation — Jake",
            body = listOf(
                "Runs a roofing crew of nine. Busiest April through October, so training has to survive a 5am start.",
                "Lower back flares up after long days on ladders. Cleared by his doctor for deadlifts, wants to be careful.",
                "Goal is 20 lb down before his sister's wedding in June.",
                "Blunt, hates being sold to. Wants the plan, not the pitch.",
                "Mentioned his business partner is looking for a trainer as well.",
            ).joinToString("\n"),
            at = daysAgo(21),
            isPinned = true,
            person = jake,
            context = context,
        )
        jake.tags = listOf(tag("Potential Client", TagKind.BUSINESS), tag("Lead", TagKind.BUSINESS))
        jake.workNote = "Runs a roofing crew of nine. Knows every contractor in Delray."
        val jakeChat = interaction(
            InteractionKind.IN_PERSON,
            at = daysAgo(1),
            title = "Talked about training at Life Time",
            raw = "Ran into Jake at Life Time. He owns a roofing company, wants to lose about " +
                "20 pounds before his daughter's wedding, and said to text him next week " +
                "about getting started.",
            summary = "Wants to start training. Text next week.",
            person = jake,
            context = context,
        )
        addMemories(
            listOf(
                Triple("Owns Martinez Roofing", MemoryCategory.WORK, Priority.HIGH),
                Triple("Wants to lose 20 lb", MemoryCategory.GOALS, Priority.HIGH),
                Triple("Daughter is getting married in the spring", MemoryCategory.FAMILY, Priority.NORMAL),
                Triple("Trains at Life Time in the mornings", MemoryCategory.PERSONAL, Priority.NORMAL),
            ),
            to = jake, from = jakeChat, at = daysAgo(1), context = context,
        )
        addFollowUp(
            "Text about starting training",
            note = "He asked me to reach out after his vacation.",
            due = daysAhead(2),
            priority = Priority.HIGH,
            person = jake,
            from = jakeChat,
            context = context,
        )

        // Business — the active client
        val sarah = Person(
            givenName = "Sarah",
            familyName = "Miller",
            context = RelationshipContext.BUSINESS,
            company = "Coastal Design Co.",
            jobTitle = "Creative Director",
            phoneNumbers = listOf("(561) 555-0198"),
            emailAddresses = listOf("sarah.miller@coastaldesign.com"),
            priority = Priority.HIGH,
            isFavorite = true,
            howWeMet = "Referred by Michael Reed",
            introducedBy = "Michael Reed",
            createdAt = daysAgo(240),
            isSampleData = true,
        )
        context.insert(sarah)
        sarah.tags = listOf(tag("Current Client", TagKind.BUSINESS))
        addNote(
            "Check-In · Week 12",
            body = listOf(
                "Down 2 lb this week, 14 total since we started.",
                "Travel: Italy for two weeks in September. Wants to keep lifting while she's away — hotel gym only, so dumbbells and bands.",
                "Squat depth is finally consistent. Added 15 lb to her working set.",
                "Said her sister is asking about training too.",
            ).joinToString("\n"),
            at = daysAgo(3),
            person = sarah,
            context = context,
        )
        val sarahCall = interaction(
            InteractionKind.CALL,
            at = daysAgo(3),
            title = "Weekly check-in",
            raw = "Good week — down 2 lb. Traveling to Italy in September so we need to " +
                "front-load the next block.",
            summary = "Down 2 lb. Italy trip in September — adjust the plan around it.",
            person = sarah,
            context = context,
        )
        addMemories(
            listOf(
                Triple("Going to Italy in September", MemoryCategory.IMPORTANT_DATE, Priority.HIGH),
                Triple("Trains Tuesday and Thursday mornings", MemoryCategory.PREFERENCES, Priority.NORMAL),
                Triple("Two kids, 7 and 10", MemoryCategory.FAMILY, Priority.NORMAL),
            ),
            to = sarah, from = sarahCall, at = daysAgo(3), context = context,
        )
        interaction(
            InteractionKind.IN_PERSON,
            at = daysAgo(10),
            title = "Session",
            raw = null,
            summary = "Reworked her squat setup. Much better depth.",
            person = sarah,
            context = context,
        )
        addFollowUp(
            "Send the September training block",
            due = daysAgo(1),
            priority = Priority.HIGH,
            person = sarah,
            from = sarahCall,
            context = context,
        )

        // Business — the partner who sends referrals
        val michael = Person(
            givenName = "Michael",
            familyName = "Reed",
            context = RelationshipContext.BOTH,
            company = "Reed Physical Therapy",
            jobTitle = "Partner",
            phoneNumbers = listOf("(561) 555-0110"),
            emailAddresses = listOf("michael@reedpt.com"),
            priority = Priority.NORMAL,
            isFavorite = true,
            howWeMet = "Shared office space in 2021",
            createdAt = daysAgo(900),
            isSampleData = true,
        )
        context.insert(michael)
        michael.tags = listOf(
            tag("Business Partner", TagKind.BUSINESS),
            tag("Referral Source", TagKind.BUSINESS),
            tag("Friend", TagKind.PERSONAL),
        )
        val michaelMeeting = interaction(
            InteractionKind.MEETING,
            at = daysAgo(14),
            title = "Quarterly catch-up",
            raw = null,
            summary = "Agreed to keep swapping referrals. He wants to co-host a workshop in the fall.",
            person = michael,
            context = context,
        )
        addMemories(
            listOf(
                Triple("Sends 2–3 referrals a month", MemoryCategory.BUSINESS, Priority.HIGH),
                Triple("Wants to co-host a fall workshop", MemoryCategory.GOALS, Priority.NORMAL),
                Triple("Runs the Delray half marathon every year", MemoryCategory.INTERESTS, Priority.LOW),
            ),
            to = michael, from = michaelMeeting, at = daysAgo(14), context = context,
        )
        addFollowUp("Pick a date for the workshop", due = daysAhead(5), person = michael, from = michaelMeeting, context = context)

        // Business — the lead that went quiet
        val dana = Person(
            givenName = "Dana",
            familyName = "Whitfield",
            context = RelationshipContext.BUSINESS,
            company = "Whitfield Interiors",
            jobTitle = "Principal",
            phoneNumbers = listOf("(561) 555-0177"),
            priority = Priority.NORMAL,
            howWeMet = "Chamber of commerce mixer",
            createdAt = daysAgo(90),
            isSampleData = true,
        )
        context.insert(dana)
        dana.tags = listOf(tag("Potential Client", TagKind.BUSINESS))
        interaction(
            InteractionKind.EMAIL,
            at = daysAgo(38),
            title = "Sent pricing",
            raw = null,
            summary = "Sent the small-group pricing sheet. She said she'd think it over.",
            person = dana,
            context = context,
        )
        addMemories(
            listOf(Triple("Considering small-group training for her team", MemoryCategory.BUSINESS, Priority.NORMAL)),
            to = dana, from = null, at = daysAgo(38), context = context,
        )

        // Business — the vendor
        val priya = Person(
            givenName = "Priya",
            familyName = "Raman",
            context = RelationshipContext.BUSINESS,
            company = "Bright Studio",
            jobTitle = "Photographer",
            emailAddresses = listOf("hello@brightstudio.co"),
            createdAt = daysAgo(150),
            isSampleData = true,
        )
        context.insert(priya)
        priya.tags = listOf(tag("Vendor", TagKind.BUSINESS))
        interaction(
            InteractionKind.EMAIL,
            at = daysAgo(21),
            title = "Booked the shoot",
            raw = null,
            summary = "Booked her for new headshots. Invoice paid.",
            person = priya,
            context = context,
        )

        // Personal — close friend
        val alex = Person(
            givenName = "Alex",
            familyName = "Nguyen",
            context = RelationshipContext.PERSONAL,
            phoneNumbers = listOf("(561) 555-0155"),
            isFavorite = true,
            createdAt = daysAgo(1500),
            isSampleData = true,
        )
        context.insert(alex)
        alex.tags = listOf(tag("Close Friend", TagKind.PERSONAL))
        val alexText = interaction(
            InteractionKind.TEXT,
            at = daysAgo(2),
            title = null,
            raw = null,
            summary = "Made plans for Saturday. He starts the new job on the 1st.",
            person = alex,
            context = context,
        )
        addMemories(
            listOf(
                Triple("Starting a new job on the 1st", MemoryCategory.WORK, Priority.HIGH),
                Triple("Allergic to shellfish", MemoryCategory.PREFERENCES, Priority.HIGH),
            ),
            to = alex, from = alexText, at = daysAgo(2), context = context,
        )

        // Personal — family
        val mom = Person(
            givenName = "Linda",
            familyName = "Clark",
            preferredName = "Mom",
            context = RelationshipContext.PERSONAL,
            phoneNumbers = listOf("(407) 555-0121"),
            priority = Priority.HIGH,
            isFavorite = true,
            createdAt = daysAgo(2000),
            isSampleData = true,
        )
        context.insert(mom)
        mom.tags = listOf(tag("Family", TagKind.PERSONAL))
        interaction(
            InteractionKind.CALL,
            at = daysAgo(5),
            title = "Sunday call",
            raw = null,
            summary = "Her knee is better. Wants to visit in October.",
            person = mom,
            context = context,
        )
        addMemories(
            listOf(
                Triple("Wants to visit in October", MemoryCategory.IMPORTANT_DATE, Priority.HIGH),
                Triple("Birthday: March 14", MemoryCategory.IMPORTANT_DATE, Priority.HIGH),
            ),
            to = mom, from = null, at = daysAgo(5), context = context,
        )
        addFollowUp("Call about October flights", due = daysAhead(4), person = mom, from = null, context = context)

        // Personal — friend who has gone quiet
        val ben = Person(
            givenName = "Ben",
            familyName = "Ortiz",
            context = RelationshipContext.PERSONAL,
            priority = Priority.HIGH,
            createdAt = daysAgo(800),
            isSampleData = true,
        )
        context.insert(ben)
        ben.tags = listOf(tag("Friend", TagKind.PERSONAL))
        interaction(
            InteractionKind.IN_PERSON,
            at = daysAgo(95),
            title = "Dinner",
            raw = null,
            summary = "Caught up over dinner. He and Maya are house hunting.",
            person = ben,
            context = context,
        )
        addMemories(
            listOf(Triple("House hunting with Maya", MemoryCategory.PERSONAL, Priority.NORMAL)),
            to = ben, from = null, at = daysAgo(95), context = context,
        )

        // Places: one gym holding a client, a colleague and a friend at once,
        // plus the clients trained at home — the pair of chips People is built around.
        val lifeTime = PersonGroup(
            name = "Life Time",
            symbolName = "figure.run",
            detail = "Delray",
            aliases = "LT",
            context = RelationshipContext.BOTH,
            sortOrder = 10,
            isSampleData = true,
        )
        context.insert(lifeTime)
        lifeTime.members = listOf(jake, michael, alex)

        val homeStudio = PersonGroup(
            name = "Home Studio",
            symbolName = "house",
            aliases = "HS, In-Home",
            context = RelationshipContext.BUSINESS,
            sortOrder = 20,
            isSampleData = true,
        )
        context.insert(homeStudio)
        homeStudio.members = listOf(sarah, dana)

        // The referral that started the client relationship, as a link.
        context.insert(
            PersonLink(
                personA = sarah,
                personB = michael,
                labelAToB = "Referred By",
                labelBToA = "Referred",
                note = "Sent her over after her half marathon",
                createdAt = daysAgo(240),
            ),
        )

        context.save()
    }

    private fun interaction(
        kind: InteractionKind,
        at: Instant,
        title: String?,
        raw: String?,
        summary: String?,
        person: Person,
        context: ModelContext,
    ): Interaction {
        val interaction = Interaction(kind, at, title, raw, summary, person = person)
        context.insert(interaction)
        val last = person.lastInteractionAt
        if (last == null || last < at) person.lastInteractionAt = at
        return interaction
    }

    private fun addMemories(
        items: List<Triple<String, MemoryCategory, Priority>>,
        to: Person,
        from: Interaction?,
        at: Instant,
        context: ModelContext,
    ) {
        for ((content, category, importance) in items) {
            context.insert(Memory(content, category, importance, at, person = to, sourceInteraction = from))
        }
    }

    private fun addNote(
        title: String,
        body: String,
        source: NoteSource = NoteSource.RECORDER,
        at: Instant,
        isPinned: Boolean = false,
        person: Person,
        context: ModelContext,
    ) {
        context.insert(ConversationNote(title, body, source, at, at, isPinned, person = person))
    }

    private fun addFollowUp(
        title: String,
        note: String? = null,
        due: Instant,
        priority: Priority = Priority.NORMAL,
        person: Person,
        from: Interaction?,
        context: ModelContext,
    ) {
        context.insert(FollowUp(title, note, due, priority = priority, person = person, sourceInteraction = from))
    }
}
